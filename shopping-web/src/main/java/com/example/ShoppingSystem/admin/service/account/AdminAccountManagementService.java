package com.example.ShoppingSystem.admin.service.account;

import com.example.ShoppingSystem.Utils.SnowflakeIdWorker;
import com.example.ShoppingSystem.admin.dto.AdminAccountManagementDtos.AccountCreditDetailResponse;
import com.example.ShoppingSystem.admin.dto.AdminAccountManagementDtos.AccountCreditListItemResponse;
import com.example.ShoppingSystem.admin.dto.AdminAccountManagementDtos.AccountCreditListResponse;
import com.example.ShoppingSystem.admin.dto.AdminAccountManagementDtos.AccountLoginRecordResponse;
import com.example.ShoppingSystem.admin.dto.AdminAccountManagementDtos.AccountRestoreRequest;
import com.example.ShoppingSystem.admin.dto.AdminAccountManagementDtos.AccountRestoreResponse;
import com.example.ShoppingSystem.admin.dto.AdminAccountManagementDtos.AccountRiskScoreEventListResponse;
import com.example.ShoppingSystem.admin.dto.AdminAccountManagementDtos.AccountRiskScoreEventResponse;
import com.example.ShoppingSystem.admin.dto.AdminAccountManagementDtos.AccountRiskTerminationDetailResponse;
import com.example.ShoppingSystem.admin.dto.AdminAccountManagementDtos.AccountRiskTerminationItemResponse;
import com.example.ShoppingSystem.admin.dto.AdminAccountManagementDtos.AccountRiskTerminationListResponse;
import com.example.ShoppingSystem.admin.dto.AdminAccountManagementDtos.AccountScoreAdjustRequest;
import com.example.ShoppingSystem.admin.dto.AdminAccountManagementDtos.AccountScoreAdjustResponse;
import com.example.ShoppingSystem.admin.dto.AdminAccountManagementDtos.AccountSelfTerminationItemResponse;
import com.example.ShoppingSystem.admin.dto.AdminAccountManagementDtos.AccountSelfTerminationListResponse;
import com.example.ShoppingSystem.admin.service.common.AdminPaginationValidator;
import com.example.ShoppingSystem.mapper.admin.AdminAccountManagementMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.postgresql.util.PGobject;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import com.example.ShoppingSystem.admin.service.common.AdminServiceException;

@Service
public class AdminAccountManagementService {

    private static final int DETAIL_EVENT_LIMIT = 20;
    private static final String EVENT_ADMIN_SCORE_ADJUST = "ADMIN_SCORE_ADJUST";
    private static final Set<String> SUPPORTED_LEVELS = Set.of("L1", "L2", "L3", "L4", "L5", "L6");
    private static final Set<String> SUPPORTED_STATUSES = Set.of("ACTIVE", "DISABLED", "LOCKED", "RISK_TERMINATED");
    private static final Set<String> SUPPORTED_SELF_SCOPES = Set.of("within7Days", "expired", "deleted", "restored");

    private final AdminAccountManagementMapper adminAccountManagementMapper;
    private final SnowflakeIdWorker snowflakeIdWorker;
    private final ObjectMapper objectMapper;

    public AdminAccountManagementService(AdminAccountManagementMapper adminAccountManagementMapper,
                                         SnowflakeIdWorker snowflakeIdWorker,
                                         ObjectMapper objectMapper) {
        this.adminAccountManagementMapper = adminAccountManagementMapper;
        this.snowflakeIdWorker = snowflakeIdWorker;
        this.objectMapper = objectMapper;
    }

