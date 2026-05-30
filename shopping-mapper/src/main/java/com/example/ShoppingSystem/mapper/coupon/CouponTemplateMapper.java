package com.example.ShoppingSystem.mapper.coupon;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface CouponTemplateMapper {

    int insertTemplate(@Param("id") byte[] id,
                       @Param("couponCode") String couponCode,
                       @Param("name") String name,
                       @Param("discountType") String discountType,
                       @Param("thresholdAmountYuan") BigDecimal thresholdAmountYuan,
                       @Param("discountAmountYuan") BigDecimal discountAmountYuan,
                       @Param("discountRate") BigDecimal discountRate,
                       @Param("maxDiscountAmountYuan") BigDecimal maxDiscountAmountYuan,
                       @Param("totalQuantity") Integer totalQuantity,
                       @Param("remainingQuantity") Integer remainingQuantity,
                       @Param("perUserLimit") Integer perUserLimit,
                       @Param("scopeType") String scopeType,
                       @Param("receiveStartAt") OffsetDateTime receiveStartAt,
                       @Param("receiveEndAt") OffsetDateTime receiveEndAt,
                       @Param("validStartAt") OffsetDateTime validStartAt,
                       @Param("validEndAt") OffsetDateTime validEndAt);

    int updateTemplate(@Param("id") byte[] id,
                       @Param("couponCode") String couponCode,
                       @Param("name") String name,
                       @Param("discountType") String discountType,
                       @Param("thresholdAmountYuan") BigDecimal thresholdAmountYuan,
                       @Param("discountAmountYuan") BigDecimal discountAmountYuan,
                       @Param("discountRate") BigDecimal discountRate,
                       @Param("maxDiscountAmountYuan") BigDecimal maxDiscountAmountYuan,
                       @Param("totalQuantity") Integer totalQuantity,
                       @Param("remainingQuantity") Integer remainingQuantity,
                       @Param("perUserLimit") Integer perUserLimit,
                       @Param("scopeType") String scopeType,
                       @Param("receiveStartAt") OffsetDateTime receiveStartAt,
                       @Param("receiveEndAt") OffsetDateTime receiveEndAt,
                       @Param("validStartAt") OffsetDateTime validStartAt,
                       @Param("validEndAt") OffsetDateTime validEndAt);

    Map<String, Object> findById(@Param("id") byte[] id);

    List<Map<String, Object>> listTemplates(@Param("offset") int offset,
                                            @Param("pageSize") int pageSize,
                                            @Param("name") String name,
                                            @Param("status") String status);

    long countTemplates(@Param("name") String name,
                        @Param("status") String status);

    int publish(@Param("id") byte[] id);

    int disable(@Param("id") byte[] id);

    int softDelete(@Param("id") byte[] id);

    int batchUpdateRemainingQuantity(@Param("itemsJson") String itemsJson);

    int expireTemplates();
}
