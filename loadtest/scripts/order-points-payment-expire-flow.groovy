import groovy.json.JsonOutput
import groovy.json.JsonSlurper

import java.net.HttpURLConnection
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.security.cert.X509Certificate
import java.time.OffsetDateTime
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

def stringProp = { String name, String fallback ->
    String value = props.getProperty(name)
    return value == null || value.trim().isEmpty() ? fallback : value.trim()
}
def intProp = { String name, int fallback ->
    try {
        return Integer.parseInt(stringProp(name, String.valueOf(fallback)))
    } catch (Throwable ignored) {
        return fallback
    }
}
def longProp = { String name, long fallback ->
    try {
        return Long.parseLong(stringProp(name, String.valueOf(fallback)))
    } catch (Throwable ignored) {
        return fallback
    }
}
def boolProp = { String name, boolean fallback ->
    String value = props.getProperty(name)
    return value == null || value.trim().isEmpty() ? fallback : Boolean.parseBoolean(value.trim())
}

String runId = stringProp('RUN_ID', 'local')
String scenarioMode = stringProp('SCENARIOS', 'MAIN').toUpperCase(Locale.ROOT)
String protocol = stringProp('PROTOCOL', 'https')
String host = stringProp('HOST', '127.0.0.1')
int port = intProp('PORT', 6655)
String legacySkuId = stringProp('SKU_ID', '33SRKE5DbzvBWPCjGosBu')
String skuAId = stringProp('SKU_A_ID', legacySkuId)
String skuBId = stringProp('SKU_B_ID', legacySkuId)
String tokenCsv = stringProp('TOKEN_CSV', 'C:/Users/damn/Desktop/shopping/loadtest-output/xss-users-token.csv')
String resultCsv = stringProp('ORDER_POINTS_RESULT_CSV', 'loadtest-output/order-points-payment-results.csv')
boolean appendResults = boolProp('APPEND_RESULTS', true)
long httpTimeoutMs = longProp('HTTP_TIMEOUT_MS', 120_000L)
long longHttpTimeoutMs = longProp('LONG_HTTP_TIMEOUT_MS', 430_000L)
long clientTimeoutMs = longProp('CLIENT_TIMEOUT_MS', 2_000L)
int concurrentThreads = intProp('CONCURRENT_THREADS', 50)
int timedEventThreads = intProp('TIMED_EVENT_THREADS', 120)
long concurrentGroupPauseMs = longProp('CONCURRENT_GROUP_PAUSE_MS', 10_000L)
boolean failOnHttp5xx = boolProp('FAIL_ON_HTTP_5XX', true)

String baseUrl = "${protocol}://${host}:${port}"
boolean insecureTls = protocol.equalsIgnoreCase('https')
SSLContext loadtestSslContext = null
HostnameVerifier loadtestHostnameVerifier = null
if (insecureTls) {
    TrustManager[] trustAll = [new X509TrustManager() {
        void checkClientTrusted(X509Certificate[] chain, String authType) {}
        void checkServerTrusted(X509Certificate[] chain, String authType) {}
        X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0] }
    }] as TrustManager[]
    loadtestSslContext = SSLContext.getInstance('TLS')
    loadtestSslContext.init(null, trustAll, new java.security.SecureRandom())
    loadtestHostnameVerifier = new HostnameVerifier() {
        boolean verify(String hostname, javax.net.ssl.SSLSession session) { return true }
    }
}

JsonSlurper jsonSlurper = new JsonSlurper()
AtomicInteger rowCounter = new AtomicInteger(0)
AtomicInteger idempotencySequence = new AtomicInteger(0)
Object csvLock = new Object()

List<String> resultHeaders = [
        'run_id',
        'phase',
        'scenario',
        'attempt',
        'user_id',
        'sku_id',
        'quantity',
        'order_no',
        'idempotency_key',
        'expire_at',
        'expire_at_epoch_ms',
        'target_offset_ms',
        'pay_started_at_ms',
        'request_fault',
        'delay_ms',
        'http_code',
        'business_code',
        'order_status',
        'payment_type',
        'used_points',
        'available_points',
        'elapsed_ms',
        'success',
        'error'
]

