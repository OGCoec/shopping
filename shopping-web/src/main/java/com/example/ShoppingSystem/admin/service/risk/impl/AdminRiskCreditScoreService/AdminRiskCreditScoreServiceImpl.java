package com.example.ShoppingSystem.admin.service.risk.impl.AdminRiskCreditScoreService;

import com.example.ShoppingSystem.common.datasource.DataSourceRoute;
import com.example.ShoppingSystem.common.datasource.RoutedTransactionExecutor;
import com.example.ShoppingSystem.admin.dto.AdminDeviceRiskDetailResponse;
import com.example.ShoppingSystem.admin.dto.AdminDeviceRiskListItemResponse;
import com.example.ShoppingSystem.admin.dto.AdminDeviceRiskListResponse;
import com.example.ShoppingSystem.admin.dto.AdminDeviceScoreEventResponse;
import com.example.ShoppingSystem.admin.dto.AdminIpRiskBatchUpdateRequest;
import com.example.ShoppingSystem.admin.dto.AdminIpRiskBatchUpdateResponse;
import com.example.ShoppingSystem.admin.dto.AdminIpRiskCountryResponse;
import com.example.ShoppingSystem.admin.dto.AdminIpRiskListItemResponse;
import com.example.ShoppingSystem.admin.dto.AdminIpRiskListResponse;
import com.example.ShoppingSystem.admin.service.common.AdminPaginationValidator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.example.ShoppingSystem.config.datasource.RiskReadReplicaQueryExecutor;
import com.example.ShoppingSystem.mapper.risk.AdminDeviceRiskProfileMapper;
import com.example.ShoppingSystem.mapper.risk.IpReputationProfileMapper;
import com.example.ShoppingSystem.quota.IpCountryLocalCacheStore;
import com.example.ShoppingSystem.quota.IpRiskLocalCacheStore;
import com.example.ShoppingSystem.service.user.auth.register.risk.IpL6CountingBloomDecisionService;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import com.example.ShoppingSystem.admin.service.common.AdminServiceException;

import com.example.ShoppingSystem.admin.service.risk.AdminRiskCreditScoreService;
@Service
public class AdminRiskCreditScoreServiceImpl implements AdminRiskCreditScoreService {

    private static final Logger log = LoggerFactory.getLogger(AdminRiskCreditScoreService.class);

    private static final String FAMILY_IPV4 = "ipv4";
    private static final String FAMILY_IPV6 = "ipv6";
    private static final String DEFAULT_SORT = "risk_first";
    private static final String SORT_RECENT_FIRST = "recent_first";
    private static final Set<String> SUPPORTED_DEVICE_SORTS = Set.of(DEFAULT_SORT, SORT_RECENT_FIRST);
    private static final int DEFAULT_PAGE_SIZE = 50;
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
    private final RiskReadReplicaQueryExecutor riskReadReplicaQueryExecutor;
    private final RoutedTransactionExecutor routedTransactionExecutor;

    @Value("${register.ip-risk-multi-level.redis-key-prefix:register:ip:risk:v2:}")
    private String riskRedisKeyPrefix;

    @Value("${register.ip-country-cache.redis-key-prefix:register:ip:country:}")
    private String countryRedisKeyPrefix;

    public AdminRiskCreditScoreServiceImpl(AdminDeviceRiskProfileMapper adminDeviceRiskProfileMapper,
                                       IpReputationProfileMapper ipReputationProfileMapper,
                                       StringRedisTemplate stringRedisTemplate,
                                       ObjectMapper objectMapper,
                                       IpRiskLocalCacheStore ipRiskLocalCacheStore,
                                       IpCountryLocalCacheStore ipCountryLocalCacheStore,
                                       IpL6CountingBloomDecisionService ipL6CountingBloomDecisionService,
                                       RiskReadReplicaQueryExecutor riskReadReplicaQueryExecutor,
                                       RoutedTransactionExecutor routedTransactionExecutor) {
        this.adminDeviceRiskProfileMapper = adminDeviceRiskProfileMapper;
        this.ipReputationProfileMapper = ipReputationProfileMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.ipRiskLocalCacheStore = ipRiskLocalCacheStore;
        this.ipCountryLocalCacheStore = ipCountryLocalCacheStore;
        this.ipL6CountingBloomDecisionService = ipL6CountingBloomDecisionService;
        this.riskReadReplicaQueryExecutor = riskReadReplicaQueryExecutor;
        this.routedTransactionExecutor = routedTransactionExecutor;
    }

