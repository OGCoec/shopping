package com.example.ShoppingSystem.admin.service;

import com.example.ShoppingSystem.admin.dto.AdminDeviceRiskDetailResponse;
import com.example.ShoppingSystem.admin.dto.AdminDeviceRiskListItemResponse;
import com.example.ShoppingSystem.admin.dto.AdminDeviceRiskListResponse;
import com.example.ShoppingSystem.admin.dto.AdminDeviceScoreEventResponse;
import com.example.ShoppingSystem.admin.dto.AdminIpRiskBatchUpdateRequest;
import com.example.ShoppingSystem.admin.dto.AdminIpRiskBatchUpdateResponse;
import com.example.ShoppingSystem.admin.dto.AdminIpRiskCountryResponse;
import com.example.ShoppingSystem.admin.dto.AdminIpRiskListItemResponse;
import com.example.ShoppingSystem.admin.dto.AdminIpRiskListResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.example.ShoppingSystem.mapper.AdminDeviceRiskProfileMapper;
import com.example.ShoppingSystem.mapper.IpReputationProfileMapper;
import com.example.ShoppingSystem.quota.IpCountryLocalCacheStore;
import com.example.ShoppingSystem.quota.IpGeoSnapshot;
import com.example.ShoppingSystem.quota.IpRiskLocalCacheStore;
import com.example.ShoppingSystem.service.user.auth.register.risk.impl.IpL6CountingBloomDecisionService;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Service
public class AdminRiskCreditScoreService {

    private static final Logger log = LoggerFactory.getLogger(AdminRiskCreditScoreService.class);

    private static final String FAMILY_IPV4 = "ipv4";
    private static final String FAMILY_IPV6 = "ipv6";
    private static final String DEFAULT_SORT = "risk_first";
    private static final String SORT_RECENT_FIRST = "recent_first";
    private static final Set<String> SUPPORTED_DEVICE_SORTS = Set.of(DEFAULT_SORT, SORT_RECENT_FIRST);
    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 200;
    private static final int DEVICE_DETAIL_EVENT_LIMIT = 50;
    private static final int MAX_BATCH_IPS = 50;
    private static final String ACTION_REMOVE_RISK = "remove_risk";
    private static final String ACTION_ADD_RISK = "add_risk";
    private static final Set<String> SUPPORTED_FAMILIES = Set.of(FAMILY_IPV4, FAMILY_IPV6);
    private static final Set<String> SUPPORTED_LEVELS = Set.of("L1", "L2", "L3", "L4", "L5", "L6");
    private static final Map<String, String> DIAL_CODE_BY_COUNTRY = Map.ofEntries(
            Map.entry("AU", "+61"),
            Map.entry("BR", "+55"),
            Map.entry("CA", "+1"),
            Map.entry("CN", "+86"),
            Map.entry("DE", "+49"),
            Map.entry("ES", "+34"),
            Map.entry("FR", "+33"),
            Map.entry("GB", "+44"),
            Map.entry("HK", "+852"),
            Map.entry("ID", "+62"),
            Map.entry("IN", "+91"),
            Map.entry("IT", "+39"),
            Map.entry("JP", "+81"),
            Map.entry("KR", "+82"),
            Map.entry("MO", "+853"),
            Map.entry("MX", "+52"),
            Map.entry("MY", "+60"),
            Map.entry("NL", "+31"),
            Map.entry("NZ", "+64"),
            Map.entry("PH", "+63"),
            Map.entry("RU", "+7"),
            Map.entry("SG", "+65"),
            Map.entry("TH", "+66"),
            Map.entry("TR", "+90"),
            Map.entry("TW", "+886"),
            Map.entry("US", "+1"),
            Map.entry("VN", "+84")
    );

    private final AdminDeviceRiskProfileMapper adminDeviceRiskProfileMapper;
    private final IpReputationProfileMapper ipReputationProfileMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final IpRiskLocalCacheStore ipRiskLocalCacheStore;
    private final IpCountryLocalCacheStore ipCountryLocalCacheStore;
    private final IpL6CountingBloomDecisionService ipL6CountingBloomDecisionService;

    @Value("${admin.risk-credit.ip-list-cache.redis-key-prefix:admin:ip-risk:list:v1:}")
    private String listCacheKeyPrefix;

    @Value("${admin.risk-credit.ip-list-cache.redis-ttl-seconds:60}")
    private int listCacheTtlSeconds;

    @Value("${admin.risk-credit.device-list-cache.redis-key-prefix:admin:device-risk:list:v1:}")
    private String deviceListCacheKeyPrefix;

    @Value("${admin.risk-credit.device-list-cache.redis-ttl-seconds:45}")
    private int deviceListCacheTtlSeconds;