Path resultPath = Paths.get(resultCsv).toAbsolutePath()
Files.createDirectories(resultPath.getParent())
if (!appendResults && Files.exists(resultPath)) {
    Files.delete(resultPath)
}
if (!Files.exists(resultPath)) {
    Files.writeString(
            resultPath,
            resultHeaders.join(',') + System.lineSeparator(),
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND
    )
}

def csvValue = { Object raw ->
    String value = raw == null ? '' : String.valueOf(raw)
    if (value.contains('"') || value.contains(',') || value.contains('\n') || value.contains('\r')) {
        return '"' + value.replace('"', '""') + '"'
    }
    return value
}

def writeRow = { Map<String, ?> row ->
    String line = resultHeaders.collect { csvValue(row.get(it)) }.join(',') + System.lineSeparator()
    synchronized (csvLock) {
        Files.writeString(
                resultPath,
                line,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        )
        rowCounter.incrementAndGet()
    }
}

def parseCsvLine = { String line ->
    List<String> values = []
    StringBuilder current = new StringBuilder()
    boolean quoted = false
    for (int i = 0; i < line.length(); i++) {
        char ch = line.charAt(i)
        if (ch == '"' as char) {
            if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"' as char) {
                current.append('"')
                i++
            } else {
                quoted = !quoted
            }
        } else if (ch == ',' as char && !quoted) {
            values.add(current.toString())
            current.setLength(0)
        } else {
            current.append(ch)
        }
    }
    values.add(current.toString())
    return values
}

Path tokenPath = Paths.get(tokenCsv)
if (!Files.exists(tokenPath)) {
    throw new IllegalStateException("Token CSV does not exist: ${tokenCsv}")
}
List<String> tokenLines = Files.readAllLines(tokenPath, StandardCharsets.UTF_8)
        .findAll { it != null && !it.trim().isEmpty() }
if (tokenLines.size() < 201) {
    throw new IllegalStateException("Token CSV must contain header plus userId=1..200: ${tokenCsv}")
}
List<String> tokenHeader = parseCsvLine(tokenLines.get(0))
int userIdIndex = tokenHeader.indexOf('userId')
int accessTokenIndex = tokenHeader.indexOf('accessToken')
if (userIdIndex < 0 || accessTokenIndex < 0) {
    throw new IllegalStateException("Token CSV header must contain userId,accessToken: ${tokenCsv}")
}
Map<Long, String> tokenByUserId = new LinkedHashMap<>()
for (String line : tokenLines.drop(1)) {
    List<String> columns = parseCsvLine(line)
    if (columns.size() <= Math.max(userIdIndex, accessTokenIndex)) {
        continue
    }
    try {
        Long userId = Long.parseLong(columns.get(userIdIndex).trim())
        String accessToken = columns.get(accessTokenIndex).trim()
        if (!accessToken.isEmpty()) {
            tokenByUserId.putIfAbsent(userId, accessToken)
        }
    } catch (Throwable ignored) {
    }
}
for (long requiredUserId = 1L; requiredUserId <= 200L; requiredUserId++) {
    if (!tokenByUserId.containsKey(requiredUserId)) {
        throw new IllegalStateException("Token CSV missing required userId=${requiredUserId}: ${tokenCsv}")
    }
}

def user = { long userId ->
    return [userId: userId, accessToken: tokenByUserId.get(userId)]
}

def userLabel = { long userId ->
    return String.format(Locale.ROOT, 'U%03d', userId)
}

def parseEpochMs = { String value ->
    if (value == null || value.trim().isEmpty()) {
        return 0L
    }
    try {
        return OffsetDateTime.parse(value.trim()).toInstant().toEpochMilli()
    } catch (Throwable ignored) {
        return 0L
    }
}

