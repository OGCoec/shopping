package com.example.ShoppingSystem.mapper.coupon;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

@Mapper
public interface CouponUsageRecordMapper {

    int insertUsageRecordIgnore(@Param("id") byte[] id,
                                @Param("userCouponId") byte[] userCouponId,
                                @Param("couponTemplateId") byte[] couponTemplateId,
                                @Param("userId") Long userId,
                                @Param("orderNo") String orderNo,
                                @Param("action") String action,
                                @Param("orderAmountYuan") BigDecimal orderAmountYuan,
                                @Param("discountAmountYuan") BigDecimal discountAmountYuan,
                                @Param("idempotencyKey") String idempotencyKey);

    int batchInsertUsageRecordsIgnore(@Param("recordsJson") String recordsJson);
}