    @Value("${admin.risk-credit.device-detail-cache.redis-key-prefix:admin:device-risk:detail:v1:}")
    private String deviceDetailCacheKeyPrefix;

    @Value("${admin.risk-credit.device-detail-cache.redis-ttl-seconds:30}")
    private int deviceDetailCacheTtlSeconds;

    @Value("${register.ip-risk-multi-level.redis-key-prefix:register:ip:risk:v2:}")
    private String riskRedisKeyPrefix;

    @Value("${register.ip-risk-multi-level.redis-ttl-minutes:60}")
    private int riskRedisTtlMinutes;

    @Value("${register.ip-risk-multi-level.redis-ttl-jitter-minutes:120}")
    private int riskRedisTtlJitterMinutes;

    @Value("${register.ip-country-cache.redis-key-prefix:register:ip:country:}")
    private String countryRedisKeyPrefix;

    @Value("${register.ip-country-cache.redis-ttl-minutes:360}")
    private int countryRedisTtlMinutes;

    @Value("${register.ip-country-cache.redis-ttl-jitter-minutes:1080}")
    private int countryRedisTtlJitterMinutes;

    public AdminRiskCreditScoreService(AdminDeviceRiskProfileMapper adminDeviceRiskProfileMapper,
                                       IpReputationProfileMapper ipReputationProfileMapper,
                                       StringRedisTemplate stringRedisTemplate,
                                       ObjectMapper objectMapper,
                                       IpRiskLocalCacheStore ipRiskLocalCacheStore,
                                       IpCountryLocalCacheStore ipCountryLocalCacheStore,
                                       IpL6CountingBloomDecisionService ipL6CountingBloomDecisionService) {
        this.adminDeviceRiskProfileMapper = adminDeviceRiskProfileMapper;
        this.ipReputationProfileMapper = ipReputationProfileMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.ipRiskLocalCacheStore = ipRiskLocalCacheStore;
        this.ipCountryLocalCacheStore = ipCountryLocalCacheStore;
        this.ipL6CountingBloomDecisionService = ipL6CountingBloomDecisionService;
    }

    public AdminIpRiskListResponse listIpRiskProfiles(String family,
                                                      String country,
                                                      String level,
                                                      int page,
                                                      int pageSize,
                                                      String sort,
                                                      String q) {
        AdminIpRiskQuery query = normalizeQuery(family, country, level, page, pageSize, sort, q);
        String listCacheKey = listCacheKey(query);
        AdminIpRiskListResponse cachedResponse = readListCache(listCacheKey);
        if (cachedResponse != null) {
            return withSource(cachedResponse, "redis");
        }

        ScoreRange scoreRange = scoreRange(query.level());
        PageInfo<Map<String, Object>> pageInfo = pageIpRiskProfiles(query, scoreRange);
        List<Map<String, Object>> rows = pageInfo.getList();

        List<AdminIpRiskListItemResponse> items = rows.stream()
                .map(this::toItem)
                .toList();
        long total = pageInfo.getTotal();
        boolean hasNext = pageInfo.isHasNextPage();
        AdminIpRiskListResponse response = new AdminIpRiskListResponse(
                query.family(),
                countryResponse(query.country()),
                query.level(),
                query.page(),
                query.pageSize(),
                total,
                hasNext,
                query.sort(),
                "db",
                items
        );
        writeListCache(listCacheKey, response);
        warmReturnedPageCaches(items);
        return response;
    }

