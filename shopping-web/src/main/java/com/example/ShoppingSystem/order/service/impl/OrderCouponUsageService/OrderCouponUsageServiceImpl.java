package com.example.ShoppingSystem.order.service.impl.OrderCouponUsageService;

import com.example.ShoppingSystem.Utils.HybridIdCodec;
import com.example.ShoppingSystem.Utils.HybridSemaphoreIdWorker;
import com.example.ShoppingSystem.mapper.coupon.CouponUsageRecordMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.ShoppingSystem.order.service.OrderCouponUsageService;
import com.example.ShoppingSystem.order.service.LockedOrderCoupon;
import com.example.ShoppingSystem.order.service.OrderRowMapper;
import com.example.ShoppingSystem.order.service.OrderServiceException;
@Service
public class OrderCouponUsageServiceImpl implements OrderCouponUsageService {

    private final CouponUsageRecordMapper couponUsageRecordMapper;
    private final HybridSemaphoreIdWorker hybridSemaphoreIdWorker;
    private final ObjectMapper objectMapper;

    public OrderCouponUsageServiceImpl(CouponUsageRecordMapper couponUsageRecordMapper,
                                   HybridSemaphoreIdWorker hybridSemaphoreIdWorker,
                                   ObjectMapper objectMapper) {
        this.couponUsageRecordMapper = couponUsageRecordMapper;
        this.hybridSemaphoreIdWorker = hybridSemaphoreIdWorker;
        this.objectMapper = objectMapper;
    }

    public void writeLock(Long userId,
                          LockedOrderCoupon coupon,
                          BigDecimal orderAmountYuan,
                          BigDecimal discountAmountYuan,
                          String orderNo) {
        if (coupon == null) {
            return;
        }
        couponUsageRecordMapper.insertUsageRecordIgnore(
                hybridSemaphoreIdWorker.nextId(),
                coupon.userCouponId(),
                coupon.couponTemplateId(),
                userId,
                orderNo,
                "LOCK",
                orderAmountYuan,
                discountAmountYuan,
                "ORDER_COUPON_LOCK:" + orderNo
        );
    }

    public void writeReleases(List<Map<String, Object>> releasedCoupons) {
        if (releasedCoupons == null || releasedCoupons.isEmpty()) {
            return;
        }
        List<Map<String, Object>> usageRecords = releasedCoupons.stream()
                .map(this::releaseUsageRecordRow)
                .toList();
        couponUsageRecordMapper.batchInsertUsageRecordsIgnore(toJson(usageRecords));
    }

    public void writeRelease(Long userId,
                             LockedOrderCoupon coupon,
                             String orderNo) {
        if (coupon == null) {
            return;
        }
        couponUsageRecordMapper.insertUsageRecordIgnore(
                hybridSemaphoreIdWorker.nextId(),
                coupon.userCouponId(),
                coupon.couponTemplateId(),
                userId,
                orderNo,
                "RELEASE",
                BigDecimal.ZERO.setScale(2),
                BigDecimal.ZERO.setScale(2),
                "ORDER_COUPON_RELEASE:" + orderNo
        );
    }

    public void writeUse(Long userId,
                         LockedOrderCoupon coupon,
                         BigDecimal orderAmountYuan,
                         BigDecimal discountAmountYuan,
                         String orderNo) {
        if (coupon == null) {
            return;
        }
        couponUsageRecordMapper.insertUsageRecordIgnore(
                hybridSemaphoreIdWorker.nextId(),
                coupon.userCouponId(),
                coupon.couponTemplateId(),
                userId,
                orderNo,
                "USE",
                orderAmountYuan,
                discountAmountYuan,
                "ORDER_COUPON_USE:" + orderNo
        );
    }

    private Map<String, Object> releaseUsageRecordRow(Map<String, Object> row) {
        String orderNo = OrderRowMapper.text(row, "orderNo");
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("id_hex", HybridIdCodec.toHex(hybridSemaphoreIdWorker.nextId()));
        record.put("user_coupon_id_hex", OrderRowMapper.text(row, "userCouponId"));
        record.put("coupon_template_id_hex", OrderRowMapper.text(row, "couponTemplateId"));
        record.put("user_id", OrderRowMapper.longValue(row, "userId"));
        record.put("order_no", orderNo);
        record.put("action", "RELEASE");
        record.put("order_amount_yuan", BigDecimal.ZERO.setScale(2));
        record.put("discount_amount_yuan", BigDecimal.ZERO.setScale(2));
        record.put("idempotency_key", "ORDER_COUPON_RELEASE:" + orderNo);
        return record;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new OrderServiceException("ORDER_COUPON_USAGE_BATCH_JSON_INVALID", "Order coupon usage batch json is invalid.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