    public AccountCreditListResponse listAccountCredits(Long userId,
                                                        String email,
                                                        String phone,
                                                        String status,
                                                        String riskLevel,
                                                        int page,
                                                        int pageSize) {
        String normalizedStatus = normalizeStatus(status);
        String normalizedLevel = normalizeRiskLevel(riskLevel);
        String emailPattern = likePattern(normalizeEmail(email));
        String phonePattern = likePattern(normalizeText(phone));
        int safePage = AdminPaginationValidator.normalizePage(page);
        int safePageSize = safePageSize(pageSize);

        PageInfo<Map<String, Object>> pageInfo = page(() ->
                adminAccountManagementMapper.listAccountCreditProfiles(
                        userId,
                        emailPattern,
                        phonePattern,
                        normalizedStatus,
                        normalizedLevel
                ),
                safePage,
                safePageSize
        );
        List<AccountCreditListItemResponse> items = pageInfo.getList()
                .stream()
                .map(this::toCreditListItem)
                .toList();
        return new AccountCreditListResponse(
                normalizedLevel,
                normalizedStatus,
                safePage,
                safePageSize,
                pageInfo.getTotal(),
                pageInfo.isHasNextPage(),
                items
        );
    }

    public AccountCreditDetailResponse getAccountCreditDetail(Long userId) {
        Long safeUserId = requireId(userId, "ADMIN_ACCOUNT_USER_ID_REQUIRED", "用户 ID 不能为空。");
        Map<String, Object> row = adminAccountManagementMapper.findAccountCreditDetail(safeUserId);
        if (row == null || row.isEmpty()) {
            throw new AdminServiceException("ADMIN_ACCOUNT_CREDIT_NOT_FOUND", "账号信用分记录不存在。", HttpStatus.NOT_FOUND);
        }
        AccountLoginRecordResponse firstLogin = toLoginRecord(adminAccountManagementMapper.findFirstLoginRecord(safeUserId));
        List<AccountRiskScoreEventResponse> recentEvents = adminAccountManagementMapper
                .listRecentRiskScoreEvents(safeUserId, DETAIL_EVENT_LIMIT)
                .stream()
                .map(this::toRiskScoreEvent)
                .toList();
        return toCreditDetail(row, firstLogin, recentEvents);
    }

    public AccountRiskScoreEventListResponse listAccountCreditEvents(Long userId, int page, int pageSize) {
        Long safeUserId = requireId(userId, "ADMIN_ACCOUNT_USER_ID_REQUIRED", "用户 ID 不能为空。");
        int safePage = AdminPaginationValidator.normalizePage(page);
        int safePageSize = safePageSize(pageSize);
        PageInfo<Map<String, Object>> pageInfo = page(
                () -> adminAccountManagementMapper.listRiskScoreEvents(safeUserId),
                safePage,
                safePageSize
        );
        List<AccountRiskScoreEventResponse> items = pageInfo.getList()
                .stream()
                .map(this::toRiskScoreEvent)
                .toList();
        return new AccountRiskScoreEventListResponse(
                safeUserId,
                safePage,
                safePageSize,
                pageInfo.getTotal(),
                pageInfo.isHasNextPage(),
                items
        );
    }