    public AdminIpRiskBatchUpdateResponse batchUpdateIpRiskScores(String family,
                                                                   AdminIpRiskBatchUpdateRequest request) {
        // ── 参数校验 ──
        String normalizedFamily = normalizeText(family).toLowerCase(Locale.ROOT);
        if (!SUPPORTED_FAMILIES.contains(normalizedFamily)) {
            throw new IllegalArgumentException("IP family must be ipv4 or ipv6.");
        }
        String action = normalizeText(request.action()).toLowerCase(Locale.ROOT);
        if (!ACTION_REMOVE_RISK.equals(action) && !ACTION_ADD_RISK.equals(action)) {
            throw new IllegalArgumentException("action 必须为 remove_risk 或 add_risk。");
        }
        List<String> ips = request.ips();
        if (ips == null || ips.isEmpty()) {
            throw new IllegalArgumentException("IP 列表不能为空。");
        }
        if (ips.size() > MAX_BATCH_IPS) {
            throw new IllegalArgumentException("单次最多处理 " + MAX_BATCH_IPS + " 个 IP。");
        }
        int targetScore = request.targetScore();
        if (ACTION_REMOVE_RISK.equals(action)) {
            if (targetScore < 3000 || targetScore > 10000) {
                throw new IllegalArgumentException("移出风险时分数必须在 3000-10000 之间。");
            }
        } else {
            if (targetScore < 0 || targetScore > 2999) {
                throw new IllegalArgumentException("添加风险时分数必须在 0-2999 之间。");
            }
        }
        List<String> normalizedIps = ips.stream()
                .map(ip -> normalizeText(ip).toLowerCase(Locale.ROOT))
                .filter(ip -> !ip.isEmpty())
                .distinct()
                .toList();
        if (normalizedIps.isEmpty()) {
            throw new IllegalArgumentException("IP 列表不能为空。");
        }

        // ── 1) 一次 DB 批量更新 ──
        int dbUpdated;
        if (FAMILY_IPV4.equals(normalizedFamily)) {
            dbUpdated = ipReputationProfileMapper.batchUpdateIpv4Scores(normalizedIps, targetScore);
        } else {
            dbUpdated = ipReputationProfileMapper.batchUpdateIpv6Scores(normalizedIps, targetScore);
        }

        // ── 2) 一次 Redis 批量删除（风险缓存 + 国家缓存 + 管理列表缓存） ──
        int cacheDeleted = 0;
        try {
            List<String> cacheKeys = new ArrayList<>();
            normalizedIps.forEach(ip -> {
                cacheKeys.add(riskRedisKeyPrefix + ip);
                cacheKeys.add(countryRedisKeyPrefix + ip);
            });
            // 管理列表缓存按前缀模式清除
            Set<String> adminListKeys = stringRedisTemplate.keys(listCacheKeyPrefix + "*");
            if (adminListKeys != null && !adminListKeys.isEmpty()) {
                cacheKeys.addAll(adminListKeys);
            }
            if (!cacheKeys.isEmpty()) {
                Long deleted = stringRedisTemplate.delete(cacheKeys);
                cacheDeleted = deleted != null ? deleted.intValue() : 0;
            }
        } catch (Exception e) {
            log.warn("Admin IP risk cache batch delete failed, reason={}", e.getMessage());
        }

        // ── 3) 一次布隆过滤器同步（一次 Lua 往返） ──
        int bloomSynced = 0;
        try {
            long synced = ipL6CountingBloomDecisionService.batchSyncMembershipByScore(
                    normalizedFamily, normalizedIps, targetScore);
            bloomSynced = (int) synced;
        } catch (Exception e) {
            log.warn("Admin IP risk bloom batch sync failed, reason={}", e.getMessage());
        }

        // ── 4) Caffeine 本地缓存失效（无网络延迟） ──
        normalizedIps.forEach(ip -> {
            ipRiskLocalCacheStore.invalidate(ip);
            ipCountryLocalCacheStore.invalidate(ip);
        });

        String actionLabel = ACTION_REMOVE_RISK.equals(action) ? "风险移出" : "风险添加";
        String message = String.format("%s完成：DB 更新 %d 行，缓存清除 %d 个 key，布隆同步 %d 个元素。",
                actionLabel, dbUpdated, cacheDeleted, bloomSynced);
        return new AdminIpRiskBatchUpdateResponse(
                action, targetScore, dbUpdated, cacheDeleted, bloomSynced, message);
    }

    private PageInfo<Map<String, Object>> pageIpRiskProfiles(AdminIpRiskQuery query, ScoreRange scoreRange) {
        try {
            return PageHelper.startPage(query.page(), query.pageSize(), true)
                    .doSelectPageInfo(() -> {
                        if (FAMILY_IPV6.equals(query.family())) {
                            ipReputationProfileMapper.listIpv6AdminRiskProfiles(
                                    query.country(),
                                    scoreRange.minScore(),
                                    scoreRange.maxScoreExclusive(),
                                    query.ipQueryPattern()
                            );
                            return;
                        }
                        ipReputationProfileMapper.listIpv4AdminRiskProfiles(
                                query.country(),
                                scoreRange.minScore(),
                                scoreRange.maxScoreExclusive(),
                                query.ipQueryPattern()
                        );
                    });
        } finally {
            PageHelper.clearPage();
        }
    }

    public AdminDeviceRiskListResponse listDeviceRiskProfiles(String level, int page, int pageSize, String sort, String q) {
        AdminDeviceRiskQuery query = normalizeDeviceQuery(level, page, pageSize, sort, q);
        String cacheKey = deviceListCacheKey(query);
        AdminDeviceRiskListResponse cached = readDeviceListCache(cacheKey);
        if (cached != null) {
            return withDeviceSource(cached, "redis");
        }

        ScoreRange scoreRange = scoreRange(query.level());
        PageInfo<Map<String, Object>> pageInfo;
        try {
            pageInfo = PageHelper.startPage(query.page(), query.pageSize(), true)
                    .doSelectPageInfo(() -> adminDeviceRiskProfileMapper.listDeviceRiskProfiles(
                            query.level(),
                            scoreRange.minScore(),
                            scoreRange.maxScoreExclusive(),
                            query.queryPattern(),
                            query.sort()
                    ));
        } finally {
            PageHelper.clearPage();
        }

        List<AdminDeviceRiskListItemResponse> items = pageInfo.getList().stream()
                .map(this::toDeviceItem)
                .toList();
        long total = pageInfo.getTotal();
        boolean hasNext = pageInfo.isHasNextPage();
        AdminDeviceRiskListResponse response = new AdminDeviceRiskListResponse(
                query.level(),
                query.page(),
                query.pageSize(),
                total,
                hasNext,
                query.sort(),
                "db",
                items
        );
        writeDeviceListCache(cacheKey, response);
        return response;
    }

