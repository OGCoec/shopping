package com.example.ShoppingSystem.order.service.impl.OrderCouponService;

import com.example.ShoppingSystem.Utils.HybridIdCodec;
import com.example.ShoppingSystem.config.datasource.CouponReadReplicaQueryExecutor;
import com.example.ShoppingSystem.mapper.coupon.CouponScopeMapper;
import com.example.ShoppingSystem.mapper.coupon.CouponTemplateMapper;
import com.example.ShoppingSystem.mapper.coupon.UserCouponMapper;
import com.example.ShoppingSystem.order.dto.OrderCouponOptionResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.example.ShoppingSystem.order.service.OrderCouponService;
import com.example.ShoppingSystem.order.service.LockedOrderCoupon;
import com.example.ShoppingSystem.order.service.OrderAmountCalculator;
import com.example.ShoppingSystem.order.service.OrderCouponSnapshot;
import com.example.ShoppingSystem.order.service.OrderRedisSnapshot;
import com.example.ShoppingSystem.order.service.OrderRowMapper;
import com.example.ShoppingSystem.order.service.OrderServiceException;
import com.example.ShoppingSystem.order.service.OrderSkuSnapshot;
@Service
public class OrderCouponServiceImpl implements OrderCouponService {

    private final UserCouponMapper userCouponMapper;
    private final CouponTemplateMapper couponTemplateMapper;
    private final CouponScopeMapper couponScopeMapper;
    private final CouponReadReplicaQueryExecutor couponReadReplicaQueryExecutor;
    private final ObjectMapper objectMapper;

    public OrderCouponServiceImpl(UserCouponMapper userCouponMapper,
                              CouponTemplateMapper couponTemplateMapper,
                              CouponScopeMapper couponScopeMapper,
                              CouponReadReplicaQueryExecutor couponReadReplicaQueryExecutor,
                              ObjectMapper objectMapper) {
        this.userCouponMapper = userCouponMapper;
        this.couponTemplateMapper = couponTemplateMapper;
        this.couponScopeMapper = couponScopeMapper;
        this.couponReadReplicaQueryExecutor = couponReadReplicaQueryExecutor;
        this.objectMapper = objectMapper;
    }

    public CouponOptions couponOptions(Long userId,
                                       OrderSkuSnapshot sku,
                                       BigDecimal orderAmount,
                                       String selectedUserCouponId,
                                       OffsetDateTime now) {
        List<Map<String, Object>> rows = mergeCouponTemplateFields(
                userCouponMapper.listOrderCandidateCoupons(
                userId,
                sku.skuId(),
                sku.spuId(),
                sku.categoryId(),
                now
        ), sku);
        byte[] selectedBytes = parseOptionalCouponId(selectedUserCouponId);
        String selectedText = selectedBytes == null ? "" : HybridIdCodec.toBase62(selectedBytes);
        List<OrderCouponOptionResponse> available = new ArrayList<>();
        List<OrderCouponOptionResponse> unavailable = new ArrayList<>();
        BigDecimal selectedDiscount = BigDecimal.ZERO;
        String selectedCouponId = null;

        for (Map<String, Object> row : rows) {
            OrderCouponSnapshot coupon = toCouponSnapshot(row);
            String reason = unavailableReason(coupon, orderAmount, now);
            boolean selected = selectedText.equals(coupon.userCouponIdText());
            BigDecimal discount = reason == null ? OrderAmountCalculator.discount(orderAmount, coupon) : BigDecimal.ZERO;
            OrderCouponOptionResponse option = new OrderCouponOptionResponse(
                    coupon.userCouponIdText(),
                    coupon.couponTemplateIdText(),
                    coupon.name(),
                    discount,
                    selected,
                    reason
            );
            if (reason == null) {
                available.add(option);
            } else {
                unavailable.add(option);
            }
            if (selected && reason == null) {
                selectedDiscount = discount;
                selectedCouponId = coupon.userCouponIdText();
            }
        }

        return new CouponOptions(
                List.copyOf(available),
                List.copyOf(unavailable),
                selectedCouponId,
                OrderAmountCalculator.money(selectedDiscount)
        );
    }

