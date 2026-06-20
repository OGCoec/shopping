package com.example.ShoppingSystem.mapper.product;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.Map;

@Mapper
public interface OrderProductSkuMapper {

    Map<String, Object> findSkuForOrder(@Param("skuId") byte[] skuId);

    Map<String, Object> findHotSkuForOrder(@Param("skuId") byte[] skuId,
                                           @Param("now") OffsetDateTime now);

    int deductNormalSkuStock(@Param("skuId") byte[] skuId,
                             @Param("quantity") int quantity);

    int increaseNormalSkuStock(@Param("skuId") byte[] skuId,
                               @Param("quantity") int quantity);

    int increaseNormalSkuStocks(@Param("itemsJson") String itemsJson);
}
