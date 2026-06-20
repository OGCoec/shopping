package com.example.ShoppingSystem.mapper.product;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface ProductSpuMapper {

    long countSpuPage(@Param("categoryId") Long categoryId,
                      @Param("status") String status);

    List<Map<String, Object>> listSpuPage(@Param("categoryId") Long categoryId,
                                          @Param("status") String status,
                                          @Param("limit") int limit,
                                          @Param("offset") long offset);

    List<Map<String, Object>> listSpuPageByIds(@Param("ids") List<Long> ids,
                                               @Param("categoryId") Long categoryId,
                                               @Param("status") String status);

    List<Map<String, Object>> listSpuIndexDocuments(@Param("limit") int limit,
                                                    @Param("offset") long offset);

    List<Map<String, Object>> listSpuIndexDocumentsByIds(@Param("ids") List<Long> ids);

    List<Map<String, Object>> listSpuIndexDocumentsByCategoryIds(@Param("categoryIds") List<Long> categoryIds,
                                                                 @Param("limit") int limit,
                                                                 @Param("offset") long offset);

    List<Long> listSpuIdsByCategoryIds(@Param("categoryIds") List<Long> categoryIds,
                                       @Param("limit") int limit,
                                       @Param("offset") long offset);

    long countExistingSpuIds(@Param("ids") List<Long> ids);

    long countExistingSkuIds(@Param("ids") List<byte[]> ids);

    List<String> listSkuIdHexesBySpuId(@Param("spuId") Long spuId);

    List<Map<String, Object>> listSkuSummariesBySkuIds(@Param("ids") List<byte[]> ids);

    Map<String, Object> findSpuById(@Param("id") Long id);

    Map<String, Object> findSpuDetailById(@Param("id") Long id);

    Map<String, Object> findActivePublicSpuDetailById(@Param("id") Long id);

    Map<String, Object> findSkuBySpuIdAndSkuId(@Param("spuId") Long spuId,
                                               @Param("skuId") byte[] skuId);

    Map<String, Object> updateSpuDetail(@Param("id") Long id,
                                        @Param("categoryId") Long categoryId,
                                        @Param("name") String name,
                                        @Param("subtitle") String subtitle,
                                        @Param("brandName") String brandName,
                                        @Param("mainImageUrl") String mainImageUrl,
                                        @Param("status") String status,
                                        @Param("detailId") Long detailId,
                                        @Param("imageUrlsJson") String imageUrlsJson,
                                        @Param("detailImageUrlsJson") String detailImageUrlsJson,
                                        @Param("attributesJson") String attributesJson,
                                        @Param("description") String description,
                                        @Param("afterSale") String afterSale,
                                        @Param("skusJson") String skusJson);

    Map<String, Object> insertSku(@Param("spuId") Long spuId,
                                  @Param("skuId") byte[] skuId,
                                  @Param("skuCode") String skuCode,
                                  @Param("skuName") String skuName,
                                  @Param("specJson") String specJson,
                                  @Param("skuImageUrlsJson") String skuImageUrlsJson,
                                  @Param("priceYuan") java.math.BigDecimal priceYuan,
                                  @Param("originalPriceYuan") java.math.BigDecimal originalPriceYuan,
                                  @Param("stockQuantity") Integer stockQuantity,
                                  @Param("status") String status);

    Map<String, Object> updateSkuBySpuIdAndSkuId(@Param("spuId") Long spuId,
                                                 @Param("skuId") byte[] skuId,
                                                 @Param("skuCode") String skuCode,
                                                 @Param("skuName") String skuName,
                                                 @Param("specJson") String specJson,
                                                 @Param("skuImageUrlsJson") String skuImageUrlsJson,
                                                 @Param("priceYuan") java.math.BigDecimal priceYuan,
                                                 @Param("originalPriceYuan") java.math.BigDecimal originalPriceYuan,
                                                 @Param("stockQuantity") Integer stockQuantity,
                                                 @Param("status") String status);

    Map<String, Object> updateSkuStatusBySpuIdAndSkuId(@Param("spuId") Long spuId,
                                                       @Param("skuId") byte[] skuId,
                                                       @Param("status") String status);

    Map<String, Object> deleteSkuBySpuIdAndSkuIdReturningImages(@Param("spuId") Long spuId,
                                                                @Param("skuId") byte[] skuId);

    Map<String, Object> batchUpdateSkuStatusByIds(@Param("spuId") Long spuId,
                                                  @Param("ids") List<byte[]> ids,
                                                  @Param("status") String status);

    Map<String, Object> batchDeleteSkuByIdsReturningImages(@Param("spuId") Long spuId,
                                                           @Param("ids") List<byte[]> ids);

    List<Long> listAllSpuIds(@Param("limit") int limit, @Param("offset") long offset);

    List<String> listAllSkuIds(@Param("limit") int limit, @Param("offset") long offset);

    int insertSpu(@Param("id") Long id,
                  @Param("categoryId") Long categoryId,
                  @Param("name") String name,
                  @Param("subtitle") String subtitle,
                  @Param("brandName") String brandName,
                  @Param("mainImageUrl") String mainImageUrl,
                  @Param("status") String status);

    int updateStatus(@Param("id") Long id, @Param("status") String status);

    Map<String, Object> batchUpdateStatusByIds(@Param("ids") List<Long> ids,
                                               @Param("status") String status);

    Map<String, Object> batchUpdateStatusByLeafCategory(@Param("categoryId") Long categoryId,
                                                        @Param("status") String status);

    Map<String, Object> batchDeleteByIds(@Param("ids") List<Long> ids);

    Map<String, Object> batchDeleteByLeafCategory(@Param("categoryId") Long categoryId);
}