def httpJson = { String method, String path, String accessToken, Object body, long timeoutMillis ->
    long startedAt = System.currentTimeMillis()
    HttpURLConnection connection = null
    try {
        URL url = new URL(baseUrl + path)
        connection = (HttpURLConnection) url.openConnection()
        if (connection instanceof HttpsURLConnection && loadtestSslContext != null) {
            ((HttpsURLConnection) connection).setSSLSocketFactory(loadtestSslContext.getSocketFactory())
            ((HttpsURLConnection) connection).setHostnameVerifier(loadtestHostnameVerifier)
        }
        connection.setRequestMethod(method)
        connection.setConnectTimeout((int) Math.min(Integer.MAX_VALUE, Math.max(1L, timeoutMillis)))
        connection.setReadTimeout((int) Math.min(Integer.MAX_VALUE, Math.max(1L, timeoutMillis)))
        connection.setRequestProperty('Accept', 'application/json')
        connection.setRequestProperty('Content-Type', 'application/json')
        if (accessToken != null && !accessToken.isBlank()) {
            connection.setRequestProperty('Authorization', 'Bearer ' + accessToken)
        }
        if (body != null) {
            byte[] bytes = JsonOutput.toJson(body).getBytes(StandardCharsets.UTF_8)
            connection.setDoOutput(true)
            connection.getOutputStream().withCloseable { it.write(bytes) }
        }
        int httpCode = connection.getResponseCode()
        InputStream stream = httpCode >= 400 ? connection.getErrorStream() : connection.getInputStream()
        String responseBody = stream == null ? '' : stream.getText(StandardCharsets.UTF_8.name())
        Object parsedJson = null
        try {
            parsedJson = responseBody == null || responseBody.isBlank() ? null : jsonSlurper.parseText(responseBody)
        } catch (Throwable ignored) {
        }
        return [
                httpCode : httpCode,
                body     : responseBody,
                json     : parsedJson,
                startedAt: startedAt,
                elapsedMs: System.currentTimeMillis() - startedAt,
                error    : ''
        ]
    } catch (SocketTimeoutException e) {
        return [
                httpCode : 0,
                body     : '',
                json     : null,
                startedAt: startedAt,
                elapsedMs: System.currentTimeMillis() - startedAt,
                error    : 'CLIENT_TIMEOUT'
        ]
    } catch (Throwable e) {
        return [
                httpCode : 0,
                body     : '',
                json     : null,
                startedAt: startedAt,
                elapsedMs: System.currentTimeMillis() - startedAt,
                error    : e.getClass().getSimpleName() + ':' + e.getMessage()
        ]
    } finally {
        if (connection != null) {
            connection.disconnect()
        }
    }
}

def createOrder = { String phase, String scenario, Map currentUser, String skuIdValue, int quantity ->
    String idempotencyKey = "JMETER-POINTS-${runId}-${scenario}-${currentUser.userId}-${idempotencySequence.incrementAndGet()}"
    Map response = httpJson(
            'POST',
            '/shopping/user/api/orders',
            currentUser.accessToken,
            [skuId: skuIdValue, quantity: quantity, idempotencyKey: idempotencyKey],
            httpTimeoutMs
    )
    String businessCode = response.json?.code?.toString() ?: response.error
    Object data = response.json?.data
    String orderNo = data?.orderNo?.toString() ?: ''
    String expireAt = data?.expireAt?.toString() ?: ''
    long expireAtEpochMs = parseEpochMs(expireAt)
    if (businessCode != 'ORDER_CREATE_OK' || orderNo.isBlank()) {
        writeRow([
                run_id            : runId,
                phase             : phase,
                scenario          : scenario + '_CREATE_FAILED',
                attempt           : 1,
                user_id           : currentUser.userId,
                sku_id            : skuIdValue,
                quantity          : quantity,
                order_no          : orderNo,
                idempotency_key   : idempotencyKey,
                expire_at         : expireAt,
                expire_at_epoch_ms: expireAtEpochMs,
                http_code         : response.httpCode,
                business_code     : businessCode,
                elapsed_ms        : response.elapsedMs,
                success           : false,
                error             : response.error
        ])
        return null
    }
    return [
            user            : currentUser,
            skuId           : skuIdValue,
            quantity        : quantity,
            orderNo         : orderNo,
            idempotencyKey  : idempotencyKey,
            expireAt        : expireAt,
            expireAtEpochMs : expireAtEpochMs
    ]
}

