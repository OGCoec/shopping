package com.example.ShoppingSystem.order.service;

import com.example.ShoppingSystem.Utils.HybridIdCodec;
import com.example.ShoppingSystem.mapper.coupon.UserCouponMapper;
import com.example.ShoppingSystem.order.dto.OrderCouponOptionResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public interface OrderCouponService {
    public record CouponOptions(List<OrderCouponOptionResponse> availableCoupons,
                                    List<OrderCouponOptionResponse> unavailableCoupons,
                                    String selectedUserCouponId,
                                    BigDecimal selectedDiscountAmountYuan) {
        }

    public CouponOptions couponOptions(Long userId,
                                       OrderSkuSnapshot sku,
                                       BigDecimal orderAmount,
                                       String selectedUserCouponId,
                                       OffsetDateTime now);

    public LockedOrderCoupon lockCoupon(Long userId,
                                        OrderSkuSnapshot sku,
                                        BigDecimal orderAmount,
                                        String rawUserCouponId,
                                        String orderNo,
                                        OffsetDateTime now);

    public LockedOrderCoupon releaseLockedCoupon(String orderNo, OffsetDateTime now);

    public List<Map<String, Object>> releaseLockedCoupons(List<OrderRedisSnapshot> snapshots);

    public LockedOrderCoupon useLockedCoupon(String orderNo, OffsetDateTime now);
}
