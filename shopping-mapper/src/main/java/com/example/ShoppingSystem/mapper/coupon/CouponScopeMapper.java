package com.example.ShoppingSystem.mapper.coupon;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface CouponScopeMapper {

    int deleteByTemplateId(@Param("couponTemplateId") byte[] couponTemplateId);

    int insertScopes(@Param("itemsJson") String itemsJson);

    long countExistingTargets(@Param("scopeType") String scopeType,
                              @Param("itemsJson") String itemsJson);

    List<Map<String, Object>> listByTemplateId(@Param("couponTemplateId") byte[] couponTemplateId);

    List<Map<String, Object>> listByTemplateIds(@Param("couponTemplateIds") List<byte[]> couponTemplateIds);
}