def payAndRecord = { Map event, int attempt = 1 ->
    Map order = event.order
    Map response = httpJson(
            'POST',
            "/shopping/user/api/orders/${order.orderNo}/pay",
            event.user.accessToken,
            event.body,
            (long) event.timeoutMs
    )
    Object data = response.json?.data
    String businessCode = response.json?.code?.toString() ?: response.error
    writeRow([
            run_id            : runId,
            phase             : event.phase,
            scenario          : event.scenario,
            attempt           : attempt,
            user_id           : event.user.userId,
            sku_id            : order.skuId,
            quantity          : order.quantity,
            order_no          : order.orderNo,
            idempotency_key   : order.idempotencyKey,
            expire_at         : order.expireAt,
            expire_at_epoch_ms: order.expireAtEpochMs,
            target_offset_ms  : event.targetOffsetMs,
            pay_started_at_ms : response.startedAt,
            request_fault     : event.body?.loadtestFault ?: '',
            delay_ms          : event.body?.loadtestDelayMillis ?: 0,
            http_code         : response.httpCode,
            business_code     : businessCode,
            order_status      : data?.status ?: '',
            payment_type      : data?.paymentType ?: '',
            used_points       : data?.usedPoints ?: '',
            available_points  : data?.availablePoints ?: '',
            elapsed_ms        : response.elapsedMs,
            success           : response.httpCode >= 200 && response.httpCode < 300,
            error             : response.error
    ])
    return response
}

def waitUntil = { long targetMs ->
    long delay = targetMs - System.currentTimeMillis()
    while (delay > 0L) {
        Thread.sleep(Math.min(delay, 1_000L))
        delay = targetMs - System.currentTimeMillis()
    }
}

def boundaryScenario = { long offsetMs, int index ->
    String prefix
    if (offsetMs < 0L) {
        prefix = 'BOUNDARY_M' + Math.abs(offsetMs)
    } else if (offsetMs == 0L) {
        prefix = 'BOUNDARY_0'
    } else {
        prefix = 'BOUNDARY_P' + offsetMs
    }
    return prefix + '_' + String.format(Locale.ROOT, '%02d', index)
}

def runUnsupportedScenario = { long startUserId = 143L, long endUserId = 146L, String phaseName = 'UNSUPPORTED' ->
    for (long userId = startUserId; userId <= endUserId; userId++) {
        Map currentUser = user(userId)
        String scenario = "UNSUPPORTED_${userLabel(userId)}"
        Map order = createOrder(phaseName, scenario, currentUser, skuAId, 1)
        if (order == null) {
            continue
        }
        payAndRecord([
                phase         : phaseName,
                scenario      : scenario,
                user          : currentUser,
                order         : order,
                body          : [paymentType: 'POINTS'],
                timeoutMs     : httpTimeoutMs,
                targetOffsetMs: 0L
        ], 1)
    }
}

def prepareBoundaryEvents = {
    List<Map> timedEvents = []
    long userId = 41L
    List<Map> boundaryDefinitions = [
            [offsetMs: -1_000L, count: 7],
            [offsetMs: -200L, count: 7],
            [offsetMs: 0L, count: 6],
            [offsetMs: 200L, count: 6],
            [offsetMs: 1_000L, count: 6],
            [offsetMs: 10_000L, count: 6],
            [offsetMs: 300_000L, count: 6],
            [offsetMs: 360_000L, count: 6]
    ]
    for (Map definition : boundaryDefinitions) {
        long offsetMs = definition.offsetMs as long
        int count = definition.count as int
        for (int index = 1; index <= count; index++) {
            Map currentUser = user(userId++)
            String scenario = boundaryScenario(offsetMs, index)
            Map order = createOrder('MAIN', scenario, currentUser, skuBId, 1)
            if (order == null) {
                continue
            }
            timedEvents.add([
                    phase         : 'MAIN',
                    scenario      : scenario,
                    user          : currentUser,
                    order         : order,
                    body          : [paymentType: 'POINTS'],
                    timeoutMs     : httpTimeoutMs,
                    targetOffsetMs: offsetMs,
                    targetMs      : order.expireAtEpochMs + offsetMs
            ])
        }
    }
    if (userId != 91L) {
        throw new IllegalStateException("Boundary user allocation ended at ${userId}, expected 91.")
    }
    return timedEvents
}

