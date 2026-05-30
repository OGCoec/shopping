package com.example.ShoppingSystem.mapper.product;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface ProductHotSkuMapper {

    List<Map<String, Object>> listHotSkusBySpuId(@Param("spuId") Long spuId);

    Map<String, Object> upsertHotSkus(@Param("spuId") Long spuId,
                                      @Param("itemsJson") String itemsJson);

    Map<String, Object> deleteHotSkusBySkuIds(@Param("spuId") Long spuId,
                                              @Param("ids") List<byte[]> ids);
}