    @Transactional
    public AccountScoreAdjustResponse adjustAccountScore(Long userId,
                                                         AccountScoreAdjustRequest request,
                                                         String adminUsername) {
        Long safeUserId = requireId(userId, "ADMIN_ACCOUNT_USER_ID_REQUIRED", "用户 ID 不能为空。");
        int scoreDelta = request == null || request.scoreDelta() == null ? 0 : request.scoreDelta();
        if (scoreDelta == 0) {
            throw new AdminServiceException("ADMIN_ACCOUNT_SCORE_DELTA_INVALID", "调整分数不能为 0。", HttpStatus.BAD_REQUEST);
        }
        String reason = normalizeText(request == null ? null : request.reason());
        if (reason == null) {
            throw new AdminServiceException("ADMIN_ACCOUNT_SCORE_REASON_REQUIRED", "管理员调整原因不能为空。", HttpStatus.BAD_REQUEST);
        }
        if (reason.length() > 128) {
            throw new AdminServiceException("ADMIN_ACCOUNT_SCORE_REASON_TOO_LONG", "管理员调整原因不能超过 128 个字符。", HttpStatus.BAD_REQUEST);
        }

        Map<String, Object> row = adminAccountManagementMapper.lockRiskProfileForAdjust(safeUserId);
        if (row == null || row.isEmpty()) {
            throw new AdminServiceException("ADMIN_ACCOUNT_CREDIT_NOT_FOUND", "账号信用分记录不存在。", HttpStatus.NOT_FOUND);
        }

        int scoreBefore = toInt(value(row, "currentScore", "current_score"), 0);
        long nextScore = (long) scoreBefore + scoreDelta;
        if (nextScore < 0L || nextScore > 10000L) {
            throw new AdminServiceException("ADMIN_ACCOUNT_SCORE_OUT_OF_RANGE", "调整后的信用分必须在 0-10000 之间。", HttpStatus.BAD_REQUEST);
        }
        int scoreAfter = (int) nextScore;
        String riskLevelBefore = normalizeNullable(toStringValue(value(row, "riskLevel", "risk_level")));
        if (riskLevelBefore == null) {
            riskLevelBefore = resolveRiskLevel(scoreBefore);
        }
        String riskLevelAfter = resolveRiskLevel(scoreAfter);
        OffsetDateTime now = OffsetDateTime.now();

        int updated = adminAccountManagementMapper.updateRiskProfileScore(
                safeUserId,
                scoreDelta,
                scoreAfter,
                riskLevelAfter,
                now
        );
        if (updated <= 0) {
            throw new AdminServiceException("ADMIN_ACCOUNT_SCORE_UPDATE_FAILED", "账号信用分更新失败。", HttpStatus.CONFLICT);
        }

        adminAccountManagementMapper.insertRiskScoreEvent(
                snowflakeIdWorker.nextId(),
                safeUserId,
                EVENT_ADMIN_SCORE_ADJUST,
                scoreBefore,
                scoreDelta,
                scoreAfter,
                riskLevelBefore,
                riskLevelAfter,
                reason,
                buildAdjustMetadata(adminUsername, scoreDelta, reason),
                now
        );

        return new AccountScoreAdjustResponse(
                safeUserId,
                scoreBefore,
                scoreDelta,
                scoreAfter,
                riskLevelBefore,
                riskLevelAfter,
                EVENT_ADMIN_SCORE_ADJUST,
                toIsoString(now)
        );
    }

    public AccountSelfTerminationListResponse listSelfTerminations(String scope,
                                                                   Long userId,
                                                                   String email,
                                                                   String phone,
                                                                   int page,
                                                                   int pageSize) {
        String normalizedScope = normalizeSelfScope(scope);
        int safePage = AdminPaginationValidator.normalizePage(page);
        int safePageSize = safePageSize(pageSize);
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(7);
        PageInfo<Map<String, Object>> pageInfo = page(
                () -> adminAccountManagementMapper.listSelfTerminations(
                        normalizedScope,
                        userId,
                        likePattern(normalizeEmail(email)),
                        likePattern(normalizeText(phone)),
                        cutoff
                ),
                safePage,
                safePageSize
        );
        List<AccountSelfTerminationItemResponse> items = pageInfo.getList()
                .stream()
                .map(this::toSelfTerminationItem)
                .toList();
        return new AccountSelfTerminationListResponse(
                normalizedScope,
                safePage,
                safePageSize,
                pageInfo.getTotal(),
                pageInfo.isHasNextPage(),
                items
        );
    }