    public LockedOrderCoupon lockCoupon(Long userId,
                                        OrderSkuSnapshot sku,
                                        BigDecimal orderAmount,
                                        String rawUserCouponId,
                                        String orderNo,
                                        OffsetDateTime now) {
        byte[] userCouponId = parseOptionalCouponId(rawUserCouponId);
        if (userCouponId == null) {
            return null;
        }
        Map<String, Object> candidateRow = userCouponMapper.findOrderCandidateCoupon(
                userId,
                userCouponId,
                sku.skuId(),
                sku.spuId(),
                sku.categoryId(),
                now
        );
        if (candidateRow == null || candidateRow.isEmpty()) {
            throw new OrderServiceException("ORDER_COUPON_UNAVAILABLE", "Coupon is unavailable.", HttpStatus.CONFLICT);
        }
        OrderCouponSnapshot candidate = toCouponSnapshot(mergeCouponTemplateFields(List.of(candidateRow), sku).get(0));
        if (unavailableReason(candidate, orderAmount, now) != null) {
            throw new OrderServiceException("ORDER_COUPON_UNAVAILABLE", "Coupon is unavailable.", HttpStatus.CONFLICT);
        }
        Map<String, Object> row = userCouponMapper.lockCouponForOrder(
                userId,
                userCouponId,
                orderNo,
                sku.skuId(),
                sku.spuId(),
                sku.categoryId(),
                orderAmount,
                now
        );
        if (row == null || row.isEmpty()) {
            throw new OrderServiceException("ORDER_COUPON_UNAVAILABLE", "Coupon is unavailable.", HttpStatus.CONFLICT);
        }
        return new LockedOrderCoupon(
                OrderRowMapper.idBytes(row, "userCouponId"),
                OrderRowMapper.idText(row, "userCouponId"),
                OrderRowMapper.idBytes(row, "couponTemplateId"),
                OrderRowMapper.idText(row, "couponTemplateId"),
                candidate.discountType(),
                candidate.discountAmountYuan(),
                candidate.discountRate(),
                candidate.maxDiscountAmountYuan()
        );
    }