def blockingDefinitions = {
    return [
            [startUser: 91L, endUser: 105L, delayMs: 10_000L, fault: 'SLEEP_BEFORE_DEDUCT', prefix: 'BLOCK_10000_SLEEP', timeoutMs: longHttpTimeoutMs],
            [startUser: 106L, endUser: 120L, delayMs: 70_000L, fault: 'SLEEP_BEFORE_DEDUCT', prefix: 'BLOCK_70000_SLEEP', timeoutMs: longHttpTimeoutMs],
            [startUser: 121L, endUser: 130L, delayMs: 360_000L, fault: 'THROW_AFTER_SLEEP', prefix: 'BLOCK_360000_THROW_AFTER_SLEEP', timeoutMs: longHttpTimeoutMs],
            [startUser: 131L, endUser: 140L, delayMs: 360_000L, fault: 'THROW_AFTER_DEDUCT', prefix: 'BLOCK_360000_THROW_AFTER_DEDUCT', timeoutMs: longHttpTimeoutMs]
    ]
}

def prepareBlockingEventsForDefinition = { Map definition ->
    List<Map> timedEvents = []
    for (long blockingUserId = definition.startUser as long; blockingUserId <= (definition.endUser as long); blockingUserId++) {
        Map currentUser = user(blockingUserId)
        String scenario = "${definition.prefix}_${userLabel(blockingUserId)}"
        Map order = createOrder('MAIN', scenario, currentUser, skuBId, 1)
        if (order == null) {
            continue
        }
        timedEvents.add([
                phase         : 'MAIN',
                scenario      : scenario,
                user          : currentUser,
                order         : order,
                body          : [
                        paymentType            : 'POINTS',
                        loadtestDelayMillis    : definition.delayMs,
                        loadtestFault          : definition.fault
                ],
                timeoutMs     : definition.timeoutMs,
                targetOffsetMs: -1_000L,
                targetMs      : order.expireAtEpochMs - 1_000L
        ])
    }
    return timedEvents
}

def prepareBlockingEvents = {
    List<Map> timedEvents = []
    for (Map definition : blockingDefinitions()) {
        timedEvents.addAll(prepareBlockingEventsForDefinition(definition))
    }
    return timedEvents
}

def prepareTimedEvents = {
    List<Map> timedEvents = []
    timedEvents.addAll(prepareBoundaryEvents())
    timedEvents.addAll(prepareBlockingEvents())
    return timedEvents
}
def startTimedEvents = { List<Map> timedEvents ->
    def executor = Executors.newFixedThreadPool(Math.max(1, Math.min(timedEventThreads, timedEvents.size())))
    List futures = []
    for (Map event : timedEvents) {
        final Map currentEvent = event
        futures.add(executor.submit({
            waitUntil(currentEvent.targetMs as long)
            payAndRecord(currentEvent, 1)
        } as Callable))
    }
    return [executor: executor, futures: futures]
}

def waitForTimedEvents = { Map timedRun ->
    if (timedRun == null) {
        return
    }
    try {
        timedRun.futures.each { it.get() }
    } finally {
        timedRun.executor.shutdown()
        timedRun.executor.awaitTermination(30L, TimeUnit.SECONDS)
    }
}

def runTimedEvents = { List<Map> timedEvents ->
    waitForTimedEvents(startTimedEvents(timedEvents))
}

def runBoundaryScenarios = {
    runTimedEvents(prepareBoundaryEvents())
}