    @Transactional
    public AccountRestoreResponse restoreSelfTermination(Long id,
                                                         AccountRestoreRequest request,
                                                         String adminUsername) {
        Long safeId = requireId(id, "ADMIN_ACCOUNT_SELF_TERMINATION_ID_REQUIRED", "停用记录 ID 不能为空。");
        Map<String, Object> row = adminAccountManagementMapper.findSelfTerminationById(safeId);
        if (row == null || row.isEmpty()) {
            throw new AdminServiceException("ADMIN_ACCOUNT_SELF_TERMINATION_NOT_FOUND", "主动停用记录不存在。", HttpStatus.NOT_FOUND);
        }
        Long userId = toLong(value(row, "userId", "user_id"), 0L);
        OffsetDateTime deletedAt = toOffsetDateTime(value(row, "deletedAt", "deleted_at"));
        OffsetDateTime restoredAt = toOffsetDateTime(value(row, "restoredAt", "restored_at"));
        boolean deleted = toBoolean(value(row, "deleted", "is_deleted"));
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(7);
        if (userId == null || userId <= 0L || deleted || restoredAt != null || deletedAt == null || deletedAt.isBefore(cutoff)) {
            throw new AdminServiceException("ADMIN_ACCOUNT_SELF_TERMINATION_NOT_RESTORABLE", "该主动停用记录不能恢复。", HttpStatus.BAD_REQUEST);
        }

        OffsetDateTime now = OffsetDateTime.now();
        String restoreReason = normalizeText(request == null ? null : request.reason());
        if (restoreReason != null && restoreReason.length() > 512) {
            restoreReason = restoreReason.substring(0, 512);
        }
        int marked = adminAccountManagementMapper.markSelfTerminationRestored(
                safeId,
                now,
                normalizeAdmin(adminUsername),
                restoreReason,
                cutoff
        );
        if (marked <= 0) {
            throw new AdminServiceException("ADMIN_ACCOUNT_SELF_TERMINATION_RESTORE_FAILED", "主动停用记录恢复失败。", HttpStatus.CONFLICT);
        }
        int restoredIdentity = adminAccountManagementMapper.restoreDisabledIdentity(userId, now);
        if (restoredIdentity <= 0) {
            throw new AdminServiceException("ADMIN_ACCOUNT_SELF_TERMINATION_STATUS_CONFLICT", "账号当前不是主动停用状态，不能恢复。", HttpStatus.CONFLICT);
        }
        return new AccountRestoreResponse(safeId, userId, "ACTIVE", toIsoString(now));
    }

    public AccountRiskTerminationListResponse listRiskTerminations(Long userId,
                                                                   String email,
                                                                   String phone,
                                                                   int page,
                                                                   int pageSize) {
        int safePage = AdminPaginationValidator.normalizePage(page);
        int safePageSize = safePageSize(pageSize);
        PageInfo<Map<String, Object>> pageInfo = page(
                () -> adminAccountManagementMapper.listRiskTerminations(
                        userId,
                        likePattern(normalizeEmail(email)),
                        likePattern(normalizeText(phone))
                ),
                safePage,
                safePageSize
        );
        List<AccountRiskTerminationItemResponse> items = pageInfo.getList()
                .stream()
                .map(this::toRiskTerminationItem)
                .toList();
        return new AccountRiskTerminationListResponse(
                safePage,
                safePageSize,
                pageInfo.getTotal(),
                pageInfo.isHasNextPage(),
                items
        );
    }

    public AccountRiskTerminationDetailResponse getRiskTerminationDetail(Long id) {
        Long safeId = requireId(id, "ADMIN_ACCOUNT_RISK_TERMINATION_ID_REQUIRED", "风控停用记录 ID 不能为空。");
        Map<String, Object> row = adminAccountManagementMapper.findRiskTerminationById(safeId);
        if (row == null || row.isEmpty()) {
            throw new AdminServiceException("ADMIN_ACCOUNT_RISK_TERMINATION_NOT_FOUND", "风控停用记录不存在。", HttpStatus.NOT_FOUND);
        }
        AccountRiskTerminationItemResponse termination = toRiskTerminationItem(row);
        List<AccountRiskScoreEventResponse> recentEvents = adminAccountManagementMapper
                .listRecentRiskScoreEvents(termination.userId(), DETAIL_EVENT_LIMIT)
                .stream()
                .map(this::toRiskScoreEvent)
                .toList();
        return new AccountRiskTerminationDetailResponse(termination, recentEvents);
    }

    private <T> PageInfo<T> page(SelectCallback<T> callback, int page, int pageSize) {
        try {
            return PageHelper.startPage(page, pageSize, true)
                    .doSelectPageInfo(callback::select);
        } finally {
            PageHelper.clearPage();
        }
    }

