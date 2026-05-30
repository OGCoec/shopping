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

    int expireUnusedCoupons();
}