    public AdminIpRiskListResponse listIpRiskProfiles(String family,
                                                      String country,
                                                      String level,
                                                      int page,
                                                      int pageSize,
                                                      String sort,
                                                      String q) {
        AdminIpRiskQuery query = normalizeQuery(family, country, level, page, pageSize, sort, q);
        ScoreRange scoreRange = scoreRange(query.level());
        PageInfo<Map<String, Object>> pageInfo = riskReadReplicaQueryExecutor.query(
                () -> pageIpRiskProfiles(query, scoreRange));
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
        int dbUpdated = routedTransactionExecutor.execute(DataSourceRoute.RISK, () -> {
            if (FAMILY_IPV4.equals(normalizedFamily)) {
                return ipReputationProfileMapper.batchUpdateIpv4Scores(normalizedIps, targetScore);
            }
            return ipReputationProfileMapper.batchUpdateIpv6Scores(normalizedIps, targetScore);
        });

        // ── 2) 一次 Redis 批量删除（风险缓存 + 国家缓存） ──
        int cacheDeleted = 0;
        try {
            List<String> cacheKeys = new ArrayList<>();
            normalizedIps.forEach(ip -> {
                cacheKeys.add(riskRedisKeyPrefix + ip);
                cacheKeys.add(countryRedisKeyPrefix + ip);
            });
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
        try {
            ipRiskLocalCacheStore.invalidateAll(normalizedIps);
            ipCountryLocalCacheStore.invalidateAll(normalizedIps);
        } catch (Exception e) {
            log.warn("Admin IP risk local cache batch invalidation failed, reason={}", e.getMessage());
        }

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

    private PageInfo<Map<String, Object>> pageDeviceRiskProfiles(AdminDeviceRiskQuery query, ScoreRange scoreRange) {
        try {
            return PageHelper.startPage(query.page(), query.pageSize(), true)
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
    }

    private AdminDeviceDetailRows findDeviceDetailRows(String normalizedId) {
        Map<String, Object> row = adminDeviceRiskProfileMapper.findDeviceById(normalizedId);
        if (row == null || row.isEmpty()) {
            return new AdminDeviceDetailRows(row, List.of());
        }
        List<Map<String, Object>> eventRows = adminDeviceRiskProfileMapper.listScoreEventsByDeviceId(
                normalizedId, DEVICE_DETAIL_EVENT_LIMIT);
        return new AdminDeviceDetailRows(row, eventRows);
    }

    public AdminDeviceRiskListResponse listDeviceRiskProfiles(String level, int page, int pageSize, String sort, String q) {
        AdminDeviceRiskQuery query = normalizeDeviceQuery(level, page, pageSize, sort, q);
        ScoreRange scoreRange = scoreRange(query.level());
        PageInfo<Map<String, Object>> pageInfo = riskReadReplicaQueryExecutor.query(
                () -> pageDeviceRiskProfiles(query, scoreRange));

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
        return response;
    }

    public AdminDeviceRiskDetailResponse getDeviceDetail(String deviceId) {
        String normalizedId = normalizeDeviceId(deviceId);
        AdminDeviceDetailRows detailRows = riskReadReplicaQueryExecutor.query(
                () -> findDeviceDetailRows(normalizedId));
        Map<String, Object> row = detailRows.row();
        if (row == null || row.isEmpty()) {
            throw new AdminServiceException("ADMIN_RISK_DEVICE_NOT_FOUND", "设备不存在。", HttpStatus.NOT_FOUND);
        }

        List<Map<String, Object>> eventRows = detailRows.eventRows();
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

        int safePage = AdminPaginationValidator.normalizePage(page);
        int safePageSize = AdminPaginationValidator.normalizePageSize(pageSize);
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

        int safePage = AdminPaginationValidator.normalizePage(page);
        int safePageSize = AdminPaginationValidator.normalizePageSize(pageSize);
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

    private record ScoreRange(Integer minScore, Integer maxScoreExclusive) {
    }

    private record AdminDeviceDetailRows(Map<String, Object> row,
                                         List<Map<String, Object>> eventRows) {
    }
}