    private AccountCreditListItemResponse toCreditListItem(Map<String, Object> row) {
        return new AccountCreditListItemResponse(
                toLong(value(row, "userId", "user_id"), null),
                toStringValue(value(row, "email")),
                toStringValue(value(row, "phone")),
                toStringValue(value(row, "status")),
                toInt(value(row, "currentScore", "current_score"), 0),
                toStringValue(value(row, "riskLevel", "risk_level")),
                Math.max(0, toInt(value(row, "lockCount", "lock_count"), 0)),
                toStringValue(value(row, "lockReason", "lock_reason")),
                toIsoString(value(row, "lockUntil", "lock_until")),
                toIsoString(value(row, "lastLoginAt", "last_login_at")),
                toIsoString(value(row, "updatedAt", "updated_at"))
        );
    }

    private AccountCreditDetailResponse toCreditDetail(Map<String, Object> row,
                                                       AccountLoginRecordResponse firstLogin,
                                                       List<AccountRiskScoreEventResponse> recentEvents) {
        return new AccountCreditDetailResponse(
                toLong(value(row, "userId", "user_id"), null),
                toStringValue(value(row, "email")),
                toStringValue(value(row, "phone")),
                toStringValue(value(row, "status")),
                toInt(value(row, "currentScore", "current_score"), 0),
                toStringValue(value(row, "riskLevel", "risk_level")),
                toInt(value(row, "currentEnvScore", "current_env_score"), 0),
                toInt(value(row, "behaviorScoreDelta", "behavior_score_delta"), 0),
                Math.max(0, toInt(value(row, "lockCount", "lock_count"), 0)),
                toStringValue(value(row, "lockReason", "lock_reason")),
                toIsoString(value(row, "lockUntil", "lock_until")),
                toIsoString(value(row, "riskRecoveryStartedAt", "risk_recovery_started_at")),
                toIsoString(value(row, "lastRiskPenaltyAt", "last_risk_penalty_at")),
                toIsoString(value(row, "lastLoginAt", "last_login_at")),
                toStringValue(value(row, "lastLoginIp", "last_login_ip")),
                toStringValue(value(row, "lastDeviceFingerprint", "last_device_fingerprint")),
                toIsoString(value(row, "updatedAt", "updated_at")),
                firstLogin,
                recentEvents
        );
    }

    private AccountLoginRecordResponse toLoginRecord(Map<String, Object> row) {
        if (row == null || row.isEmpty()) {
            return null;
        }
        return new AccountLoginRecordResponse(
                toStringValue(value(row, "loginType", "login_type")),
                toStringValue(value(row, "loginIp", "login_ip")),
                toStringValue(value(row, "userAgent", "user_agent")),
                toStringValue(value(row, "deviceFingerprint", "device_fingerprint")),
                toIsoString(value(row, "loginAt", "login_at"))
        );
    }

    private AccountRiskScoreEventResponse toRiskScoreEvent(Map<String, Object> row) {
        return new AccountRiskScoreEventResponse(
                toLong(value(row, "id"), null),
                toStringValue(value(row, "eventType", "event_type")),
                toInt(value(row, "scoreBefore", "score_before"), 0),
                toInt(value(row, "scoreDelta", "score_delta"), 0),
                toInt(value(row, "scoreAfter", "score_after"), 0),
                toStringValue(value(row, "riskLevelBefore", "risk_level_before")),
                toStringValue(value(row, "riskLevelAfter", "risk_level_after")),
                toStringValue(value(row, "reason")),
                toStringValue(value(row, "ip")),
                toStringValue(value(row, "deviceFingerprint", "device_fingerprint")),
                metadataToString(value(row, "metadata")),
                toIsoString(value(row, "createdAt", "created_at"))
        );
    }

    private AccountSelfTerminationItemResponse toSelfTerminationItem(Map<String, Object> row) {
        return new AccountSelfTerminationItemResponse(
                toLong(value(row, "id"), null),
                toLong(value(row, "userId", "user_id"), null),
                toStringValue(value(row, "email")),
                toStringValue(value(row, "phone")),
                toStringValue(value(row, "status")),
                toBoolean(value(row, "deleted", "is_deleted")),
                toBoolean(value(row, "restorable")),
                toStringValue(value(row, "deletionReason", "deletion_reason")),
                toIsoString(value(row, "deletedAt", "deleted_at")),
                toIsoString(value(row, "createdAt", "created_at")),
                toIsoString(value(row, "restoredAt", "restored_at")),
                toStringValue(value(row, "restoredBy", "restored_by")),
                toStringValue(value(row, "restoreReason", "restore_reason"))
        );
    }