    public AdminDeviceRiskDetailResponse getDeviceDetail(String deviceId) {
        String normalizedId = normalizeDeviceId(deviceId);
        String detailCacheKey = deviceDetailCacheKeyPrefix + normalizedId;
        AdminDeviceRiskDetailResponse cached = readDeviceDetailCache(detailCacheKey);
        if (cached != null) {
            return cached;
        }

        Map<String, Object> row = adminDeviceRiskProfileMapper.findDeviceById(normalizedId);
        if (row == null || row.isEmpty()) {
            throw new AdminServiceException("ADMIN_RISK_DEVICE_NOT_FOUND", "设备不存在。", HttpStatus.NOT_FOUND);
        }

        List<Map<String, Object>> eventRows = adminDeviceRiskProfileMapper.listScoreEventsByDeviceId(
                normalizedId, DEVICE_DETAIL_EVENT_LIMIT);
        List<AdminDeviceScoreEventResponse> scoreEvents = eventRows.stream()
                .map(this::toScoreEvent)
                .toList();

        String deviceFingerprint = toStringValue(value(row, "deviceFingerprint", "device_fingerprint"));
        List<String> usedIpList = parseUsedIpList(value(row, "usedIpList", "used_ip_list"));

        AdminDeviceRiskDetailResponse response = new AdminDeviceRiskDetailResponse(
                toStringValue(value(row, "deviceId", "device_id")),
                sha256Hex(deviceFingerprint),
                maskDeviceFingerprint(deviceFingerprint),
                toInt(value(row, "currentScore", "current_score"), 0),
                toStringValue(value(row, "riskLevel", "risk_level")),
                toIsoString(value(row, "firstSeenAt", "first_seen_at")),
                toIsoString(value(row, "lastSeenAt", "last_seen_at")),
                toStringValue(value(row, "lastLoginIp", "last_login_ip")),
                toIsoString(value(row, "lastIpSeenAt", "last_ip_seen_at")),
                Math.max(0, toInt(value(row, "linkedUserCount", "linked_user_count"), 0)),
                Math.max(0, toInt(value(row, "recentDistinctIpCount", "recent_distinct_ip_count"), 0)),
                Math.max(0, toInt(value(row, "recentIpSwitchCount", "recent_ip_switch_count"), 0)),
                toStringValue(value(row, "lastPenaltyReason", "last_penalty_reason")),
                Math.max(0, toInt(value(row, "lastPenaltyScore", "last_penalty_score"), 0)),
                toIsoString(value(row, "lastPenaltyAt", "last_penalty_at")),
                usedIpList,
                scoreEvents
        );
        writeDeviceDetailCache(detailCacheKey, response);
        return response;
    }

    private AdminDeviceRiskQuery normalizeDeviceQuery(String level, int page, int pageSize, String sort, String q) {
        String normalizedLevel = normalizeText(level).toUpperCase(Locale.ROOT);
        if (!normalizedLevel.isEmpty() && !SUPPORTED_LEVELS.contains(normalizedLevel)) {
            throw new AdminServiceException("ADMIN_RISK_DEVICE_LEVEL_INVALID", "Device risk level must be L1-L6.", HttpStatus.BAD_REQUEST);
        }

        String normalizedSort = normalizeText(sort).toLowerCase(Locale.ROOT);
        if (!SUPPORTED_DEVICE_SORTS.contains(normalizedSort)) {
            normalizedSort = DEFAULT_SORT;
        }

        String normalizedQ = normalizeDeviceQueryText(q);
        String queryPattern = normalizedQ == null ? null : normalizedQ + "%";

        int safePage = Math.max(1, page);
        int safePageSize = pageSize <= 0 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        return new AdminDeviceRiskQuery(
                normalizedLevel.isEmpty() ? null : normalizedLevel,
                safePage,
                safePageSize,
                normalizedSort,
                normalizedQ,
                queryPattern
        );
    }

