package com.example.ShoppingSystem.order.service;

import com.example.ShoppingSystem.Utils.HybridIdCodec;
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

@Service
public class OrderCouponService {

    private final UserCouponMapper userCouponMapper;
    private final ObjectMapper objectMapper;

    public OrderCouponService(UserCouponMapper userCouponMapper,
                              ObjectMapper objectMapper) {
        this.userCouponMapper = userCouponMapper;
        this.objectMapper = objectMapper;
    }

    public CouponOptions couponOptions(Long userId,
                                       OrderSkuSnapshot sku,
                                       BigDecimal orderAmount,
                                       String selectedUserCouponId,
                                       OffsetDateTime now) {
        List<Map<String, Object>> rows = userCouponMapper.listOrderCandidateCoupons(
                userId,
                sku.skuId(),
                sku.spuId(),
                sku.categoryId(),
                now
        );
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
                OrderRowMapper.text(row, "discountType"),
                OrderRowMapper.nullableDecimal(row, "discountAmountYuan"),
                OrderRowMapper.nullableDecimal(row, "discountRate"),
                OrderRowMapper.nullableDecimal(row, "maxDiscountAmountYuan")
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

    public record CouponOptions(List<OrderCouponOptionResponse> availableCoupons,
                                List<OrderCouponOptionResponse> unavailableCoupons,
                                String selectedUserCouponId,
                                BigDecimal selectedDiscountAmountYuan) {
    }
}
