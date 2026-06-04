package com.example.ShoppingSystem.coupon.service;

import com.example.ShoppingSystem.Utils.HybridIdCodec;
import com.example.ShoppingSystem.coupon.dto.UserCouponMineCardResponse;
import com.example.ShoppingSystem.coupon.dto.UserCouponMineDetailResponse;
import com.example.ShoppingSystem.coupon.dto.UserCouponMinePageResponse;
import com.example.ShoppingSystem.coupon.dto.UserCouponTemplateCardResponse;
import com.example.ShoppingSystem.coupon.dto.UserCouponTemplateDetailResponse;
import com.example.ShoppingSystem.coupon.dto.UserCouponTemplatePageResponse;
import com.example.ShoppingSystem.config.datasource.CouponReadReplicaQueryExecutor;
import com.example.ShoppingSystem.mapper.coupon.CouponScopeMapper;
import com.example.ShoppingSystem.mapper.coupon.CouponTemplateMapper;
import com.example.ShoppingSystem.mapper.coupon.UserCouponMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
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
public class UserCouponQueryService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_NAME_LENGTH = 128;
    private static final Set<String> SUPPORTED_USER_COUPON_STATUS_FILTERS = Set.of("UNUSED", "LOCKED", "USED", "EXPIRED", "REVOKED");

    private final CouponTemplateMapper couponTemplateMapper;
    private final UserCouponMapper userCouponMapper;
    private final CouponScopeMapper couponScopeMapper;
    private final CouponTemplateSearchService couponTemplateSearchService;
    private final CouponReadReplicaQueryExecutor couponReadReplicaQueryExecutor;

    public UserCouponQueryService(CouponTemplateMapper couponTemplateMapper,
                                  UserCouponMapper userCouponMapper,
                                  CouponScopeMapper couponScopeMapper,
                                  CouponTemplateSearchService couponTemplateSearchService,
                                  CouponReadReplicaQueryExecutor couponReadReplicaQueryExecutor) {
        this.couponTemplateMapper = couponTemplateMapper;
        this.userCouponMapper = userCouponMapper;
        this.couponScopeMapper = couponScopeMapper;
        this.couponTemplateSearchService = couponTemplateSearchService;
        this.couponReadReplicaQueryExecutor = couponReadReplicaQueryExecutor;
    }

    public UserCouponTemplatePageResponse receivablePage(Long userId,
                                                         Integer rawPage,
                                                         Integer rawPageSize,
                                                         String rawName) {
        int page = normalizePage(rawPage);
        int pageSize = normalizePageSize(rawPageSize);
        OffsetDateTime now = OffsetDateTime.now();
        String name = normalizeSearchName(rawName);
        if (!name.isBlank()) {
            return receivableSearchPage(userId, page, pageSize, name, now);
        }
        return couponReadReplicaQueryExecutor.query(() -> {
            int offset = (page - 1) * pageSize;
            long total = couponTemplateMapper.countUserReceivableTemplates(now);
            List<UserCouponTemplateCardResponse> records = couponTemplateMapper
                    .listUserReceivableTemplates(userId, now, offset, pageSize)
                    .stream()
                    .map(row -> toTemplateCard(row, null))
                    .toList();
            return new UserCouponTemplatePageResponse(page, pageSize, total, records);
        });
    }

    public UserCouponTemplateDetailResponse receivableDetail(Long userId, String rawCouponTemplateId) {
        byte[] couponTemplateId = couponIdBytes(rawCouponTemplateId);
        OffsetDateTime now = OffsetDateTime.now();
        return couponReadReplicaQueryExecutor.query(() -> {
            Map<String, Object> row = couponTemplateMapper.findUserReceivableTemplateById(couponTemplateId, userId, now);
            if (row == null || row.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "COUPON_NOT_FOUND");
            }
            List<Map<String, Object>> scopes = couponScopeMapper.listByTemplateId(couponTemplateId);
            return toTemplateDetail(row, scopes);
        });
    }

    public UserCouponMinePageResponse minePage(Long userId,
                                               Integer rawPage,
                                               Integer rawPageSize,
                                               String rawStatus) {
        int page = normalizePage(rawPage);
        int pageSize = normalizePageSize(rawPageSize);
        int offset = (page - 1) * pageSize;
        String status = normalizeOptionalUserCouponStatus(rawStatus);
        return couponReadReplicaQueryExecutor.query(() -> {
            long total = userCouponMapper.countMine(userId, status);
            List<UserCouponMineCardResponse> records = userCouponMapper
                    .listMine(userId, status, offset, pageSize)
                    .stream()
                    .map(this::toMineCard)
                    .toList();
            return new UserCouponMinePageResponse(page, pageSize, total, records);
        });
    }

    public UserCouponMineDetailResponse mineDetail(Long userId, String rawUserCouponId) {
        byte[] userCouponId = couponIdBytes(rawUserCouponId);
        return couponReadReplicaQueryExecutor.query(() -> {
            Map<String, Object> row = userCouponMapper.findMineById(userId, userCouponId);
            if (row == null || row.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "COUPON_NOT_FOUND");
            }
            byte[] couponTemplateId = couponIdBytesFromDatabaseValue(row.get("couponTemplateId"));
            List<Map<String, Object>> scopes = couponScopeMapper.listByTemplateId(couponTemplateId);
            return toMineDetail(row, scopes);
        });
    }

    private UserCouponTemplatePageResponse receivableSearchPage(Long userId,
                                                                int page,
                                                                int pageSize,
                                                                String name,
                                                                OffsetDateTime now) {
        CouponTemplateSearchService.SearchResult searchResult;
        try {
            searchResult = couponTemplateSearchService.searchActiveReceivableTemplateIds(name, now, page, pageSize);
        } catch (CouponTemplateSearchException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "COUPON_SEARCH_UNAVAILABLE");
        }
        if (searchResult.ids().isEmpty()) {
            return new UserCouponTemplatePageResponse(page, pageSize, searchResult.total(), List.of());
        }
        List<byte[]> ids = searchResult.ids().stream()
                .map(this::couponIdBytesFromSearch)
                .toList();
        List<Map<String, Object>> rows = couponReadReplicaQueryExecutor.query(
                () -> couponTemplateMapper.listUserReceivableTemplatesByIds(ids, userId, now));
        Map<String, Map<String, Object>> rowsById = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String id = HybridIdCodec.toBase62FromDatabaseValue(row.get("id"));
            if (!id.isBlank()) {
                rowsById.put(id, row);
            }
        }
        List<UserCouponTemplateCardResponse> records = new ArrayList<>(searchResult.ids().size());
        for (String id : searchResult.ids()) {
            Map<String, Object> row = rowsById.get(id);
            if (row != null) {
                records.add(toTemplateCard(row, searchResult.highlightedNames().get(id)));
            }
        }
        return new UserCouponTemplatePageResponse(page, pageSize, searchResult.total(), records);
    }

    private UserCouponTemplateCardResponse toTemplateCard(Map<String, Object> row, String highlightedName) {
        boolean claimed = toBoolean(row.get("claimed"));
        Integer remainingQuantity = toInteger(row.get("remainingQuantity"));
        return new UserCouponTemplateCardResponse(
                HybridIdCodec.toBase62FromDatabaseValue(row.get("id")),
                text(row.get("couponCode")),
                highlightedName != null && !highlightedName.isBlank() ? highlightedName : text(row.get("name")),
                text(row.get("discountType")),
                toBigDecimal(row.get("thresholdAmountYuan")),
                toBigDecimal(row.get("discountAmountYuan")),
                toBigDecimal(row.get("discountRate")),
                toBigDecimal(row.get("maxDiscountAmountYuan")),
                toInteger(row.get("totalQuantity")),
                remainingQuantity,
                toInteger(row.get("perUserLimit")),
                toOffsetDateTime(row.get("receiveStartAt")),
                toOffsetDateTime(row.get("receiveEndAt")),
                toOffsetDateTime(row.get("validStartAt")),
                toOffsetDateTime(row.get("validEndAt")),
                claimed,
                !claimed && remainingQuantity != null && remainingQuantity > 0,
                HybridIdCodec.toBase62FromDatabaseValue(row.get("userCouponId")),
                text(row.get("userCouponStatus"))
        );
    }

    private UserCouponTemplateDetailResponse toTemplateDetail(Map<String, Object> row, List<Map<String, Object>> scopes) {
        UserCouponTemplateCardResponse card = toTemplateCard(row, null);
        return new UserCouponTemplateDetailResponse(
                card.couponTemplateId(),
                card.couponCode(),
                card.name(),
                card.discountType(),
                card.thresholdAmountYuan(),
                card.discountAmountYuan(),
                card.discountRate(),
                card.maxDiscountAmountYuan(),
                card.totalQuantity(),
                card.remainingQuantity(),
                card.perUserLimit(),
                text(row.get("scopeType")),
                scopeTargetIds(scopes),
                card.receiveStartAt(),
                card.receiveEndAt(),
                card.validStartAt(),
                card.validEndAt(),
                card.claimed(),
                card.canClaim(),
                card.userCouponId(),
                card.userCouponStatus()
        );
    }

    private UserCouponMineCardResponse toMineCard(Map<String, Object> row) {
        return new UserCouponMineCardResponse(
                HybridIdCodec.toBase62FromDatabaseValue(row.get("userCouponId")),
                HybridIdCodec.toBase62FromDatabaseValue(row.get("couponTemplateId")),
                text(row.get("couponCode")),
                text(row.get("name")),
                text(row.get("discountType")),
                toBigDecimal(row.get("thresholdAmountYuan")),
                toBigDecimal(row.get("discountAmountYuan")),
                toBigDecimal(row.get("discountRate")),
                toBigDecimal(row.get("maxDiscountAmountYuan")),
                text(row.get("status")),
                toOffsetDateTime(row.get("receivedAt")),
                toOffsetDateTime(row.get("validStartAt")),
                toOffsetDateTime(row.get("validEndAt")),
                toOffsetDateTime(row.get("usedAt"))
        );
    }

    private UserCouponMineDetailResponse toMineDetail(Map<String, Object> row, List<Map<String, Object>> scopes) {
        return new UserCouponMineDetailResponse(
                HybridIdCodec.toBase62FromDatabaseValue(row.get("userCouponId")),
                HybridIdCodec.toBase62FromDatabaseValue(row.get("couponTemplateId")),
                text(row.get("couponCode")),
                text(row.get("name")),
                text(row.get("discountType")),
                toBigDecimal(row.get("thresholdAmountYuan")),
                toBigDecimal(row.get("discountAmountYuan")),
                toBigDecimal(row.get("discountRate")),
                toBigDecimal(row.get("maxDiscountAmountYuan")),
                toInteger(row.get("totalQuantity")),
                toInteger(row.get("remainingQuantity")),
                toInteger(row.get("perUserLimit")),
                text(row.get("scopeType")),
                scopeTargetIds(scopes),
                text(row.get("templateStatus")),
                text(row.get("status")),
                toOffsetDateTime(row.get("receivedAt")),
                toOffsetDateTime(row.get("validStartAt")),
                toOffsetDateTime(row.get("validEndAt")),
                text(row.get("lockedOrderNo")),
                toOffsetDateTime(row.get("lockedAt")),
                text(row.get("usedOrderNo")),
                toOffsetDateTime(row.get("usedAt"))
        );
    }

    private List<String> scopeTargetIds(List<Map<String, Object>> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (Map<String, Object> scope : scopes) {
            String type = text(scope.get("scopeTargetType"));
            if ("CATEGORY".equals(type)) {
                String id = text(scope.get("categoryId"));
                if (!id.isBlank()) {
                    ids.add(id);
                }
            } else if ("SPU".equals(type)) {
                String id = text(scope.get("spuId"));
                if (!id.isBlank()) {
                    ids.add(id);
                }
            } else if ("SKU".equals(type)) {
                String id = HybridIdCodec.toBase62FromDatabaseValue(scope.get("skuId"));
                if (!id.isBlank()) {
                    ids.add(id);
                }
            }
        }
        return List.copyOf(ids);
    }

    private int normalizePage(Integer page) {
        return page == null || page <= 0 ? DEFAULT_PAGE : page;
    }

    private int normalizePageSize(Integer pageSize) {
        return pageSize == null || pageSize <= 0 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private String normalizeSearchName(String value) {
        String text = text(value);
        return text.length() <= MAX_NAME_LENGTH ? text : text.substring(0, MAX_NAME_LENGTH);
    }

    private String normalizeOptionalUserCouponStatus(String rawStatus) {
        String status = text(rawStatus);
        if (status.isBlank()) {
            return "";
        }
        status = status.toUpperCase(Locale.ROOT);
        if (!SUPPORTED_USER_COUPON_STATUS_FILTERS.contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "COUPON_STATUS_INVALID");
        }
        return status;
    }

    private byte[] couponIdBytes(String rawId) {
        try {
            return HybridIdCodec.fromBase62(text(rawId));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "COUPON_ID_INVALID");
        }
    }

    private byte[] couponIdBytesFromSearch(String rawId) {
        try {
            return HybridIdCodec.fromBase62(rawId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "COUPON_SEARCH_RESULT_INVALID");
        }
    }

    private byte[] couponIdBytesFromDatabaseValue(Object raw) {
        try {
            return HybridIdCodec.fromHex(text(raw));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "COUPON_DATA_INVALID");
        }
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        String text = text(value);
        return text.isBlank() ? null : new BigDecimal(text);
    }

    private Integer toInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = text(value);
        return text.isBlank() ? null : Integer.parseInt(text);
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = text(value);
        return text.isBlank() ? null : Long.parseLong(text);
    }

    private boolean toBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(text(value));
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
        String text = text(raw);
        if (text.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(text);
        } catch (DateTimeParseException ignored) {
            return Instant.parse(text).atZone(ZoneId.systemDefault()).toOffsetDateTime();
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
