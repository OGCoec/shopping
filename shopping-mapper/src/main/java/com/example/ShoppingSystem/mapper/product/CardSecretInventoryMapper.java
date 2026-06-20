package com.example.ShoppingSystem.mapper.product;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface CardSecretInventoryMapper {

    Map<String, Object> batchInsertIgnoreDuplicates(@Param("spuId") Long spuId,
                                                    @Param("skuId") byte[] skuId,
                                                    @Param("itemsJson") String itemsJson);

    List<Map<String, Object>> pageInventoryForAdmin(@Param("spuId") Long spuId,
                                                    @Param("skuId") byte[] skuId,
                                                    @Param("skuIdHexes") List<String> skuIdHexes,
                                                    @Param("batchNo") String batchNo,
                                                    @Param("inventoryStatus") String inventoryStatus,
                                                    @Param("deliveryStatus") String deliveryStatus,
                                                    @Param("orderNo") String orderNo,
                                                    @Param("userId") Long userId,
                                                    @Param("orderStatus") String orderStatus,
                                                    @Param("createdByMe") boolean createdByMe,
                                                    @Param("currentAdminAvailable") boolean currentAdminAvailable,
                                                    @Param("currentAdminUsername") String currentAdminUsername,
                                                    @Param("currentAdminEmail") String currentAdminEmail,
                                                    @Param("currentAdminPhone") String currentAdminPhone,
                                                    @Param("createdByAdminUsername") String createdByAdminUsername,
                                                    @Param("importSource") String importSource);

    List<Map<String, Object>> pageDeliveriesForAdmin(@Param("spuId") Long spuId,
                                                     @Param("skuId") byte[] skuId,
                                                     @Param("skuIdHexes") List<String> skuIdHexes,
                                                     @Param("orderNo") String orderNo,
                                                     @Param("userId") Long userId,
                                                     @Param("deliveryStatus") String deliveryStatus,
                                                     @Param("orderStatus") String orderStatus,
                                                     @Param("createdByMe") boolean createdByMe,
                                                     @Param("currentAdminAvailable") boolean currentAdminAvailable,
                                                     @Param("currentAdminUsername") String currentAdminUsername,
                                                     @Param("currentAdminEmail") String currentAdminEmail,
                                                     @Param("currentAdminPhone") String currentAdminPhone,
                                                     @Param("createdByAdminUsername") String createdByAdminUsername);

    Map<String, Object> findRevealForAdmin(@Param("cardSecretId") byte[] cardSecretId);
}
