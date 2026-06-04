package com.example.ShoppingSystem.coupon.service;

import com.example.ShoppingSystem.Utils.HybridIdCodec;
import com.example.ShoppingSystem.Utils.HybridSemaphoreIdWorker;
import com.example.ShoppingSystem.admin.service.common.AdminServiceException;
import com.example.ShoppingSystem.coupon.dto.AdminCouponClaimPageResponse;
import com.example.ShoppingSystem.coupon.dto.AdminCouponClaimResponse;
import com.example.ShoppingSystem.coupon.dto.AdminCouponTemplatePageResponse;
import com.example.ShoppingSystem.coupon.dto.AdminCouponTemplateRequest;
import com.example.ShoppingSystem.coupon.dto.AdminCouponTemplateResponse;
import com.example.ShoppingSystem.config.datasource.CouponReadReplicaQueryExecutor;
import com.example.ShoppingSystem.mapper.coupon.CouponScopeMapper;
import com.example.ShoppingSystem.mapper.coupon.CouponTemplateMapper;
import com.example.ShoppingSystem.mapper.coupon.UserCouponMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class AdminCouponService {

    private static final Set<String> SUPPORTED_DISCOUNT_TYPES = Set.of("AMOUNT", "PERCENT");
    private static final Set<String> SUPPORTED_SCOPE_TYPES = Set.of("ALL", "CATEGORY", "SPU", "SKU");
    private static final Set<String> SUPPORTED_STATUS_FILTERS = Set.of("DRAFT", "ACTIVE", "DISABLED", "EXPIRED", "DELETED");
    private static final Set<String> SUPPORTED_USER_COUPON_STATUS_FILTERS = Set.of("UNUSED", "LOCKED", "USED", "EXPIRED", "REVOKED");

    private final CouponTemplateMapper couponTemplateMapper;
    private final CouponScopeMapper couponScopeMapper;
    private final UserCouponMapper userCouponMapper;
    private final HybridSemaphoreIdWorker hybridSemaphoreIdWorker;
    private final CouponRedisCacheService couponRedisCacheService;
    private final CouponTemplateSearchService couponTemplateSearchService;
    private final AdminCouponTemplateIndexService couponTemplateIndexService;
    private final CouponReadReplicaQueryExecutor couponReadReplicaQueryExecutor;
    private final ObjectMapper objectMapper;

    public AdminCouponService(CouponTemplateMapper couponTemplateMapper,
                              CouponScopeMapper couponScopeMapper,
                              UserCouponMapper userCouponMapper,
                              HybridSemaphoreIdWorker hybridSemaphoreIdWorker,
                              CouponRedisCacheService couponRedisCacheService,
                              CouponTemplateSearchService couponTemplateSearchService,
                              AdminCouponTemplateIndexService couponTemplateIndexService,
                              CouponReadReplicaQueryExecutor couponReadReplicaQueryExecutor,
                              ObjectMapper objectMapper) {
        this.couponTemplateMapper = couponTemplateMapper;
        this.couponScopeMapper = couponScopeMapper;
        this.userCouponMapper = userCouponMapper;
        this.hybridSemaphoreIdWorker = hybridSemaphoreIdWorker;
        this.couponRedisCacheService = couponRedisCacheService;
        this.couponTemplateSearchService = couponTemplateSearchService;
        this.couponTemplateIndexService = couponTemplateIndexService;
        this.couponReadReplicaQueryExecutor = couponReadReplicaQueryExecutor;
        this.objectMapper = objectMapper;
    }

    public AdminCouponTemplatePageResponse page(Integer rawPage,
                                                Integer rawPageSize,
                                                String name,
                                                String rawStatus,
                                                String rawReceiveStartAtFrom,
                                                String rawReceiveEndAtTo) {
        int page = rawPage == null || rawPage <= 0 ? 1 : rawPage;
        int pageSize = rawPageSize == null || rawPageSize <= 0 ? 20 : Math.min(rawPageSize, 100);
        String status = normalizeOptionalStatus(rawStatus);
        String keyword = normalizeText(name);
        OffsetDateTime receiveStartAtFrom = parseOptionalDateTime(
                rawReceiveStartAtFrom,
                "ADMIN_COUPON_RECEIVE_START_FROM_INVALID",
                "Coupon receive start time filter is invalid.");
        OffsetDateTime receiveEndAtTo = parseOptionalDateTime(
                rawReceiveEndAtTo,
                "ADMIN_COUPON_RECEIVE_END_TO_INVALID",
                "Coupon receive end time filter is invalid.");
        if (receiveStartAtFrom != null
                && receiveEndAtTo != null
                && receiveStartAtFrom.toInstant().isAfter(receiveEndAtTo.toInstant())) {
            throw badRequest("ADMIN_COUPON_RECEIVE_TIME_FILTER_INVALID", "Coupon receive time filter range is invalid.");
        }
        if (!keyword.isBlank()) {
            return searchPage(page, pageSize, keyword, status, receiveStartAtFrom, receiveEndAtTo);
        }
        return couponReadReplicaQueryExecutor.query(() -> {
            int offset = (page - 1) * pageSize;
            long total = couponTemplateMapper.countTemplates(status, receiveStartAtFrom, receiveEndAtTo);
            List<AdminCouponTemplateResponse> records = couponTemplateMapper.listTemplates(
                            offset,
                            pageSize,
                            status,
                            receiveStartAtFrom,
                            receiveEndAtTo)
                    .stream()
                    .map(row -> toResponse(row, List.of()))
                    .toList();
            return new AdminCouponTemplatePageResponse(page, pageSize, total, records);
        });
    }

    public AdminCouponTemplateResponse detail(String rawId) {
        byte[] id = couponIdBytes(rawId);
        return couponReadReplicaQueryExecutor.query(() -> {
            Map<String, Object> row = readTemplate(id);
            return toResponse(row, couponScopeMapper.listByTemplateId(id));
        });
    }

    public AdminCouponClaimPageResponse claimPage(String rawId,
                                                  Integer rawPage,
                                                  Integer rawPageSize,
                                                  String rawStatus,
                                                  String rawEmail) {
        byte[] couponTemplateId = couponIdBytes(rawId);
        int page = rawPage == null || rawPage <= 0 ? 1 : rawPage;
        int pageSize = rawPageSize == null || rawPageSize <= 0 ? 20 : Math.min(rawPageSize, 100);
        int offset = (page - 1) * pageSize;
        String status = normalizeOptionalUserCouponStatus(rawStatus);
        String email = normalizeOptionalEmail(rawEmail);
        return couponReadReplicaQueryExecutor.query(() -> {
            List<Map<String, Object>> rows = userCouponMapper.listAdminClaimsByTemplateId(
                    couponTemplateId,
                    status,
                    email,
                    offset,
                    pageSize);
            if (rows.isEmpty() || !toBoolean(rows.get(0).get("templateExists"))) {
            throw new AdminServiceException("ADMIN_COUPON_NOT_FOUND", "优惠券不存在。", HttpStatus.NOT_FOUND);
            }
            long total = toLong(rows.get(0).get("total"));
            List<AdminCouponClaimResponse> records = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                if (HybridIdCodec.toBase62FromDatabaseValue(row.get("userCouponId")).isBlank()) {
                    continue;
                }
                records.add(toClaimResponse(row));
            }
            return new AdminCouponClaimPageResponse(page, pageSize, total, records);
        });
    }

    @Transactional
    public AdminCouponTemplateResponse create(AdminCouponTemplateRequest request) {
        NormalizedCouponRequest normalized = normalizeRequest(request);
        byte[] id = hybridSemaphoreIdWorker.nextId();
        couponTemplateMapper.insertTemplate(
                id,
                normalized.couponCode(),
                normalized.name(),
                normalized.discountType(),
                normalized.thresholdAmountYuan(),
                normalized.discountAmountYuan(),
                normalized.discountRate(),
                normalized.maxDiscountAmountYuan(),
                normalized.totalQuantity(),
                normalized.totalQuantity(),
                normalized.perUserLimit(),
                normalized.scopeType(),
                normalized.receiveStartAt(),
                normalized.receiveEndAt(),
                normalized.validStartAt(),
                normalized.validEndAt()
        );
        replaceScopes(id, normalized);
        couponTemplateIndexService.syncCouponTemplatesAfterCommit(List.of(HybridIdCodec.toBase62(id)));
        return toResponse(requireTemplate(id), couponScopeMapper.listByTemplateId(id));
    }

    @Transactional
    public AdminCouponTemplateResponse update(String rawId, AdminCouponTemplateRequest request) {
        byte[] id = couponIdBytes(rawId);
        requireTemplate(id);
        NormalizedCouponRequest normalized = normalizeRequest(request);
        int affected = couponTemplateMapper.updateTemplate(
                id,
                normalized.couponCode(),
                normalized.name(),
                normalized.discountType(),
                normalized.thresholdAmountYuan(),
                normalized.discountAmountYuan(),
                normalized.discountRate(),
                normalized.maxDiscountAmountYuan(),
                normalized.totalQuantity(),
                normalized.totalQuantity(),
                normalized.perUserLimit(),
                normalized.scopeType(),
                normalized.receiveStartAt(),
                normalized.receiveEndAt(),
                normalized.validStartAt(),
                normalized.validEndAt()
        );
        if (affected == 0) {
            throw new AdminServiceException(
                    "ADMIN_COUPON_STATUS_NOT_EDITABLE",
                    "只有草稿或禁用状态的优惠券可以修改。",
                    HttpStatus.CONFLICT);
        }
        replaceScopes(id, normalized);
        couponTemplateIndexService.syncCouponTemplatesAfterCommit(List.of(HybridIdCodec.toBase62(id)));
        return toResponse(requireTemplate(id), couponScopeMapper.listByTemplateId(id));
    }

    @Transactional
    public AdminCouponTemplateResponse publish(String rawId) {
        byte[] id = couponIdBytes(rawId);
        requireTemplate(id);
        int affected = couponTemplateMapper.publish(id);
        if (affected == 0) {
            throw new AdminServiceException(
                    "ADMIN_COUPON_PUBLISH_CONFLICT",
                    "只有草稿或禁用状态的优惠券可以发布。",
                    HttpStatus.CONFLICT);
        }
        runAfterCommit(() -> couponRedisCacheService.writeCouponToRedis(id));
        couponTemplateIndexService.syncCouponTemplatesAfterCommit(List.of(HybridIdCodec.toBase62(id)));
        return toResponse(requireTemplate(id), couponScopeMapper.listByTemplateId(id));
    }

    @Transactional
    public AdminCouponTemplateResponse disable(String rawId) {
        byte[] id = couponIdBytes(rawId);
        requireTemplate(id);
        int affected = couponTemplateMapper.disable(id);
        if (affected == 0) {
            throw new AdminServiceException(
                    "ADMIN_COUPON_DISABLE_CONFLICT",
                    "只有启用状态的优惠券可以禁用。",
                    HttpStatus.CONFLICT);
        }
        String couponId = HybridIdCodec.toBase62(id);
        runAfterCommit(() -> couponRedisCacheService.markDisabled(couponId));
        couponTemplateIndexService.syncCouponTemplatesAfterCommit(List.of(couponId));
        return toResponse(requireTemplate(id), couponScopeMapper.listByTemplateId(id));
    }

    @Transactional
    public void softDelete(String rawId) {
        byte[] id = couponIdBytes(rawId);
        requireTemplate(id);
        int affected = couponTemplateMapper.softDelete(id);
        if (affected == 0) {
            throw new AdminServiceException(
                    "ADMIN_COUPON_DELETE_CONFLICT",
                    "只有草稿、禁用或过期状态的优惠券可以删除。",
                    HttpStatus.CONFLICT);
        }
        String couponId = HybridIdCodec.toBase62(id);
        runAfterCommit(() -> couponRedisCacheService.deleteCouponRuntime(couponId));
        couponTemplateIndexService.deleteCouponTemplatesAfterCommit(List.of(couponId));
    }

    private AdminCouponTemplatePageResponse searchPage(int page,
                                                       int pageSize,
                                                       String keyword,
                                                       String status,
                                                       OffsetDateTime receiveStartAtFrom,
                                                       OffsetDateTime receiveEndAtTo) {
        CouponTemplateSearchService.SearchResult searchResult;
        try {
            searchResult = couponTemplateSearchService.searchAdminTemplateIds(
                    keyword,
                    status,
                    receiveStartAtFrom,
                    receiveEndAtTo,
                    page,
                    pageSize);
        } catch (CouponTemplateSearchException e) {
            throw new AdminServiceException(
                    "ADMIN_COUPON_ES_SEARCH_FAILED",
                    "Coupon search service is temporarily unavailable. Please try again later.",
                    HttpStatus.BAD_GATEWAY);
        }
        if (searchResult.ids().isEmpty()) {
            return new AdminCouponTemplatePageResponse(page, pageSize, searchResult.total(), List.of());
        }
        List<byte[]> ids = searchResult.ids().stream()
                .map(this::couponIdBytesFromSearch)
                .toList();
        List<Map<String, Object>> rows = couponReadReplicaQueryExecutor.query(
                () -> couponTemplateMapper.listTemplatesByIds(ids));
        Map<String, Map<String, Object>> rowsById = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String id = HybridIdCodec.toBase62FromDatabaseValue(row.get("id"));
            if (!id.isBlank()) {
                rowsById.put(id, row);
            }
        }
        Map<String, String> highlightedNames = searchResult.highlightedNames();
        List<AdminCouponTemplateResponse> records = new ArrayList<>(searchResult.ids().size());
        for (String id : searchResult.ids()) {
            Map<String, Object> row = rowsById.get(id);
            if (row != null) {
                records.add(toResponse(row, List.of(), highlightedNames.get(id)));
            }
        }
        return new AdminCouponTemplatePageResponse(page, pageSize, searchResult.total(), records);
    }

    private void replaceScopes(byte[] templateId, NormalizedCouponRequest request) {
        couponScopeMapper.deleteByTemplateId(templateId);
        if ("ALL".equals(request.scopeType())) {
            return;
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        String templateIdHex = HybridIdCodec.toHex(templateId);
        for (NormalizedScopeTarget target : request.targets()) {
            byte[] scopeId = hybridSemaphoreIdWorker.nextId();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id_hex", HybridIdCodec.toHex(scopeId));
            row.put("coupon_template_id_hex", templateIdHex);
            row.put("scope_target_type", request.scopeType());
            row.put("category_id", target.categoryId());
            row.put("spu_id", target.spuId());
            row.put("sku_id_hex", target.skuIdHex());
            rows.add(row);
        }
        couponScopeMapper.insertScopes(writeJson(rows, "ADMIN_COUPON_SCOPE_JSON_FAILED"));
    }

    private NormalizedCouponRequest normalizeRequest(AdminCouponTemplateRequest request) {
        if (request == null) {
            throw badRequest("ADMIN_COUPON_REQUEST_EMPTY", "优惠券参数不能为空。");
        }
        String couponCode = normalizeRequiredText(request.couponCode(), "ADMIN_COUPON_CODE_EMPTY", "优惠券编码不能为空。");
        String name = normalizeRequiredText(request.name(), "ADMIN_COUPON_NAME_EMPTY", "优惠券名称不能为空。");
        String discountType = normalizeEnum(request.discountType(), SUPPORTED_DISCOUNT_TYPES, "ADMIN_COUPON_DISCOUNT_TYPE_INVALID", "优惠类型只能是 AMOUNT 或 PERCENT。");
        String scopeType = normalizeEnum(request.scopeType(), SUPPORTED_SCOPE_TYPES, "ADMIN_COUPON_SCOPE_TYPE_INVALID", "适用范围只能是 ALL、CATEGORY、SPU 或 SKU。");
        BigDecimal threshold = defaultAmount(request.thresholdAmountYuan());
        if (threshold.signum() < 0) {
            throw badRequest("ADMIN_COUPON_THRESHOLD_INVALID", "最低使用金额不能小于 0。");
        }
        BigDecimal discountAmount = request.discountAmountYuan();
        BigDecimal discountRate = request.discountRate();
        BigDecimal maxDiscountAmount = request.maxDiscountAmountYuan();
        if ("AMOUNT".equals(discountType)) {
            if (discountAmount == null || discountAmount.signum() < 0) {
                throw badRequest("ADMIN_COUPON_DISCOUNT_AMOUNT_INVALID", "满减券减免金额不能小于 0。");
            }
            discountRate = null;
            maxDiscountAmount = null;
        } else {
            if (discountRate == null || discountRate.signum() <= 0 || discountRate.compareTo(BigDecimal.ONE) > 0) {
                throw badRequest("ADMIN_COUPON_DISCOUNT_RATE_INVALID", "折扣比例必须大于 0 且小于等于 1。");
            }
            if (maxDiscountAmount != null && maxDiscountAmount.signum() < 0) {
                throw badRequest("ADMIN_COUPON_MAX_DISCOUNT_INVALID", "最高减免金额不能小于 0。");
            }
            discountAmount = null;
        }
        int totalQuantity = normalizePositiveInt(request.totalQuantity(), "ADMIN_COUPON_TOTAL_QUANTITY_INVALID", "发放总数量必须大于 0。");
        int perUserLimit = normalizePositiveInt(request.perUserLimit(), "ADMIN_COUPON_PER_USER_LIMIT_INVALID", "单用户领取数量必须大于 0。");
        if (perUserLimit != 1) {
            throw badRequest("ADMIN_COUPON_PER_USER_LIMIT_UNSUPPORTED", "当前版本固定一人一券，perUserLimit 必须为 1。");
        }
        OffsetDateTime receiveStartAt = parseRequiredDateTime(request.receiveStartAt(), "ADMIN_COUPON_RECEIVE_START_INVALID", "领取开始时间格式无效。");
        OffsetDateTime receiveEndAt = parseRequiredDateTime(request.receiveEndAt(), "ADMIN_COUPON_RECEIVE_END_INVALID", "领取结束时间格式无效。");
        OffsetDateTime validStartAt = parseRequiredDateTime(request.validStartAt(), "ADMIN_COUPON_VALID_START_INVALID", "有效期开始时间格式无效。");
        OffsetDateTime validEndAt = parseRequiredDateTime(request.validEndAt(), "ADMIN_COUPON_VALID_END_INVALID", "有效期结束时间格式无效。");
        if (!receiveEndAt.isAfter(receiveStartAt) || !validEndAt.isAfter(validStartAt)) {
            throw badRequest("ADMIN_COUPON_TIME_RANGE_INVALID", "结束时间必须晚于开始时间。");
        }
        List<NormalizedScopeTarget> targets = normalizeTargets(scopeType, request.targetIds());
        return new NormalizedCouponRequest(
                couponCode,
                name,
                discountType,
                threshold,
                discountAmount,
                discountRate,
                maxDiscountAmount,
                totalQuantity,
                perUserLimit,
                scopeType,
                targets,
                receiveStartAt,
                receiveEndAt,
                validStartAt,
                validEndAt
        );
    }

    private List<NormalizedScopeTarget> normalizeTargets(String scopeType, List<String> rawTargetIds) {
        if ("ALL".equals(scopeType)) {
            return List.of();
        }
        if (rawTargetIds == null || rawTargetIds.isEmpty()) {
            throw badRequest("ADMIN_COUPON_SCOPE_TARGET_EMPTY", "指定范围优惠券必须选择适用目标。");
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String rawTargetId : rawTargetIds) {
            String value = normalizeText(rawTargetId);
            if (!value.isEmpty()) {
                unique.add(value);
            }
        }
        if (unique.isEmpty()) {
            throw badRequest("ADMIN_COUPON_SCOPE_TARGET_EMPTY", "指定范围优惠券必须选择适用目标。");
        }
        List<NormalizedScopeTarget> targets = new ArrayList<>(unique.size());
        List<Map<String, Object>> validateRows = new ArrayList<>(unique.size());
        for (String value : unique) {
            if ("CATEGORY".equals(scopeType)) {
                Long id = positiveLong(value, "ADMIN_COUPON_CATEGORY_ID_INVALID", "商品分类 ID 无效。");
                targets.add(new NormalizedScopeTarget(id, null, null, value));
                validateRows.add(Map.of("target_id", id));
            } else if ("SPU".equals(scopeType)) {
                Long id = positiveLong(value, "ADMIN_COUPON_SPU_ID_INVALID", "商品 SPU ID 无效。");
                targets.add(new NormalizedScopeTarget(null, id, null, value));
                validateRows.add(Map.of("target_id", id));
            } else {
                byte[] skuIdBytes = couponIdBytes(value);
                String skuIdHex = HybridIdCodec.toHex(skuIdBytes);
                targets.add(new NormalizedScopeTarget(null, null, skuIdHex, value));
                validateRows.add(Map.of("target_id_hex", skuIdHex));
            }
        }
        long matchedCount = couponScopeMapper.countExistingTargets(scopeType, writeJson(validateRows, "ADMIN_COUPON_SCOPE_VALIDATE_JSON_FAILED"));
        if (matchedCount != targets.size()) {
            throw new AdminServiceException(
                    "ADMIN_COUPON_SCOPE_TARGET_NOT_FOUND",
                    "存在不存在或不可用的适用范围目标。",
                    HttpStatus.NOT_FOUND);
        }
        return List.copyOf(targets);
    }

    private AdminCouponTemplateResponse toResponse(Map<String, Object> row, List<Map<String, Object>> scopes) {
        return toResponse(row, scopes, null);
    }

    private AdminCouponTemplateResponse toResponse(Map<String, Object> row,
                                                   List<Map<String, Object>> scopes,
                                                   String highlightedName) {
        String name = highlightedName != null && !highlightedName.isBlank()
                ? highlightedName
                : normalizeText(row.get("name"));
        return new AdminCouponTemplateResponse(
                HybridIdCodec.toBase62FromDatabaseValue(row.get("id")),
                normalizeText(row.get("couponCode")),
                name,
                normalizeText(row.get("discountType")),
                toBigDecimal(row.get("thresholdAmountYuan")),
                toBigDecimal(row.get("discountAmountYuan")),
                toBigDecimal(row.get("discountRate")),
                toBigDecimal(row.get("maxDiscountAmountYuan")),
                toInteger(row.get("totalQuantity")),
                toInteger(row.get("remainingQuantity")),
                toInteger(row.get("perUserLimit")),
                normalizeText(row.get("scopeType")),
                scopeTargetIds(scopes),
                toOffsetDateTime(row.get("receiveStartAt")),
                toOffsetDateTime(row.get("receiveEndAt")),
                toOffsetDateTime(row.get("validStartAt")),
                toOffsetDateTime(row.get("validEndAt")),
                normalizeText(row.get("status")),
                toLong(row.get("version")),
                toOffsetDateTime(row.get("createdAt")),
                toOffsetDateTime(row.get("updatedAt"))
        );
    }

    private AdminCouponClaimResponse toClaimResponse(Map<String, Object> row) {
        return new AdminCouponClaimResponse(
                HybridIdCodec.toBase62FromDatabaseValue(row.get("userCouponId")),
                HybridIdCodec.toBase62FromDatabaseValue(row.get("couponTemplateId")),
                toLong(row.get("userId")),
                normalizeText(row.get("email")),
                normalizeText(row.get("status")),
                toOffsetDateTime(row.get("receivedAt")),
                toOffsetDateTime(row.get("validStartAt")),
                toOffsetDateTime(row.get("validEndAt")),
                normalizeText(row.get("lockedOrderNo")),
                toOffsetDateTime(row.get("lockedAt")),
                normalizeText(row.get("usedOrderNo")),
                toOffsetDateTime(row.get("usedAt"))
        );
    }

    private List<String> scopeTargetIds(List<Map<String, Object>> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return List.of();
        }
        List<String> ids = new ArrayList<>(scopes.size());
        for (Map<String, Object> scope : scopes) {
            String type = normalizeText(scope.get("scopeTargetType"));
            if ("CATEGORY".equals(type)) {
                ids.add(normalizeText(scope.get("categoryId")));
            } else if ("SPU".equals(type)) {
                ids.add(normalizeText(scope.get("spuId")));
            } else if ("SKU".equals(type)) {
                ids.add(HybridIdCodec.toBase62FromDatabaseValue(scope.get("skuId")));
            }
        }
        return ids;
    }

    private Map<String, Object> requireTemplate(byte[] id) {
        Map<String, Object> row = couponTemplateMapper.findById(id);
        if (row == null || row.isEmpty()) {
            throw new AdminServiceException("ADMIN_COUPON_NOT_FOUND", "优惠券不存在。", HttpStatus.NOT_FOUND);
        }
        return row;
    }

    private Map<String, Object> readTemplate(byte[] id) {
        return requireTemplate(id);
    }

    private String normalizeOptionalStatus(String rawStatus) {
        String status = normalizeText(rawStatus);
        if (status.isEmpty()) {
            return "";
        }
        status = status.toUpperCase(Locale.ROOT);
        if (!SUPPORTED_STATUS_FILTERS.contains(status)) {
            throw badRequest("ADMIN_COUPON_STATUS_INVALID", "优惠券状态无效。");
        }
        return status;
    }

    private String normalizeOptionalUserCouponStatus(String rawStatus) {
        String status = normalizeText(rawStatus);
        if (status.isEmpty()) {
            return "";
        }
        status = status.toUpperCase(Locale.ROOT);
        if (!SUPPORTED_USER_COUPON_STATUS_FILTERS.contains(status)) {
            throw badRequest("ADMIN_USER_COUPON_STATUS_INVALID", "用户优惠券状态无效。");
        }
        return status;
    }

    private String normalizeOptionalEmail(String rawEmail) {
        return normalizeText(rawEmail).toLowerCase(Locale.ROOT);
    }

    private OffsetDateTime parseOptionalDateTime(String value, String code, String message) {
        String text = normalizeText(value);
        if (text.isEmpty()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(text);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(text).atZone(ZoneId.systemDefault()).toOffsetDateTime();
            } catch (DateTimeParseException e) {
                throw badRequest(code, message);
            }
        }
    }

    private byte[] couponIdBytes(String rawId) {
        String id = normalizeText(rawId);
        try {
            return HybridIdCodec.fromBase62(id);
        } catch (IllegalArgumentException e) {
            throw badRequest("ADMIN_COUPON_ID_INVALID", "优惠券 ID 无效。");
        }
    }

    private byte[] couponIdBytesFromSearch(String rawId) {
        try {
            return HybridIdCodec.fromBase62(rawId);
        } catch (IllegalArgumentException e) {
            throw new AdminServiceException(
                    "ADMIN_COUPON_ES_RESULT_INVALID",
                    "Coupon search result is invalid. Please rebuild coupon index.",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String normalizeEnum(String rawValue, Set<String> supported, String code, String message) {
        String value = normalizeText(rawValue).toUpperCase(Locale.ROOT);
        if (!supported.contains(value)) {
            throw badRequest(code, message);
        }
        return value;
    }

    private String normalizeRequiredText(String value, String code, String message) {
        String text = normalizeText(value);
        if (text.isEmpty()) {
            throw badRequest(code, message);
        }
        return text;
    }

    private int normalizePositiveInt(Integer value, String code, String message) {
        if (value == null || value <= 0) {
            throw badRequest(code, message);
        }
        return value;
    }

    private Long positiveLong(String value, String code, String message) {
        try {
            long id = Long.parseLong(value);
            if (id <= 0L) {
                throw new NumberFormatException("non-positive");
            }
            return id;
        } catch (NumberFormatException e) {
            throw badRequest(code, message);
        }
    }

    private BigDecimal defaultAmount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private OffsetDateTime parseRequiredDateTime(String value, String code, String message) {
        String text = normalizeText(value);
        if (text.isEmpty()) {
            throw badRequest(code, message);
        }
        try {
            return OffsetDateTime.parse(text);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(text).atZone(ZoneId.systemDefault()).toOffsetDateTime();
            } catch (DateTimeParseException e) {
                throw badRequest(code, message);
            }
        }
    }

    private String writeJson(Object value, String code) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new AdminServiceException(code, "优惠券参数序列化失败。", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        String text = normalizeText(value);
        return text.isEmpty() ? null : new BigDecimal(text);
    }

    private Integer toInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = normalizeText(value);
        return text.isEmpty() ? null : Integer.parseInt(text);
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = normalizeText(value);
        return text.isEmpty() ? null : Long.parseLong(text);
    }

    private boolean toBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(normalizeText(value));
    }

    private OffsetDateTime toOffsetDateTime(Object raw) {
        if (raw instanceof OffsetDateTime time) {
            return time;
        }
        if (raw instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant().atZone(ZoneId.systemDefault()).toOffsetDateTime();
        }
        if (raw instanceof java.util.Date date) {
            return date.toInstant().atZone(ZoneId.systemDefault()).toOffsetDateTime();
        }
        String text = normalizeText(raw);
        if (text.isEmpty()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(text);
        } catch (DateTimeParseException ignored) {
            return Instant.parse(text).atZone(ZoneId.systemDefault()).toOffsetDateTime();
        }
    }

    private String normalizeText(Object raw) {
        return raw == null ? "" : String.valueOf(raw).trim();
    }

    private AdminServiceException badRequest(String code, String message) {
        return new AdminServiceException(code, message, HttpStatus.BAD_REQUEST);
    }

    private void runAfterCommit(Runnable runnable) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            runnable.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                runnable.run();
            }
        });
    }

    private record NormalizedCouponRequest(String couponCode,
                                           String name,
                                           String discountType,
                                           BigDecimal thresholdAmountYuan,
                                           BigDecimal discountAmountYuan,
                                           BigDecimal discountRate,
                                           BigDecimal maxDiscountAmountYuan,
                                           Integer totalQuantity,
                                           Integer perUserLimit,
                                           String scopeType,
                                           List<NormalizedScopeTarget> targets,
                                           OffsetDateTime receiveStartAt,
                                           OffsetDateTime receiveEndAt,
                                           OffsetDateTime validStartAt,
                                           OffsetDateTime validEndAt) {
    }

    private record NormalizedScopeTarget(Long categoryId,
                                         Long spuId,
                                         String skuIdHex,
                                         String displayId) {
    }
}
