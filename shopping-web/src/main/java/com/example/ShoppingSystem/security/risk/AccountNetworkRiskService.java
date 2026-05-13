package com.example.ShoppingSystem.security.risk;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.example.ShoppingSystem.Utils.SnowflakeIdWorker;
import com.example.ShoppingSystem.entity.entity.UserLoginIdentity;
import com.example.ShoppingSystem.filter.preauth.PreAuthHeaders;
import com.example.ShoppingSystem.filter.preauth.domain.TrustedExitIpMatcher;
import com.example.ShoppingSystem.filter.preauth.support.PreAuthIpNormalizer;
import com.example.ShoppingSystem.filter.preauth.support.PreAuthRequestResolver;
import com.example.ShoppingSystem.mapper.UserLoginIdentityMapper;
import com.example.ShoppingSystem.mapper.UserRiskAccountTerminationMapper;
import com.example.ShoppingSystem.mapper.UserRiskProfileMapper;
import com.example.ShoppingSystem.quota.IpCountryQueryService;
import com.example.ShoppingSystem.quota.IpGeoSnapshot;
import com.example.ShoppingSystem.quota.IpReputationMultiLevelQueryService;
import com.example.ShoppingSystem.redisdata.UserAuthRiskRedisKeys;
import com.example.ShoppingSystem.security.token.AuthTokenService;
import com.example.ShoppingSystem.service.user.auth.register.risk.IpReputationEvidence;
import com.example.ShoppingSystem.service.user.auth.risk.TerminatedAccountEmailBloomService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class AccountNetworkRiskService {

    private static final Logger log = LoggerFactory.getLogger(AccountNetworkRiskService.class);

    private static final int DEFAULT_ACCOUNT_SCORE = 10000;
    private static final int NETWORK_LOCK_THRESHOLD = 600;
    private static final int WEBRTC_MISMATCH_PENALTY = 100;
    private static final int VPN_PROXY_PENALTY = 80;
    private static final int IP_CHANGED_PENALTY = 40;
    private static final int COUNTRY_CHANGED_PENALTY = 120;
    private static final int IMPOSSIBLE_TRAVEL_LOW_PENALTY = 150;
    private static final int IMPOSSIBLE_TRAVEL_MEDIUM_PENALTY = 220;
    private static final int IMPOSSIBLE_TRAVEL_HIGH_PENALTY = 300;
    private static final double EARTH_RADIUS_KM = 6371.0088D;
    private static final long MIN_SPEED_WINDOW_MILLIS = 60_000L;
    private static final String LOCK_MESSAGE =
            "For security reasons, this account is temporarily unavailable. Please try again later.";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_LOCKED = "LOCKED";
    private static final String STATUS_RISK_TERMINATED = "RISK_TERMINATED";
    private static final String REASON_NETWORK_LOCK = "NETWORK_RISK_LOCK_30M";
    private static final String REASON_TERMINATION_REQUIRED = "ACCOUNT_TERMINATION_REQUIRED";
    private static final String EVENT_NETWORK_LOCK = "NETWORK_RISK_LOCK_30M";
    private static final String EVENT_TERMINATION_REQUIRED = "ACCOUNT_TERMINATION_REQUIRED";

    private static final DefaultRedisScript<Long> INCREMENT_NETWORK_WINDOW_SCRIPT = new DefaultRedisScript<>(
            """
                    local penalty = tonumber(ARGV[1]) or 0
                    local ttl = tonumber(ARGV[2]) or 1800
                    local score = redis.call('INCRBY', KEYS[1], penalty)
                    if redis.call('TTL', KEYS[1]) < 0 then
                        redis.call('EXPIRE', KEYS[1], ttl)
                    end
                    for i = 2, #KEYS do
                        redis.call('INCR', KEYS[i])
                        if redis.call('TTL', KEYS[i]) < 0 then
                            redis.call('EXPIRE', KEYS[i], ttl)
                        end
                    end
                    return score
                    """,
            Long.class
    );

    private final StringRedisTemplate stringRedisTemplate;
    private final UserRiskProfileMapper userRiskProfileMapper;
    private final UserLoginIdentityMapper userLoginIdentityMapper;
    private final UserRiskAccountTerminationMapper userRiskAccountTerminationMapper;
    private final TerminatedAccountEmailBloomService terminatedAccountEmailBloomService;
    private final PreAuthRequestResolver requestResolver;
    private final IpCountryQueryService ipCountryQueryService;
    private final IpReputationMultiLevelQueryService ipReputationMultiLevelQueryService;
    private final AuthTokenService authTokenService;
    private final SnowflakeIdWorker snowflakeIdWorker;
    private final TrustedExitIpMatcher trustedExitIpMatcher;

    public AccountNetworkRiskService(StringRedisTemplate stringRedisTemplate,
                                     UserRiskProfileMapper userRiskProfileMapper,
                                     UserLoginIdentityMapper userLoginIdentityMapper,
                                     UserRiskAccountTerminationMapper userRiskAccountTerminationMapper,
                                     TerminatedAccountEmailBloomService terminatedAccountEmailBloomService,
                                     PreAuthRequestResolver requestResolver,
                                     IpCountryQueryService ipCountryQueryService,
                                     IpReputationMultiLevelQueryService ipReputationMultiLevelQueryService,
                                     AuthTokenService authTokenService,
                                     SnowflakeIdWorker snowflakeIdWorker,
                                     TrustedExitIpMatcher trustedExitIpMatcher) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.userRiskProfileMapper = userRiskProfileMapper;
        this.userLoginIdentityMapper = userLoginIdentityMapper;
        this.userRiskAccountTerminationMapper = userRiskAccountTerminationMapper;
        this.terminatedAccountEmailBloomService = terminatedAccountEmailBloomService;
        this.requestResolver = requestResolver;
        this.ipCountryQueryService = ipCountryQueryService;
        this.ipReputationMultiLevelQueryService = ipReputationMultiLevelQueryService;
        this.authTokenService = authTokenService;
        this.snowflakeIdWorker = snowflakeIdWorker;
        this.trustedExitIpMatcher = trustedExitIpMatcher;
    }

    @Transactional
    public AccountNetworkRiskDecision evaluate(HttpServletRequest request) {
        if (request == null || "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return AccountNetworkRiskDecision.allow();
        }

        Long userId = resolveAuthenticatedUserId(request);
        if (userId == null) {
            return AccountNetworkRiskDecision.allow();
        }

        AccountNetworkRiskDecision existingRedisLock = checkRedisLock(userId);
        if (!existingRedisLock.allowed()) {
            return existingRedisLock;
        }

        OffsetDateTime now = OffsetDateTime.now();
        Map<String, Object> state = userRiskProfileMapper.findUserRiskStateByUserId(userId);
        AccountNetworkRiskDecision existingDbLock = checkDbLock(userId, state, now);
        if (!existingDbLock.allowed()) {
            return existingDbLock;
        }

        String currentIp = normalizeIp(requestResolver.resolveClientIp(request));
        String deviceFingerprint = normalizeText(request.getHeader(PreAuthHeaders.HEADER_DEVICE_FINGERPRINT));
        RiskEvaluation riskEvaluation = evaluateRisk(state, currentIp, request);

        userRiskProfileMapper.touchUserNetworkState(
                userId,
                DEFAULT_ACCOUNT_SCORE,
                resolveRiskLevel(DEFAULT_ACCOUNT_SCORE),
                now,
                currentIp,
                deviceFingerprint,
                now
        );

        if (riskEvaluation.penaltyScore() <= 0 || riskEvaluation.events().isEmpty()) {
            return AccountNetworkRiskDecision.allow();
        }

        NetworkWindowSnapshot windowSnapshot = incrementNetworkWindow(
                userId,
                riskEvaluation.penaltyScore(),
                riskEvaluation.events()
        );
        log.info("Post-login account network risk recorded, userId={}, method={}, uri={}, ip={}, webRtcIp={}, webRtcStatus={}, events={}, penalty={}, networkScore30m={}",
                userId,
                request.getMethod(),
                request.getRequestURI(),
                currentIp,
                riskEvaluation.webRtcIp(),
                riskEvaluation.webRtcStatus(),
                riskEvaluation.eventNames(),
                riskEvaluation.penaltyScore(),
                windowSnapshot.networkScore30m());

        if (windowSnapshot.networkScore30m() <= NETWORK_LOCK_THRESHOLD) {
            return AccountNetworkRiskDecision.allow();
        }
        return triggerLock(userId, currentIp, deviceFingerprint, riskEvaluation, windowSnapshot);
    }

    private RiskEvaluation evaluateRisk(Map<String, Object> state, String currentIp, HttpServletRequest request) {
        String previousIp = normalizeIp(readString(state, "lastLoginIp", "last_login_ip"));
        OffsetDateTime previousSeenAt = readOffsetDateTime(state, "lastLoginAt", "last_login_at");
        List<String> webRtcIps = normalizeIpCandidates(
                request == null ? "" : request.getHeader(PreAuthHeaders.HEADER_WEBRTC_IP),
                request == null ? "" : request.getHeader(PreAuthHeaders.HEADER_WEBRTC_IPS)
        );
        String webRtcIp = webRtcIps.isEmpty() ? "" : webRtcIps.get(0);
        String webRtcStatus = normalizeWebRtcStatus(request == null ? "" : request.getHeader(PreAuthHeaders.HEADER_WEBRTC_STATUS));

        LinkedHashSet<NetworkRiskEvent> events = new LinkedHashSet<>();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("previousIp", previousIp);
        metadata.put("currentIp", currentIp);
        metadata.put("webRtcIp", webRtcIp);
        metadata.put("webRtcIps", String.join(",", webRtcIps));
        metadata.put("webRtcStatus", webRtcStatus);

        boolean webRtcStrictMatch = StrUtil.isNotBlank(currentIp) && webRtcIps.contains(currentIp);
        boolean webRtcTrustedMatch = StrUtil.isNotBlank(currentIp)
                && !webRtcStrictMatch
                && trustedExitIpMatcher.isTrustedMatch(currentIp, webRtcIps);
        boolean webRtcMismatch = "ok".equals(webRtcStatus)
                && StrUtil.isNotBlank(currentIp)
                && !webRtcIps.isEmpty()
                && !webRtcStrictMatch
                && !webRtcTrustedMatch;
        if (webRtcMismatch) {
            events.add(NetworkRiskEvent.WEBRTC_MISMATCH);
        }

        boolean ipChanged = StrUtil.isNotBlank(previousIp)
                && StrUtil.isNotBlank(currentIp)
                && !StrUtil.equals(previousIp, currentIp);
        IpGeoSnapshot previousGeo = null;
        IpGeoSnapshot currentGeo = null;
        if (ipChanged) {
            events.add(NetworkRiskEvent.IP_CHANGED);
            previousGeo = queryGeo(previousIp);
            currentGeo = queryGeo(currentIp);
            String previousCountry = normalizeCountry(previousGeo == null ? "" : previousGeo.country());
            String currentCountry = normalizeCountry(currentGeo == null ? "" : currentGeo.country());
            metadata.put("previousCountry", previousCountry);
            metadata.put("currentCountry", currentCountry);
            if (StrUtil.isNotBlank(previousCountry)
                    && StrUtil.isNotBlank(currentCountry)
                    && !StrUtil.equals(previousCountry, currentCountry)) {
                events.add(NetworkRiskEvent.COUNTRY_CHANGED);
            }

            int travelPenalty = impossibleTravelPenalty(previousGeo, currentGeo, previousSeenAt, OffsetDateTime.now(), metadata);
            if (travelPenalty > 0) {
                events.add(NetworkRiskEvent.IMPOSSIBLE_TRAVEL);
                metadata.put("impossibleTravelPenalty", travelPenalty);
            }
        }

        if ((ipChanged || webRtcMismatch) && isVpnOrProxySuspected(currentIp, metadata)) {
            events.add(NetworkRiskEvent.VPN_PROXY);
        }

        int penaltyScore = 0;
        for (NetworkRiskEvent event : events) {
            if (event == NetworkRiskEvent.IMPOSSIBLE_TRAVEL && metadata.get("impossibleTravelPenalty") instanceof Number number) {
                penaltyScore += Math.max(0, number.intValue());
            } else {
                penaltyScore += event.penaltyScore();
            }
        }
        metadata.put("penaltyScore", penaltyScore);
        metadata.put("events", events.stream().map(NetworkRiskEvent::eventName).toList());
        return new RiskEvaluation(events, penaltyScore, webRtcIp, webRtcStatus, metadata);
    }

    private boolean isVpnOrProxySuspected(String currentIp, Map<String, Object> metadata) {
        if (StrUtil.isBlank(currentIp)) {
            return false;
        }
        try {
            IpReputationMultiLevelQueryService.MultiLevelQueryResult result =
                    ipReputationMultiLevelQueryService.queryEvidence(currentIp);
            if (result == null || !result.success() || result.evidence() == null) {
                return false;
            }
            IpReputationEvidence evidence = result.evidence();
            Integer currentScore = result.currentScore();
            metadata.put("ipRiskSource", result.source());
            metadata.put("ipRiskScore", currentScore);
            metadata.put("ipProxyType", normalizeText(evidence.proxyType()));
            metadata.put("ipUsageType", normalizeText(evidence.usageType()));
            boolean proxyFlag = evidence.proxyIsVpn()
                    || evidence.proxyIsTor()
                    || evidence.proxyIsPublicProxy()
                    || evidence.proxyIsWebProxy()
                    || evidence.proxyIsDataCenter()
                    || evidence.proxyIsResidentialProxy()
                    || evidence.proxyIsConsumerPrivacyNetwork()
                    || evidence.proxyIsEnterprisePrivateNetwork();
            String proxyType = normalizeToken(evidence.proxyType());
            boolean proxyTypeFlag = Set.of("VPN", "TOR", "PUB", "WEB", "DCH", "RES", "CPN", "EPN")
                    .contains(proxyType);
            return proxyFlag || proxyTypeFlag || (currentScore != null && currentScore < 4800);
        } catch (Exception e) {
            log.debug("Post-login account network IP reputation lookup failed, ip={}, reason={}",
                    currentIp,
                    e.getMessage());
            return false;
        }
    }

    private NetworkWindowSnapshot incrementNetworkWindow(Long userId,
                                                         int penaltyScore,
                                                         LinkedHashSet<NetworkRiskEvent> events) {
        List<String> keys = new ArrayList<>();
        keys.add(UserAuthRiskRedisKeys.networkScore30mKey(userId));
        for (NetworkRiskEvent event : events) {
            keys.add(event.redisKey(userId));
        }
        long ttlSeconds = TimeUnit.MINUTES.toSeconds(UserAuthRiskRedisKeys.NETWORK_RISK_WINDOW_MINUTES);
        Long networkScore = stringRedisTemplate.execute(
                INCREMENT_NETWORK_WINDOW_SCRIPT,
                keys,
                String.valueOf(Math.max(0, penaltyScore)),
                String.valueOf(ttlSeconds)
        );
        return readNetworkWindowSnapshot(userId, networkScore == null ? 0L : networkScore);
    }

    private NetworkWindowSnapshot readNetworkWindowSnapshot(Long userId, long networkScore) {
        List<NetworkRiskEvent> events = List.of(
                NetworkRiskEvent.WEBRTC_MISMATCH,
                NetworkRiskEvent.VPN_PROXY,
                NetworkRiskEvent.IP_CHANGED,
                NetworkRiskEvent.COUNTRY_CHANGED,
                NetworkRiskEvent.IMPOSSIBLE_TRAVEL,
                NetworkRiskEvent.WAF_REPEATED
        );
        List<String> keys = events.stream().map(event -> event.redisKey(userId)).toList();
        List<String> values = stringRedisTemplate.opsForValue().multiGet(keys);
        Map<String, Long> counters = new LinkedHashMap<>();
        for (int index = 0; index < events.size(); index += 1) {
            String value = values == null || index >= values.size() ? "" : values.get(index);
            counters.put(events.get(index).metadataKey(), parseLong(value));
        }
        return new NetworkWindowSnapshot(networkScore, counters);
    }

    private AccountNetworkRiskDecision triggerLock(Long userId,
                                                   String currentIp,
                                                   String deviceFingerprint,
                                                   RiskEvaluation riskEvaluation,
                                                   NetworkWindowSnapshot windowSnapshot) {
        Map<String, Object> state = userRiskProfileMapper.findUserRiskStateByUserId(userId);
        int previousLockCount = readInteger(state, "lockCount", 0);
        int nextLockCount = previousLockCount + 1;
        LockDecision decision = resolveLockDecision(nextLockCount, windowSnapshot.networkScore30m());
        if (!decision.terminationRequired()) {
            Boolean marked = stringRedisTemplate.opsForValue().setIfAbsent(
                    UserAuthRiskRedisKeys.authLockKey(userId),
                    "",
                    Math.max(1L, decision.guardSeconds()),
                    TimeUnit.SECONDS
            );
            if (!Boolean.TRUE.equals(marked)) {
                return checkRedisLock(userId);
            }
        }

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime lockUntil = decision.lockSeconds() > 0L ? now.plusSeconds(decision.lockSeconds()) : null;
        int scoreBefore = readScoreBefore(state);
        String riskLevelBefore = readStringOrDefault(state, "riskLevel", resolveRiskLevel(scoreBefore));
        int currentEnvScore = readCurrentEnvScore(state, scoreBefore);
        int behaviorScoreDelta = readInteger(state, "behaviorScoreDelta", 0);
        int scoreAfter;
        int nextBehaviorScoreDelta;
        String eventType;
        String lockReason;
        if (decision.terminationRequired()) {
            scoreAfter = 0;
            nextBehaviorScoreDelta = -currentEnvScore;
            eventType = EVENT_TERMINATION_REQUIRED;
            lockReason = REASON_TERMINATION_REQUIRED;
            userLoginIdentityMapper.updateStatusByUserId(userId, STATUS_RISK_TERMINATED);
            upsertRiskTermination(userId, now, lockReason);
        } else {
            nextBehaviorScoreDelta = behaviorScoreDelta - decision.penaltyScore();
            scoreAfter = clampScore(currentEnvScore + nextBehaviorScoreDelta);
            eventType = EVENT_NETWORK_LOCK;
            lockReason = REASON_NETWORK_LOCK;
            userLoginIdentityMapper.updateStatusByUserIdIfStatus(userId, STATUS_ACTIVE, STATUS_LOCKED);
        }
        String riskLevelAfter = resolveRiskLevel(scoreAfter);

        userRiskProfileMapper.upsertUserAuthLockState(
                userId,
                currentEnvScore,
                nextBehaviorScoreDelta,
                scoreAfter,
                riskLevelAfter,
                nextLockCount,
                now,
                lockUntil,
                lockReason,
                now
        );
        userRiskProfileMapper.insertUserRiskScoreEvent(
                snowflakeIdWorker.nextId(),
                userId,
                eventType,
                scoreBefore,
                scoreAfter - scoreBefore,
                scoreAfter,
                riskLevelBefore,
                riskLevelAfter,
                lockReason,
                normalizeText(currentIp),
                normalizeText(deviceFingerprint),
                buildMetadata(riskEvaluation, windowSnapshot, nextLockCount, decision),
                now
        );
        stringRedisTemplate.delete(networkWindowKeys(userId));
        authTokenService.evictUserContext(userId);

        log.warn("Post-login account network risk locked account, userId={}, lockReason={}, lockCount={}, terminationRequired={}, networkScore30m={}, penaltyScore={}",
                userId,
                lockReason,
                nextLockCount,
                decision.terminationRequired(),
                windowSnapshot.networkScore30m(),
                decision.penaltyScore());

        return AccountNetworkRiskDecision.blocked(
                decision.terminationRequired(),
                !decision.terminationRequired() && decision.lockSeconds() > 0L
                        ? TimeUnit.SECONDS.toMillis(decision.lockSeconds())
                        : null,
                decision.terminationRequired() ? STATUS_RISK_TERMINATED : STATUS_LOCKED,
                lockReason,
                LOCK_MESSAGE
        );
    }

    private AccountNetworkRiskDecision checkRedisLock(Long userId) {
        Boolean exists = stringRedisTemplate.hasKey(UserAuthRiskRedisKeys.authLockKey(userId));
        if (!Boolean.TRUE.equals(exists)) {
            return AccountNetworkRiskDecision.allow();
        }
        Long ttlMs = stringRedisTemplate.getExpire(UserAuthRiskRedisKeys.authLockKey(userId), TimeUnit.MILLISECONDS);
        return AccountNetworkRiskDecision.blocked(
                false,
                ttlMs != null && ttlMs > 0L ? ttlMs : null,
                STATUS_LOCKED,
                REASON_NETWORK_LOCK,
                LOCK_MESSAGE
        );
    }

    private AccountNetworkRiskDecision checkDbLock(Long userId, Map<String, Object> state, OffsetDateTime now) {
        OffsetDateTime lockUntil = readOffsetDateTime(state, "lockUntil", "lock_until");
        if (lockUntil == null || !lockUntil.isAfter(now)) {
            return AccountNetworkRiskDecision.allow();
        }
        long retryAfterMs = Math.max(1L, Duration.between(now, lockUntil).toMillis());
        stringRedisTemplate.opsForValue().set(
                UserAuthRiskRedisKeys.authLockKey(userId),
                "",
                retryAfterMs,
                TimeUnit.MILLISECONDS
        );
        return AccountNetworkRiskDecision.blocked(
                false,
                retryAfterMs,
                STATUS_LOCKED,
                readStringOrDefault(state, "lockReason", REASON_NETWORK_LOCK),
                LOCK_MESSAGE
        );
    }

    private String buildMetadata(RiskEvaluation riskEvaluation,
                                 NetworkWindowSnapshot windowSnapshot,
                                 int lockCount,
                                 LockDecision decision) {
        Map<String, Object> metadata = new LinkedHashMap<>(riskEvaluation.metadata());
        metadata.put("networkScore30m", windowSnapshot.networkScore30m());
        metadata.putAll(windowSnapshot.counters());
        metadata.put("lockCount", lockCount);
        metadata.put("lockDays", decision.lockSeconds() <= 0L ? 0L : TimeUnit.SECONDS.toDays(decision.lockSeconds()));
        metadata.put("penaltyScore", decision.penaltyScore());
        metadata.put("terminationRequired", decision.terminationRequired());
        metadata.put("threshold", NETWORK_LOCK_THRESHOLD);
        return JSONUtil.toJsonStr(metadata);
    }

    private LockDecision resolveLockDecision(int lockCount, long networkScore30m) {
        if (lockCount >= 4) {
            return new LockDecision(0L, 0, true);
        }
        int penaltyScore = (int) Math.min(1500L, Math.max(NETWORK_LOCK_THRESHOLD, networkScore30m));
        return switch (lockCount) {
            case 1 -> new LockDecision(TimeUnit.DAYS.toSeconds(1), penaltyScore, false);
            case 2 -> new LockDecision(TimeUnit.DAYS.toSeconds(3), penaltyScore, false);
            case 3 -> new LockDecision(TimeUnit.DAYS.toSeconds(7), penaltyScore, false);
            default -> new LockDecision(0L, 0, true);
        };
    }

    private void upsertRiskTermination(Long userId, OffsetDateTime now, String reason) {
        UserLoginIdentity identity = userLoginIdentityMapper.findByUserId(userId);
        if (identity == null) {
            return;
        }
        String email = normalizeEmail(identity.getEmail());
        if (StrUtil.isBlank(email)) {
            return;
        }
        String emailHash = sha256(email);
        String phone = Boolean.TRUE.equals(identity.getPhoneVerified()) ? normalizeText(identity.getPhone()) : null;
        userRiskAccountTerminationMapper.upsertRiskTermination(
                snowflakeIdWorker.nextId(),
                userId,
                email,
                emailHash,
                phone,
                phone == null ? null : sha256(phone),
                reason,
                now,
                now
        );
        terminatedAccountEmailBloomService.addTerminatedEmailHashAsync(emailHash);
    }

    private int impossibleTravelPenalty(IpGeoSnapshot previousGeo,
                                        IpGeoSnapshot currentGeo,
                                        OffsetDateTime previousSeenAt,
                                        OffsetDateTime currentSeenAt,
                                        Map<String, Object> metadata) {
        Double speed = speedKmh(previousGeo, currentGeo, previousSeenAt, currentSeenAt);
        if (speed == null || speed < 900D || previousSeenAt == null || currentSeenAt == null) {
            return 0;
        }

        double elapsedHours = elapsedHours(previousSeenAt, currentSeenAt);
        if (elapsedHours > 72D) {
            return 0;
        }
        metadata.put("travelSpeedKmh", Math.round(speed));
        metadata.put("travelElapsedHours", Math.round(elapsedHours * 100D) / 100D);
        if (speed < 1500D) {
            return IMPOSSIBLE_TRAVEL_LOW_PENALTY;
        }
        if (speed < 3000D) {
            return IMPOSSIBLE_TRAVEL_MEDIUM_PENALTY;
        }
        return IMPOSSIBLE_TRAVEL_HIGH_PENALTY;
    }

    private Double speedKmh(IpGeoSnapshot previousGeo,
                            IpGeoSnapshot currentGeo,
                            OffsetDateTime previousSeenAt,
                            OffsetDateTime currentSeenAt) {
        if (previousGeo == null || currentGeo == null
                || !previousGeo.hasCoordinate() || !currentGeo.hasCoordinate()
                || previousSeenAt == null || currentSeenAt == null) {
            return null;
        }
        return distanceKm(
                previousGeo.latitude(),
                previousGeo.longitude(),
                currentGeo.latitude(),
                currentGeo.longitude()
        ) / elapsedHours(previousSeenAt, currentSeenAt);
    }

    private double elapsedHours(OffsetDateTime previousSeenAt, OffsetDateTime currentSeenAt) {
        long elapsedMillis = Duration.between(previousSeenAt, currentSeenAt).toMillis();
        return Math.max(MIN_SPEED_WINDOW_MILLIS, elapsedMillis) / 3_600_000D;
    }

    private double distanceKm(BigDecimal previousLatitude,
                              BigDecimal previousLongitude,
                              BigDecimal currentLatitude,
                              BigDecimal currentLongitude) {
        double lat1 = Math.toRadians(previousLatitude.doubleValue());
        double lat2 = Math.toRadians(currentLatitude.doubleValue());
        double deltaLat = Math.toRadians(currentLatitude.doubleValue() - previousLatitude.doubleValue());
        double deltaLon = Math.toRadians(currentLongitude.doubleValue() - previousLongitude.doubleValue());
        double a = Math.sin(deltaLat / 2D) * Math.sin(deltaLat / 2D)
                + Math.cos(lat1) * Math.cos(lat2)
                * Math.sin(deltaLon / 2D) * Math.sin(deltaLon / 2D);
        double c = 2D * Math.atan2(Math.sqrt(a), Math.sqrt(1D - a));
        return EARTH_RADIUS_KM * c;
    }

    private IpGeoSnapshot queryGeo(String ip) {
        if (StrUtil.isBlank(ip)) {
            return null;
        }
        try {
            IpCountryQueryService.GeoQueryResult result = ipCountryQueryService.queryGeo(ip);
            return result != null && result.success() ? result.geo() : null;
        } catch (Exception e) {
            log.debug("Post-login account network geo lookup failed, ip={}, reason={}", ip, e.getMessage());
            return null;
        }
    }

    private List<String> networkWindowKeys(Long userId) {
        return List.of(
                UserAuthRiskRedisKeys.networkScore30mKey(userId),
                UserAuthRiskRedisKeys.networkWebRtcMismatch30mKey(userId),
                UserAuthRiskRedisKeys.networkVpnProxy30mKey(userId),
                UserAuthRiskRedisKeys.networkIpChanged30mKey(userId),
                UserAuthRiskRedisKeys.networkCountryChanged30mKey(userId),
                UserAuthRiskRedisKeys.networkImpossibleTravel30mKey(userId),
                UserAuthRiskRedisKeys.networkWafRepeated30mKey(userId)
        );
    }

    private Long resolveAuthenticatedUserId(HttpServletRequest request) {
        Object value = request == null ? null : request.getAttribute("authUserId");
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null || StrUtil.isBlank(value.toString())) {
            return null;
        }
        try {
            return Long.parseLong(value.toString().trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private int readScoreBefore(Map<String, Object> state) {
        int score = readInteger(state, "currentScore", DEFAULT_ACCOUNT_SCORE);
        return clampScore(score);
    }

    private int readCurrentEnvScore(Map<String, Object> state, int scoreBefore) {
        int envScore = readInteger(state, "currentEnvScore", 0);
        if (envScore > 0) {
            return clampScore(envScore);
        }
        return scoreBefore > 0 ? scoreBefore : DEFAULT_ACCOUNT_SCORE;
    }

    private int readInteger(Map<String, Object> state, String key, int fallback) {
        if (state == null || !state.containsKey(key)) {
            return fallback;
        }
        Object value = state.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null || StrUtil.isBlank(value.toString())) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String readStringOrDefault(Map<String, Object> state, String key, String fallback) {
        String value = readString(state, key);
        return value == null ? fallback : value;
    }

    private String readString(Map<String, Object> state, String... keys) {
        Object value = readValue(state, keys);
        return normalizeText(value == null ? null : String.valueOf(value));
    }

    private OffsetDateTime readOffsetDateTime(Map<String, Object> state, String... keys) {
        Object value = readValue(state, keys);
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime;
        }
        if (value instanceof Instant instant) {
            return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
        }
        if (value instanceof Timestamp timestamp) {
            return OffsetDateTime.ofInstant(timestamp.toInstant(), ZoneOffset.UTC);
        }
        if (value instanceof Date date) {
            return OffsetDateTime.ofInstant(date.toInstant(), ZoneOffset.UTC);
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.atOffset(ZoneOffset.UTC);
        }
        String text = normalizeText(value == null ? null : value.toString());
        if (text == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(text);
        } catch (Exception ignored) {
        }
        try {
            return OffsetDateTime.ofInstant(Instant.parse(text), ZoneOffset.UTC);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Object readValue(Map<String, Object> state, String... keys) {
        if (state == null || state.isEmpty() || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (key != null && state.containsKey(key)) {
                return state.get(key);
            }
        }
        for (String key : keys) {
            String snake = camelToSnake(key);
            if (state.containsKey(snake)) {
                return state.get(snake);
            }
        }
        return null;
    }

    private String camelToSnake(String value) {
        if (StrUtil.isBlank(value)) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < value.length(); index += 1) {
            char current = value.charAt(index);
            if (Character.isUpperCase(current)) {
                builder.append('_').append(Character.toLowerCase(current));
            } else {
                builder.append(current);
            }
        }
        return builder.toString();
    }

    private long parseLong(String value) {
        if (StrUtil.isBlank(value)) {
            return 0L;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private int clampScore(int score) {
        return Math.max(0, Math.min(10000, score));
    }

    private String resolveRiskLevel(int score) {
        int safeScore = clampScore(score);
        if (safeScore >= 8500) {
            return "L1";
        }
        if (safeScore >= 7500) {
            return "L2";
        }
        if (safeScore >= 6000) {
            return "L3";
        }
        if (safeScore >= 4800) {
            return "L4";
        }
        if (safeScore >= 3000) {
            return "L5";
        }
        return "L6";
    }

    private String normalizeIp(String rawIp) {
        return PreAuthIpNormalizer.normalizeIp(rawIp);
    }

    private List<String> normalizeIpCandidates(String rawPrimaryIp, String rawCandidateIps) {
        Set<String> normalized = new LinkedHashSet<>();
        addNormalizedIp(normalized, rawPrimaryIp);
        if (StrUtil.isNotBlank(rawCandidateIps)) {
            String[] parts = rawCandidateIps.split("[,\\s]+");
            for (String part : parts) {
                addNormalizedIp(normalized, part);
            }
        }
        return new ArrayList<>(normalized);
    }

    private void addNormalizedIp(Set<String> target, String rawIp) {
        String normalized = normalizeIp(rawIp);
        if (StrUtil.isNotBlank(normalized)) {
            target.add(normalized);
        }
    }

    private String normalizeWebRtcStatus(String rawStatus) {
        if (StrUtil.isBlank(rawStatus)) {
            return "";
        }
        String normalized = rawStatus.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "ok", "timeout", "unsupported", "private_only", "error" -> normalized;
            default -> "error";
        };
    }

    private String normalizeCountry(String country) {
        String normalized = normalizeText(country);
        if (normalized == null) {
            return "";
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        return normalized.length() == 2 ? normalized : "";
    }

    private String normalizeToken(String value) {
        return StrUtil.blankToDefault(value, "").trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeEmail(String email) {
        String normalized = normalizeText(email);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(StrUtil.nullToEmpty(value).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest is unavailable.", e);
        }
    }

    public record AccountNetworkRiskDecision(boolean allowed,
                                             boolean terminationRequired,
                                             Long retryAfterMs,
                                             String status,
                                             String reason,
                                             String message) {

        public static AccountNetworkRiskDecision allow() {
            return new AccountNetworkRiskDecision(true, false, null, "", "", "");
        }

        public static AccountNetworkRiskDecision blocked(boolean terminationRequired,
                                                         Long retryAfterMs,
                                                         String status,
                                                         String reason,
                                                         String message) {
            return new AccountNetworkRiskDecision(false, terminationRequired, retryAfterMs, status, reason, message);
        }
    }

    private record RiskEvaluation(LinkedHashSet<NetworkRiskEvent> events,
                                  int penaltyScore,
                                  String webRtcIp,
                                  String webRtcStatus,
                                  Map<String, Object> metadata) {

        List<String> eventNames() {
            return events.stream().map(NetworkRiskEvent::eventName).toList();
        }
    }

    private record NetworkWindowSnapshot(long networkScore30m, Map<String, Long> counters) {
    }

    private record LockDecision(long lockSeconds, int penaltyScore, boolean terminationRequired) {

        private long guardSeconds() {
            return lockSeconds > 0L ? lockSeconds : TimeUnit.DAYS.toSeconds(1);
        }
    }

    private enum NetworkRiskEvent {
        WEBRTC_MISMATCH("WEBRTC_IP_MISMATCH", "webrtcMismatch", WEBRTC_MISMATCH_PENALTY),
        VPN_PROXY("VPN_PROXY_SUSPECTED", "vpnProxy", VPN_PROXY_PENALTY),
        IP_CHANGED("ACCOUNT_IP_CHANGED", "ipChanged", IP_CHANGED_PENALTY),
        COUNTRY_CHANGED("COUNTRY_CHANGED", "countryChanged", COUNTRY_CHANGED_PENALTY),
        IMPOSSIBLE_TRAVEL("IMPOSSIBLE_TRAVEL", "impossibleTravel", 0),
        WAF_REPEATED("WAF_REPEATED_AFTER_LOGIN", "wafRepeated", 0);

        private final String eventName;
        private final String metadataKey;
        private final int penaltyScore;

        NetworkRiskEvent(String eventName, String metadataKey, int penaltyScore) {
            this.eventName = eventName;
            this.metadataKey = metadataKey;
            this.penaltyScore = penaltyScore;
        }

        private String eventName() {
            return eventName;
        }

        private String metadataKey() {
            return metadataKey;
        }

        private int penaltyScore() {
            return penaltyScore;
        }

        private String redisKey(Long userId) {
            return switch (this) {
                case WEBRTC_MISMATCH -> UserAuthRiskRedisKeys.networkWebRtcMismatch30mKey(userId);
                case VPN_PROXY -> UserAuthRiskRedisKeys.networkVpnProxy30mKey(userId);
                case IP_CHANGED -> UserAuthRiskRedisKeys.networkIpChanged30mKey(userId);
                case COUNTRY_CHANGED -> UserAuthRiskRedisKeys.networkCountryChanged30mKey(userId);
                case IMPOSSIBLE_TRAVEL -> UserAuthRiskRedisKeys.networkImpossibleTravel30mKey(userId);
                case WAF_REPEATED -> UserAuthRiskRedisKeys.networkWafRepeated30mKey(userId);
            };
        }
    }
}