def runBlockingScenarios = {
    for (Map definition : blockingDefinitions()) {
        runTimedEvents(prepareBlockingEventsForDefinition(definition))
    }
}
def runNormalQuantities = {
    for (long userId = 1L; userId <= 20L; userId++) {
        int quantity = (int) (((userId - 1L) % 5L) + 1L)
        Map currentUser = user(userId)
        String scenario = "NORMAL_${userLabel(userId)}_Q${quantity}"
        Map order = createOrder('MAIN', scenario, currentUser, skuAId, quantity)
        if (order == null) {
            continue
        }
        payAndRecord([
                phase         : 'MAIN',
                scenario      : scenario,
                user          : currentUser,
                order         : order,
                body          : [paymentType: 'POINTS'],
                timeoutMs     : httpTimeoutMs,
                targetOffsetMs: 0L
        ], 1)
    }
}

def runConcurrentSameOrder = {
    for (long userId = 21L; userId <= 40L; userId++) {
        Map currentUser = user(userId)
        String scenario = "CONCURRENT_${userLabel(userId)}"
        Map order = createOrder('MAIN', scenario, currentUser, skuAId, 1)
        if (order == null) {
            throw new IllegalStateException("Failed to create concurrent order for ${scenario}")
        }
        def executor = Executors.newFixedThreadPool(concurrentThreads)
        CountDownLatch ready = new CountDownLatch(concurrentThreads)
        CountDownLatch start = new CountDownLatch(1)
        List futures = []
        for (int attemptIndex = 1; attemptIndex <= concurrentThreads; attemptIndex++) {
            final int attempt = attemptIndex
            futures.add(executor.submit({
                ready.countDown()
                start.await(10L, TimeUnit.SECONDS)
                payAndRecord([
                        phase         : 'MAIN',
                        scenario      : scenario,
                        user          : currentUser,
                        order         : order,
                        body          : [paymentType: 'POINTS'],
                        timeoutMs     : httpTimeoutMs,
                        targetOffsetMs: 0L
                ], attempt)
            } as Callable))
        }
        ready.await(10L, TimeUnit.SECONDS)
        start.countDown()
        List responses = []
        try {
            futures.each { responses.add(it.get()) }
        } finally {
            executor.shutdown()
            executor.awaitTermination(30L, TimeUnit.SECONDS)
        }
        if (failOnHttp5xx) {
            List failedResponses = responses.findAll {
                int httpCode = (it.httpCode ?: 0) as int
                return httpCode == 0 || httpCode >= 500
            }
            if (!failedResponses.isEmpty()) {
                throw new IllegalStateException(
                        "${scenario} had ${failedResponses.size()} infrastructure failures during ${concurrentThreads}-thread payment burst"
                )
            }
        }
        if (concurrentGroupPauseMs > 0L && userId < 40L) {
            Thread.sleep(concurrentGroupPauseMs)
        }
    }
}

def runInsufficientPoints = { Collection<Long> userIds = [141L, 142L], String phaseName = 'MAIN', int quantity = 5 ->
    for (long userId : userIds) {
        Map currentUser = user(userId)
        String scenario = "INSUFFICIENT_${userLabel(userId)}_Q${quantity}"
        Map order = createOrder(phaseName, scenario, currentUser, skuAId, quantity)
        if (order == null) {
            continue
        }
        payAndRecord([
                phase         : phaseName,
                scenario      : scenario,
                user          : currentUser,
                order         : order,
                body          : [paymentType: 'POINTS'],
                timeoutMs     : httpTimeoutMs,
                targetOffsetMs: 0L
        ], 1)
    }
}

