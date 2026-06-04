package com.example.ShoppingSystem.mapper.order;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface OrderCardSecretDeliveryMapper {

    List<Map<String, Object>> listOrderItemsForUserOrder(@Param("userId") Long userId,
                                                         @Param("orderNo") String orderNo);

    List<Map<String, Object>> listPaidOrderItemsForDelivery(@Param("ordersJson") String ordersJson);

    List<Map<String, Object>> deliverPaidOrderCardSecrets(@Param("itemsJson") String itemsJson);

    List<Map<String, Object>> listDeliveredSecretsForUserOrder(@Param("userId") Long userId,
                                                               @Param("orderNo") String orderNo);
}