    private String normalizeDeviceQueryText(String q) {
        String normalized = normalizeText(q);
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > 128) {
            throw new AdminServiceException("ADMIN_RISK_DEVICE_QUERY_TOO_LONG", "设备查询内容过长。", HttpStatus.BAD_REQUEST);
        }
        if (!normalized.matches("^[0-9a-fA-F.:]+$")) {
            throw new AdminServiceException("ADMIN_RISK_DEVICE_QUERY_INVALID", "设备查询只能包含十六进制字符、冒号和点。", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String normalizeDeviceId(String deviceId) {
        String normalized = normalizeText(deviceId).toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.length() != 32 || !normalized.matches("^[0-9a-f]{32}$")) {
            throw new AdminServiceException("ADMIN_RISK_DEVICE_ID_INVALID", "设备 ID 格式无效，应为 32 位十六进制字符串。", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private AdminIpRiskQuery normalizeQuery(String family,
                                            String country,
                                            String level,
                                            int page,
                                            int pageSize,
                                            String sort,
                                            String q) {
        String normalizedFamily = normalizeText(family).toLowerCase(Locale.ROOT);
        if (!SUPPORTED_FAMILIES.contains(normalizedFamily)) {
            throw new AdminServiceException("ADMIN_RISK_IP_FAMILY_INVALID", "IP 类型只能是 IPv4 或 IPv6。", HttpStatus.BAD_REQUEST);
        }

        String normalizedCountry = normalizeCountry(country);
        String normalizedLevel = normalizeText(level).toUpperCase(Locale.ROOT);
        if (!normalizedLevel.isEmpty() && !SUPPORTED_LEVELS.contains(normalizedLevel)) {
            throw new AdminServiceException("ADMIN_RISK_IP_LEVEL_INVALID", "分数区间只能是 L1-L6。", HttpStatus.BAD_REQUEST);
        }

        String normalizedSort = normalizeText(sort);
        if (normalizedSort.isEmpty() || !DEFAULT_SORT.equalsIgnoreCase(normalizedSort)) {
            normalizedSort = DEFAULT_SORT;
        } else {
            normalizedSort = DEFAULT_SORT;
        }

        int safePage = Math.max(1, page);
        int safePageSize = pageSize <= 0 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        String normalizedQuery = normalizeIpQuery(normalizedFamily, q);
        String ipQueryPattern = normalizedQuery == null ? null : normalizedQuery + "%";
        return new AdminIpRiskQuery(
                normalizedFamily,
                normalizedCountry,
                normalizedLevel.isEmpty() ? null : normalizedLevel,
                safePage,
                safePageSize,
                normalizedSort,
                normalizedQuery,
                ipQueryPattern
        );
    }

    private String normalizeCountry(String country) {
        String normalized = normalizeText(country).toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return null;
        }
        if (!normalized.matches("^[A-Z]{2}$")) {
            throw new AdminServiceException("ADMIN_RISK_IP_COUNTRY_INVALID", "国家必须使用 ISO2 代码。", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String normalizeIpQuery(String family, String q) {
        String normalized = normalizeText(q);
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > 128) {
            throw new AdminServiceException("ADMIN_RISK_IP_QUERY_TOO_LONG", "IP 查询内容过长。", HttpStatus.BAD_REQUEST);
        }
        if (FAMILY_IPV4.equals(family) && !normalized.matches("^[0-9.]+$")) {
            throw new AdminServiceException("ADMIN_RISK_IP_QUERY_INVALID", "IPv4 查询只能包含数字和点。", HttpStatus.BAD_REQUEST);
        }
        if (FAMILY_IPV6.equals(family) && !normalized.matches("^[0-9a-fA-F:.]+$")) {
            throw new AdminServiceException("ADMIN_RISK_IP_QUERY_INVALID", "IPv6 查询只能包含十六进制字符、冒号和点。", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private ScoreRange scoreRange(String level) {
        if (level == null || level.isBlank()) {
            return new ScoreRange(null, null);
        }
        return switch (level) {
            case "L1" -> new ScoreRange(8500, null);
            case "L2" -> new ScoreRange(7500, 8500);
            case "L3" -> new ScoreRange(6000, 7500);
            case "L4" -> new ScoreRange(4800, 6000);
            case "L5" -> new ScoreRange(3000, 4800);
            case "L6" -> new ScoreRange(null, 3000);
            default -> new ScoreRange(null, null);
        };
    }

    private AdminIpRiskListItemResponse toItem(Map<String, Object> row) {
        String countryCode = normalizeCountryCode(toStringValue(value(row, "country")));
        int score = toInt(value(row, "currentScore", "current_score"), 0);
        return new AdminIpRiskListItemResponse(
                toStringValue(value(row, "ip")),
                score,
                resolveLevel(score),
                countryCode,
                countryName(countryCode),
                dialCode(countryCode),
                flagCode(countryCode),
                toStringValue(value(row, "region")),
                toStringValue(value(row, "city")),
                toStringValue(value(row, "asn")),
                toStringValue(value(row, "providerName", "provider_name")),
                toStringValue(value(row, "ipType", "ip_type")),
                toBoolean(value(row, "datacenter", "is_datacenter")),
                toBoolean(value(row, "vpn", "is_vpn")),
                toBoolean(value(row, "proxy", "is_proxy")),
                toBoolean(value(row, "tor", "is_tor")),
                toStringValue(value(row, "sourceProvider", "source_provider")),
                toIsoString(value(row, "lastSeenAt", "last_seen_at")),
                toIsoString(value(row, "queriedAt", "queried_at")),
                toIsoString(value(row, "expiresAt", "expires_at"))
        );
    }

    private AdminDeviceRiskListItemResponse toDeviceItem(Map<String, Object> row) {
        String deviceFingerprint = toStringValue(value(row, "deviceFingerprint", "device_fingerprint"));
        return new AdminDeviceRiskListItemResponse(
                toStringValue(value(row, "deviceId", "device_id")),
                sha256Hex(deviceFingerprint),
                maskDeviceFingerprint(deviceFingerprint),
                toInt(value(row, "currentScore", "current_score"), 0),
                toStringValue(value(row, "riskLevel", "risk_level")),
                toIsoString(value(row, "firstSeenAt", "first_seen_at")),
                toIsoString(value(row, "lastSeenAt", "last_seen_at")),
                toStringValue(value(row, "lastLoginIp", "last_login_ip")),
                Math.max(0, toInt(value(row, "linkedUserCount", "linked_user_count"), 0)),
                Math.max(0, toInt(value(row, "recentDistinctIpCount", "recent_distinct_ip_count"), 0)),
                Math.max(0, toInt(value(row, "recentIpSwitchCount", "recent_ip_switch_count"), 0)),
                toStringValue(value(row, "lastPenaltyReason", "last_penalty_reason")),
                Math.max(0, toInt(value(row, "lastPenaltyScore", "last_penalty_score"), 0)),
                toIsoString(value(row, "lastPenaltyAt", "last_penalty_at"))
        );
    }

    private AdminDeviceScoreEventResponse toScoreEvent(Map<String, Object> row) {
        return new AdminDeviceScoreEventResponse(
                toInt(value(row, "scoreBefore", "score_before"), 0),
                toInt(value(row, "penaltyScore", "penalty_score"), 0),
                toInt(value(row, "scoreAfter", "score_after"), 0),
                toStringValue(value(row, "reason")),
                toIsoString(value(row, "createdAt", "created_at"))
        );
    }

    @SuppressWarnings("unchecked")
    private List<String> parseUsedIpList(Object value) {
        if (value == null) {
            return List.of();
        }
        try {
            String json;
            if (value instanceof String str) {
                json = str;
            } else if (value instanceof org.postgresql.util.PGobject pgObj) {
                json = pgObj.getValue();
            } else {
                json = value.toString();
            }
            if (json == null || json.isBlank() || "[]".equals(json.trim())) {
                return List.of();
            }
            List<String> list = objectMapper.readValue(json, new TypeReference<List<String>>() {});
            return list != null ? list : List.of();
        } catch (Exception e) {
            log.debug("Admin device used_ip_list parse failed, reason={}", e.getMessage());
            return List.of();
        }
    }

    private AdminIpRiskCountryResponse countryResponse(String countryCode) {
        String normalized = normalizeCountryCode(countryCode);
        if (normalized == null) {
            return null;
        }
        return new AdminIpRiskCountryResponse(
                normalized,
                countryName(normalized),
                dialCode(normalized),
                flagCode(normalized)
        );
    }

    private void warmReturnedPageCaches(List<AdminIpRiskListItemResponse> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        try {
            Map<String, String> riskValues = new LinkedHashMap<>();
            Map<String, IpRiskLocalCacheStore.LocalRiskSnapshot> localRisks = new LinkedHashMap<>();
            Map<String, String> countryValues = new LinkedHashMap<>();
            Map<String, IpGeoSnapshot> localGeos = new LinkedHashMap<>();
            long updatedAt = System.currentTimeMillis();

            items.forEach(item -> {
                if (item.ip() == null || item.ip().isBlank()) {
                    return;
                }
                String normalizedCountry = normalizeCountryCode(item.countryCode());
                localRisks.put(item.ip(), new IpRiskLocalCacheStore.LocalRiskSnapshot(item.score(), normalizedCountry));
                riskValues.put(riskRedisKeyPrefix + item.ip(), writeJson(new RedisRiskCacheValue(item.score(), normalizedCountry)));

                if (normalizedCountry != null) {
                    IpGeoSnapshot geo = new IpGeoSnapshot(normalizedCountry, normalizeNullable(item.region()), normalizeNullable(item.city()), null, null);
                    localGeos.put(item.ip(), geo);
                    countryValues.put(countryRedisKeyPrefix + item.ip(), writeJson(new RedisIpGeoCacheValue(
                            geo.country(),
                            geo.region(),
                            geo.city(),
                            geo.latitude(),
                            geo.longitude(),
                            "DB",
                            updatedAt
                    )));
                }
            });

            ipRiskLocalCacheStore.putRisks(localRisks);
            ipCountryLocalCacheStore.putGeos(localGeos);
            pipelineSetValues(riskValues, Duration.ofSeconds(computeTtlSeconds(riskRedisTtlMinutes, riskRedisTtlJitterMinutes)));
            pipelineSetValues(countryValues, Duration.ofSeconds(computeTtlSeconds(countryRedisTtlMinutes, countryRedisTtlJitterMinutes)));
        } catch (Exception e) {
            log.debug("Admin IP risk cache warm failed, reason={}", e.getMessage());
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void pipelineSetValues(Map<String, String> values, Duration ttl) {
        if (values == null || values.isEmpty()) {
            return;
        }
        stringRedisTemplate.executePipelined(new SessionCallback<Object>() {
            @Override
            public Object execute(RedisOperations operations) {
                values.forEach((key, value) -> operations.opsForValue().set(key, value, ttl));
                return null;
            }
        });
    }

    private AdminIpRiskListResponse readListCache(String key) {
        try {
            String value = stringRedisTemplate.opsForValue().get(key);
            if (value == null || value.isBlank()) {
                return null;
            }
            return objectMapper.readValue(value, AdminIpRiskListResponse.class);
        } catch (Exception e) {
            log.debug("Admin IP risk list cache read failed, key={}, reason={}", key, e.getMessage());
            return null;
        }
    }

    private void writeListCache(String key, AdminIpRiskListResponse response) {
        try {
            stringRedisTemplate.opsForValue().set(
                    key,
                    objectMapper.writeValueAsString(response),
                    Duration.ofSeconds(Math.max(1, listCacheTtlSeconds))
            );
        } catch (Exception e) {
            log.debug("Admin IP risk list cache write failed, key={}, reason={}", key, e.getMessage());
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private AdminIpRiskListResponse withSource(AdminIpRiskListResponse response, String source) {
        return new AdminIpRiskListResponse(
                response.family(),
                response.country(),
                response.level(),
                response.page(),
                response.pageSize(),
                response.total(),
                response.hasNext(),
                response.sort(),
                source,
                response.items()
        );
    }

    private String listCacheKey(AdminIpRiskQuery query) {
        return listCacheKeyPrefix
                + query.family() + ":"
                + (query.country() == null ? "ALL" : query.country()) + ":"
                + (query.level() == null ? "ALL" : query.level()) + ":"
                + query.sort() + ":"
                + query.page() + ":"
                + query.pageSize() + ":"
                + hashQuery(query.ipQuery());
    }

    private String hashQuery(String query) {
        if (query == null || query.isBlank()) {
            return "none";
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(query.getBytes(StandardCharsets.UTF_8));
            byte[] shortDigest = new byte[12];
            System.arraycopy(digest, 0, shortDigest, 0, shortDigest.length);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(shortDigest);
        } catch (Exception e) {
            return Integer.toHexString(query.hashCode());
        }
    }

    private String sha256Hex(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.trim().getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private String maskDeviceFingerprint(String value) {
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            return null;
        }
        if (normalized.length() <= 16) {
            return "***";
        }
        int prefixLength = Math.min(12, normalized.length() / 2);
        int suffixLength = Math.min(8, normalized.length() - prefixLength);
        return normalized.substring(0, prefixLength)
                + "..."
                + normalized.substring(normalized.length() - suffixLength);
    }

    private long computeTtlSeconds(int ttlMinutes, int jitterMinutes) {
        int min = Math.max(1, ttlMinutes);
        int jitter = Math.max(0, jitterMinutes);
        int ttl = jitter == 0 ? min : min + ThreadLocalRandom.current().nextInt(jitter + 1);
        return TimeUnit.MINUTES.toSeconds(ttl);
    }

    private String resolveLevel(int score) {
        if (score >= 8500) {
            return "L1";
        }
        if (score >= 7500) {
            return "L2";
        }
        if (score >= 6000) {
            return "L3";
        }
        if (score >= 4800) {
            return "L4";
        }
        if (score >= 3000) {
            return "L5";
        }
        return "L6";
    }

    private String countryName(String countryCode) {
        String normalized = normalizeCountryCode(countryCode);
        if (normalized == null) {
            return null;
        }
        try {
            Locale locale = new Locale.Builder().setRegion(normalized).build();
            String displayName = locale.getDisplayCountry(Locale.SIMPLIFIED_CHINESE);
            return displayName == null || displayName.isBlank() ? normalized : displayName;
        } catch (Exception e) {
            return normalized;
        }
    }

    private String dialCode(String countryCode) {
        String normalized = normalizeCountryCode(countryCode);
        return normalized == null ? null : DIAL_CODE_BY_COUNTRY.get(normalized);
    }

    private String flagCode(String countryCode) {
        String normalized = normalizeCountryCode(countryCode);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private String normalizeCountryCode(String countryCode) {
        String normalized = normalizeNullable(countryCode);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        return normalized.matches("^[A-Z]{2}$") ? normalized : null;
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() || "-".equals(normalized) ? null : normalized;
    }

    private Object value(Map<String, Object> row, String... keys) {
        if (row == null || row.isEmpty() || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (row.containsKey(key)) {
                return row.get(key);
            }
            String upperKey = key.toUpperCase(Locale.ROOT);
            if (row.containsKey(upperKey)) {
                return row.get(upperKey);
            }
        }
        return null;
    }

    private String toStringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private int toInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private long toLong(Object value, long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private boolean toBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return value != null && Boolean.parseBoolean(value.toString());
    }

    private String toIsoString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant().toString();
        }
        if (value instanceof Instant instant) {
            return instant.toString();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant().toString();
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.toInstant(ZoneOffset.UTC).toString();
        }
        return value.toString();
    }

    private record AdminIpRiskQuery(String family,
                                    String country,
                                    String level,
                                    int page,
                                    int pageSize,
                                    String sort,
                                    String ipQuery,
                                    String ipQueryPattern) {
    }

    private record AdminDeviceRiskQuery(String level,
                                        int page,
                                        int pageSize,
                                        String sort,
                                        String deviceQuery,
                                        String queryPattern) {
    }

    private AdminDeviceRiskListResponse withDeviceSource(AdminDeviceRiskListResponse response, String source) {
        return new AdminDeviceRiskListResponse(
                response.level(),
                response.page(),
                response.pageSize(),
                response.total(),
                response.hasNext(),
                response.sort(),
                source,
                response.items()
        );
    }

    private String deviceListCacheKey(AdminDeviceRiskQuery query) {
        return deviceListCacheKeyPrefix
                + (query.level() == null ? "ALL" : query.level()) + ":"
                + query.sort() + ":"
                + query.page() + ":"
                + query.pageSize() + ":"
                + hashQuery(query.deviceQuery());
    }

    private AdminDeviceRiskListResponse readDeviceListCache(String key) {
        try {
            String value = stringRedisTemplate.opsForValue().get(key);
            if (value == null || value.isBlank()) {
                return null;
            }
            return objectMapper.readValue(value, AdminDeviceRiskListResponse.class);
        } catch (Exception e) {
            log.debug("Admin device risk list cache read failed, key={}, reason={}", key, e.getMessage());
            return null;
        }
    }

    private void writeDeviceListCache(String key, AdminDeviceRiskListResponse response) {
        try {
            stringRedisTemplate.opsForValue().set(
                    key,
                    objectMapper.writeValueAsString(response),
                    Duration.ofSeconds(Math.max(1, deviceListCacheTtlSeconds))
            );
        } catch (Exception e) {
            log.debug("Admin device risk list cache write failed, key={}, reason={}", key, e.getMessage());
        }
    }

    private AdminDeviceRiskDetailResponse readDeviceDetailCache(String key) {
        try {
            String value = stringRedisTemplate.opsForValue().get(key);
            if (value == null || value.isBlank()) {
                return null;
            }
            return objectMapper.readValue(value, AdminDeviceRiskDetailResponse.class);
        } catch (Exception e) {
            log.debug("Admin device risk detail cache read failed, key={}, reason={}", key, e.getMessage());
            return null;
        }
    }

    private void writeDeviceDetailCache(String key, AdminDeviceRiskDetailResponse response) {
        try {
            stringRedisTemplate.opsForValue().set(
                    key,
                    objectMapper.writeValueAsString(response),
                    Duration.ofSeconds(Math.max(1, deviceDetailCacheTtlSeconds))
            );
        } catch (Exception e) {
            log.debug("Admin device risk detail cache write failed, key={}, reason={}", key, e.getMessage());
        }
    }

    private record ScoreRange(Integer minScore, Integer maxScoreExclusive) {
    }

    private record RedisRiskCacheValue(int score, String country) {
    }

    private record RedisIpGeoCacheValue(String country,
                                        String region,
                                        String city,
                                        Object latitude,
                                        Object longitude,
                                        String source,
                                        long updatedAtEpochMillis) {
    }
}
