package com.example.ShoppingSystem.mapper.product;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface ProductHotSkuMapper {

    List<Map<String, Object>> listHotSkusBySpuId(@Param("spuId") Long spuId);

    Map<String, Object> findHotSkuBySkuId(@Param("spuId") Long spuId,
                                          @Param("skuId") byte[] skuId);

    List<Map<String, Object>> listActiveHotSkus(@Param("now") OffsetDateTime now,
                                                @Param("limit") int limit,
                                                @Param("offset") int offset);

    Map<String, Object> findActiveHotSkuBySkuId(@Param("skuId") byte[] skuId,
                                                @Param("now") OffsetDateTime now);

    List<Map<String, Object>> listRuntimeStocksBySkuIds(@Param("spuId") Long spuId,
                                                        @Param("ids") List<byte[]> ids,
                                                        @Param("now") OffsetDateTime now);

    Map<String, Object> upsertHotSkus(@Param("spuId") Long spuId,
                                      @Param("itemsJson") String itemsJson);

    Map<String, Object> batchWritebackRuntimeStock(@Param("itemsJson") String itemsJson);

    Map<String, Object> deleteHotSkusBySkuIds(@Param("spuId") Long spuId,
                                              @Param("ids") List<byte[]> ids);
}