    private AccountRiskTerminationItemResponse toRiskTerminationItem(Map<String, Object> row) {
        return new AccountRiskTerminationItemResponse(
                toLong(value(row, "id"), null),
                toLong(value(row, "userId", "user_id"), null),
                toStringValue(value(row, "email")),
                toStringValue(value(row, "phone")),
                toStringValue(value(row, "status")),
                toInt(value(row, "currentScore", "current_score"), 0),
                toStringValue(value(row, "riskLevel", "risk_level")),
                Math.max(0, toInt(value(row, "lockCount", "lock_count"), 0)),
                toStringValue(value(row, "terminationReason", "termination_reason")),
                toIsoString(value(row, "terminatedAt", "terminated_at")),
                toIsoString(value(row, "createdAt", "created_at"))
        );
    }

    private String buildAdjustMetadata(String adminUsername, int scoreDelta, String reason) {
        try {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("operatorType", "ADMIN");
            metadata.put("admin", normalizeAdmin(adminUsername));
            metadata.put("action", scoreDelta > 0 ? "ADD" : "DEDUCT");
            metadata.put("reason", reason);
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception e) {
            return "{}";
        }
    }

    private Long requireId(Long id, String code, String message) {
        if (id == null || id <= 0L) {
            throw new AdminServiceException(code, message, HttpStatus.BAD_REQUEST);
        }
        return id;
    }

    private int safePageSize(int pageSize) {
        return AdminPaginationValidator.normalizePageSize(pageSize);
    }

    private String normalizeStatus(String status) {
        String normalized = normalizeText(status);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!SUPPORTED_STATUSES.contains(normalized)) {
            throw new AdminServiceException("ADMIN_ACCOUNT_STATUS_INVALID", "账号状态无效。", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String normalizeRiskLevel(String riskLevel) {
        String normalized = normalizeText(riskLevel);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!SUPPORTED_LEVELS.contains(normalized)) {
            throw new AdminServiceException("ADMIN_ACCOUNT_RISK_LEVEL_INVALID", "账号风险等级无效。", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String normalizeSelfScope(String scope) {
        String normalized = normalizeText(scope);
        if (normalized == null) {
            return null;
        }
        if (!SUPPORTED_SELF_SCOPES.contains(normalized)) {
            throw new AdminServiceException("ADMIN_ACCOUNT_SELF_SCOPE_INVALID", "主动停用筛选范围无效。", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String resolveRiskLevel(int score) {
        int safeScore = Math.max(0, Math.min(10000, score));
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

    private String likePattern(String value) {
        return value == null ? null : "%" + value + "%";
    }

    private String normalizeAdmin(String adminUsername) {
        String normalized = normalizeText(adminUsername);
        return normalized == null ? "admin" : normalized;
    }

    private String normalizeEmail(String email) {
        String normalized = normalizeText(email);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private String normalizeNullable(String value) {
        return normalizeText(value);
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
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

    private String metadataToString(Object value) {
        if (value instanceof PGobject pgObject) {
            return pgObject.getValue();
        }
        return toStringValue(value);
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

    private Long toLong(Object value, Long fallback) {
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

    private OffsetDateTime toOffsetDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime;
        }
        if (value instanceof Instant instant) {
            return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
        }
        if (value instanceof Timestamp timestamp) {
            return OffsetDateTime.ofInstant(timestamp.toInstant(), ZoneOffset.UTC);
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.atOffset(ZoneOffset.UTC);
        }
        String text = normalizeText(value.toString());
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

    private String toIsoString(Object value) {
        OffsetDateTime dateTime = toOffsetDateTime(value);
        return dateTime == null ? null : dateTime.toInstant().toString();
    }

    @FunctionalInterface
    private interface SelectCallback<T> {
        List<T> select();
    }
}