    public LockedOrderCoupon releaseLockedCoupon(String orderNo, OffsetDateTime now) {
        Map<String, Object> row = userCouponMapper.releaseLockedCouponByOrderNo(orderNo, now);
        if (row == null || row.isEmpty()) {
            return null;
        }
        return new LockedOrderCoupon(
                OrderRowMapper.idBytes(row, "userCouponId"),
                OrderRowMapper.idText(row, "userCouponId"),
                OrderRowMapper.idBytes(row, "couponTemplateId"),
                OrderRowMapper.idText(row, "couponTemplateId"),
                "",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
    }

    public List<Map<String, Object>> releaseLockedCoupons(List<OrderRedisSnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> orders = snapshots.stream()
                .map(this::lockedCouponOrderRow)
                .filter(row -> row != null)
                .toList();
        if (orders.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> released = userCouponMapper.releaseLockedCouponsByOrderNos(toJson(orders));
        return released == null ? List.of() : released;
    }

    public LockedOrderCoupon useLockedCoupon(String orderNo, OffsetDateTime now) {
        Map<String, Object> row = userCouponMapper.useLockedCouponByOrderNo(orderNo, now);
        if (row == null || row.isEmpty()) {
            return null;
        }
        return new LockedOrderCoupon(
                OrderRowMapper.idBytes(row, "userCouponId"),
                OrderRowMapper.idText(row, "userCouponId"),
                OrderRowMapper.idBytes(row, "couponTemplateId"),
                OrderRowMapper.idText(row, "couponTemplateId"),
                "",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
    }

    private List<Map<String, Object>> mergeCouponTemplateFields(List<Map<String, Object>> userCouponRows,
                                                                OrderSkuSnapshot sku) {
        if (userCouponRows == null || userCouponRows.isEmpty()) {
            return List.of();
        }
        List<byte[]> couponTemplateIds = userCouponRows.stream()
                .map(row -> OrderRowMapper.idBytes(row, "couponTemplateId"))
                .toList();
        CouponTemplateRows templateRows = couponReadReplicaQueryExecutor.query(() -> new CouponTemplateRows(
                couponTemplateMapper.listTemplatesByIds(couponTemplateIds),
                couponScopeMapper.listByTemplateIds(couponTemplateIds)
        ));
        Map<String, Map<String, Object>> templatesById = new LinkedHashMap<>();
        for (Map<String, Object> template : templateRows.templates()) {
            String templateId = HybridIdCodec.toBase62FromDatabaseValue(template.get("id"));
            if (!templateId.isBlank()) {
                templatesById.put(templateId, template);
            }
        }
        Map<String, List<Map<String, Object>>> scopesByTemplateId = templateRows.scopes().stream()
                .collect(Collectors.groupingBy(
                        row -> HybridIdCodec.toBase62FromDatabaseValue(row.get("couponTemplateId")),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        List<Map<String, Object>> mergedRows = new ArrayList<>(userCouponRows.size());
        for (Map<String, Object> row : userCouponRows) {
            Map<String, Object> merged = new LinkedHashMap<>(row);
            String couponTemplateId = OrderRowMapper.idText(row, "couponTemplateId");
            Map<String, Object> template = templatesById.get(couponTemplateId);
            if (template != null) {
                merged.put("name", template.get("name"));
                merged.put("discountType", template.get("discountType"));
                merged.put("thresholdAmountYuan", template.get("thresholdAmountYuan"));
                merged.put("discountAmountYuan", template.get("discountAmountYuan"));
                merged.put("discountRate", template.get("discountRate"));
                merged.put("maxDiscountAmountYuan", template.get("maxDiscountAmountYuan"));
                merged.put("scopeType", template.get("scopeType"));
                merged.put("templateStatus", template.get("status"));
                merged.put("templateValidStartAt", template.get("validStartAt"));
                merged.put("templateValidEndAt", template.get("validEndAt"));
                merged.put("scopeMatched", scopeMatched(template, scopesByTemplateId.get(couponTemplateId), sku));
            } else {
                merged.put("scopeMatched", false);
            }
            mergedRows.add(merged);
        }
        return mergedRows;
    }

    private boolean scopeMatched(Map<String, Object> template,
                                 List<Map<String, Object>> scopes,
                                 OrderSkuSnapshot sku) {
        String scopeType = text(template.get("scopeType"));
        if ("ALL".equals(scopeType)) {
            return true;
        }
        if (scopes == null || scopes.isEmpty()) {
            return false;
        }
        for (Map<String, Object> scope : scopes) {
            String targetType = text(scope.get("scopeTargetType"));
            if ("CATEGORY".equals(scopeType) && "CATEGORY".equals(targetType)
                    && String.valueOf(sku.categoryId()).equals(text(scope.get("categoryId")))) {
                return true;
            }
            if ("SPU".equals(scopeType) && "SPU".equals(targetType)
                    && String.valueOf(sku.spuId()).equals(text(scope.get("spuId")))) {
                return true;
            }
            if ("SKU".equals(scopeType) && "SKU".equals(targetType)
                    && sku.skuIdText().equals(HybridIdCodec.toBase62FromDatabaseValue(scope.get("skuId")))) {
                return true;
            }
        }
        return false;
    }

    private OrderCouponSnapshot toCouponSnapshot(Map<String, Object> row) {
        return new OrderCouponSnapshot(
                OrderRowMapper.idBytes(row, "userCouponId"),
                OrderRowMapper.idText(row, "userCouponId"),
                OrderRowMapper.idBytes(row, "couponTemplateId"),
                OrderRowMapper.idText(row, "couponTemplateId"),
                OrderRowMapper.text(row, "name"),
                OrderRowMapper.text(row, "discountType"),
                OrderRowMapper.decimal(row, "thresholdAmountYuan"),
                OrderRowMapper.nullableDecimal(row, "discountAmountYuan"),
                OrderRowMapper.nullableDecimal(row, "discountRate"),
                OrderRowMapper.nullableDecimal(row, "maxDiscountAmountYuan"),
                OrderRowMapper.boolValue(row, "scopeMatched"),
                OrderRowMapper.text(row, "userCouponStatus"),
                OrderRowMapper.text(row, "templateStatus"),
                OrderRowMapper.offsetDateTime(row, "validStartAt"),
                OrderRowMapper.offsetDateTime(row, "validEndAt"),
                OrderRowMapper.offsetDateTime(row, "templateValidStartAt"),
                OrderRowMapper.offsetDateTime(row, "templateValidEndAt")
        );
    }

    private String unavailableReason(OrderCouponSnapshot coupon, BigDecimal orderAmount, OffsetDateTime now) {
        if (!"UNUSED".equals(coupon.userCouponStatus())) {
            return "COUPON_NOT_UNUSED";
        }
        if (!"ACTIVE".equals(coupon.templateStatus())) {
            return "COUPON_NOT_ACTIVE";
        }
        if (coupon.validStartAt() != null && coupon.validStartAt().isAfter(now)) {
            return "COUPON_NOT_STARTED";
        }
        if (coupon.validEndAt() != null && !coupon.validEndAt().isAfter(now)) {
            return "COUPON_EXPIRED";
        }
        if (coupon.templateValidStartAt() != null && coupon.templateValidStartAt().isAfter(now)) {
            return "COUPON_TEMPLATE_NOT_STARTED";
        }
        if (coupon.templateValidEndAt() != null && !coupon.templateValidEndAt().isAfter(now)) {
            return "COUPON_TEMPLATE_EXPIRED";
        }
        if (!coupon.scopeMatched()) {
            return "COUPON_SCOPE_NOT_MATCHED";
        }
        if (orderAmount.compareTo(coupon.thresholdAmountYuan()) < 0) {
            return "ORDER_AMOUNT_NOT_ENOUGH";
        }
        return null;
    }

    private byte[] parseOptionalCouponId(String rawUserCouponId) {
        String value = rawUserCouponId == null ? "" : rawUserCouponId.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (!value.matches(HybridIdCodec.BASE62_PATTERN)) {
            throw new OrderServiceException("ORDER_COUPON_INVALID", "Coupon id is invalid.", HttpStatus.BAD_REQUEST);
        }
        try {
            return HybridIdCodec.fromBase62(value);
        } catch (IllegalArgumentException e) {
            throw new OrderServiceException("ORDER_COUPON_INVALID", "Coupon id is invalid.", HttpStatus.BAD_REQUEST);
        }
    }

    private Map<String, Object> lockedCouponOrderRow(OrderRedisSnapshot snapshot) {
        if (snapshot == null || !hasUserCoupon(snapshot.order())) {
            return null;
        }
        String orderNo = OrderRowMapper.text(snapshot.order(), "orderNo");
        if (orderNo.isBlank()) {
            return null;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("order_no", orderNo);
        row.put("user_id", OrderRowMapper.longValue(snapshot.order(), "userId"));
        return row;
    }

    private boolean hasUserCoupon(Map<String, Object> order) {
        return !OrderRowMapper.idText(order, "userCouponId").isBlank()
                || !OrderRowMapper.text(order, "userCouponIdHex").isBlank();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new OrderServiceException("ORDER_COUPON_BATCH_JSON_INVALID", "Order coupon batch json is invalid.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private record CouponTemplateRows(List<Map<String, Object>> templates,
                                      List<Map<String, Object>> scopes) {
    }
}
