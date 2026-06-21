package com.example.ShoppingSystem.mapper.coupon;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface UserCouponMapper {

    int insertClaimedCouponIgnore(@Param("id") byte[] id,
                                  @Param("userId") Long userId,
                                  @Param("couponTemplateId") byte[] couponTemplateId,
                                  @Param("validStartAt") OffsetDateTime validStartAt,
                                  @Param("validEndAt") OffsetDateTime validEndAt,
                                  @Param("receivedAt") OffsetDateTime receivedAt);

    List<Map<String, Object>> listClaimedByTemplateId(@Param("couponTemplateId") byte[] couponTemplateId);

    List<Map<String, Object>> listByUserIds(@Param("userIds") List<Long> userIds,
                                            @Param("status") String status);

    List<Map<String, Object>> listByUserIdAndTemplateIds(@Param("userId") Long userId,
                                                         @Param("couponTemplateIds") List<byte[]> couponTemplateIds);

    List<Map<String, Object>> listAdminClaimsByTemplateIdForUser(@Param("couponTemplateId") byte[] couponTemplateId,
                                                                 @Param("status") String status,
                                                                 @Param("userId") Long userId,
                                                                 @Param("offset") int offset,
                                                                 @Param("pageSize") int pageSize);

    long countAdminClaimsByTemplateIdForUser(@Param("couponTemplateId") byte[] couponTemplateId,
                                             @Param("status") String status,
                                             @Param("userId") Long userId);

    List<Map<String, Object>> listMine(@Param("userId") Long userId,
                                       @Param("status") String status,
                                       @Param("offset") int offset,
                                       @Param("pageSize") int pageSize);

    long countMine(@Param("userId") Long userId,
                   @Param("status") String status);

    Map<String, Object> findMineById(@Param("userId") Long userId,
                                     @Param("userCouponId") byte[] userCouponId);

    List<Map<String, Object>> listOrderCandidateCoupons(@Param("userId") Long userId,
                                                        @Param("skuId") byte[] skuId,
                                                        @Param("spuId") Long spuId,
                                                        @Param("categoryId") Long categoryId,
                                                        @Param("now") java.time.OffsetDateTime now);

    Map<String, Object> findOrderCandidateCoupon(@Param("userId") Long userId,
                                                 @Param("userCouponId") byte[] userCouponId,
                                                 @Param("skuId") byte[] skuId,
                                                 @Param("spuId") Long spuId,
                                                 @Param("categoryId") Long categoryId,
                                                 @Param("now") java.time.OffsetDateTime now);

    Map<String, Object> lockCouponForOrder(@Param("userId") Long userId,
                                           @Param("userCouponId") byte[] userCouponId,
                                           @Param("orderNo") String orderNo,
                                           @Param("skuId") byte[] skuId,
                                           @Param("spuId") Long spuId,
                                           @Param("categoryId") Long categoryId,
                                           @Param("orderAmountYuan") java.math.BigDecimal orderAmountYuan,
                                           @Param("now") java.time.OffsetDateTime now);

    Map<String, Object> releaseLockedCouponByOrderNo(@Param("orderNo") String orderNo,
                                                     @Param("now") java.time.OffsetDateTime now);

    List<Map<String, Object>> releaseLockedCouponsByOrderNos(@Param("ordersJson") String ordersJson);

    Map<String, Object> useLockedCouponByOrderNo(@Param("orderNo") String orderNo,
                                                 @Param("now") java.time.OffsetDateTime now);

    List<Map<String, Object>> useLockedCouponsByOrderNos(@Param("ordersJson") String ordersJson);

    int expireUnusedCoupons();

    int expireUnusedCouponsByTemplateIds(@Param("templateIdHexes") List<String> templateIdHexes);
}