def runClientTimeoutRetry = {
    for (long userId = 161L; userId <= 170L; userId++) {
        Map currentUser = user(userId)
        String scenarioBase = "CLIENT_TIMEOUT_SLEEP_${userLabel(userId)}"
        Map order = createOrder('MAIN', scenarioBase, currentUser, skuBId, 1)
        if (order == null) {
            continue
        }
        payAndRecord([
                phase         : 'MAIN',
                scenario      : scenarioBase + '_FIRST',
                user          : currentUser,
                order         : order,
                body          : [
                        paymentType        : 'POINTS',
                        loadtestDelayMillis: 10_000L,
                        loadtestFault      : 'SLEEP_BEFORE_DEDUCT'
                ],
                timeoutMs     : clientTimeoutMs,
                targetOffsetMs: 0L
        ], 1)
        payAndRecord([
                phase         : 'MAIN',
                scenario      : scenarioBase + '_RETRY',
                user          : currentUser,
                order         : order,
                body          : [paymentType: 'POINTS'],
                timeoutMs     : longHttpTimeoutMs,
                targetOffsetMs: 0L
        ], 2)
    }

    for (long userId = 171L; userId <= 180L; userId++) {
        Map currentUser = user(userId)
        String scenarioBase = "CLIENT_TIMEOUT_THROW_${userLabel(userId)}"
        Map order = createOrder('MAIN', scenarioBase, currentUser, skuBId, 1)
        if (order == null) {
            continue
        }
        payAndRecord([
                phase         : 'MAIN',
                scenario      : scenarioBase + '_FIRST',
                user          : currentUser,
                order         : order,
                body          : [
                        paymentType        : 'POINTS',
                        loadtestDelayMillis: 10_000L,
                        loadtestFault      : 'THROW_AFTER_SLEEP'
                ],
                timeoutMs     : clientTimeoutMs,
                targetOffsetMs: 0L
        ], 1)
        Thread.sleep(12_000L)
        payAndRecord([
                phase         : 'MAIN',
                scenario      : scenarioBase + '_RETRY',
                user          : currentUser,
                order         : order,
                body          : [paymentType: 'POINTS'],
                timeoutMs     : longHttpTimeoutMs,
                targetOffsetMs: 0L
        ], 2)
    }
}

long scriptStartedAt = System.currentTimeMillis()
try {
    if (scenarioMode == 'UNSUPPORTED' || scenarioMode == 'ALL') {
        runUnsupportedScenario()
    }
    if (scenarioMode == 'NEGATIVE_UNSUPPORTED') {
        runUnsupportedScenario(143L, 162L, 'NEGATIVE_UNSUPPORTED')
    }
    if (scenarioMode == 'NEGATIVE_INSUFFICIENT') {
        runInsufficientPoints((181L..200L).collect { it as Long }, 'NEGATIVE_INSUFFICIENT', 6)
    }
    if (scenarioMode == 'NORMAL') {
        runNormalQuantities()
    }
    if (scenarioMode == 'CONCURRENT') {
        runConcurrentSameOrder()
    }
    if (scenarioMode == 'BOUNDARY') {
        runBoundaryScenarios()
    }
    if (scenarioMode == 'BLOCK') {
        runBlockingScenarios()
    }
    if (scenarioMode == 'INSUFFICIENT') {
        runInsufficientPoints()
    }
    if (scenarioMode == 'CLIENT_TIMEOUT') {
        runClientTimeoutRetry()
    }
    if (scenarioMode == 'MAIN' || scenarioMode == 'ALL') {
        runNormalQuantities()
        runConcurrentSameOrder()
        runBoundaryScenarios()
        runBlockingScenarios()
        runInsufficientPoints()
        runClientTimeoutRetry()
    }
    String summary = JsonOutput.prettyPrint(JsonOutput.toJson([
            runId      : runId,
            scenarios  : scenarioMode,
            skuAId     : skuAId,
            skuBId     : skuBId,
            resultCsv  : resultPath.toString(),
            rows       : rowCounter.get(),
            elapsedMs  : System.currentTimeMillis() - scriptStartedAt
    ]))
    if (binding.hasVariable('SampleResult')) {
        SampleResult.setResponseData(summary, StandardCharsets.UTF_8.name())
        SampleResult.setDataType(org.apache.jmeter.samplers.SampleResult.TEXT)
        SampleResult.setSuccessful(true)
    }
    return summary
} catch (Throwable e) {
    if (binding.hasVariable('SampleResult')) {
        SampleResult.setResponseData((e.getClass().getName() + ': ' + e.getMessage()), StandardCharsets.UTF_8.name())
        SampleResult.setDataType(org.apache.jmeter.samplers.SampleResult.TEXT)
        SampleResult.setSuccessful(false)
        SampleResult.setResponseCode('500')
        SampleResult.setResponseMessage(e.getMessage())
    }
    throw e
}


