package com.example.ShoppingSystem.order.service;

import com.example.ShoppingSystem.order.dto.OrderDetailResponse;
import com.example.ShoppingSystem.order.dto.OrderItemResponse;
import com.example.ShoppingSystem.order.dto.OrderPageItemResponse;

import java.util.List;
import java.util.Map;

final class OrderResponseAssembler {

    private OrderResponseAssembler() {
    }

    static OrderItemResponse item(Map<String, Object> row) {
        return new OrderItemResponse(
                OrderRowMapper.idText(row, "skuId"),
                OrderRowMapper.longValue(row, "spuId"),
                OrderRowMapper.text(row, "skuCode"),
                OrderRowMapper.text(row, "skuName"),
                OrderRowMapper.text(row, "specJson"),
                OrderRowMapper.text(row, "skuImageUrl"),
                OrderRowMapper.intValue(row, "quantity", 0),
                OrderAmountCalculator.money(OrderRowMapper.decimal(row, "salePriceYuan")),
                OrderAmountCalculator.money(OrderRowMapper.decimal(row, "lineAmountYuan")),
                OrderRowMapper.boolValue(row, "hotSku")
        );
    }

    static OrderDetailResponse detail(Map<String, Object> order, List<OrderItemResponse> items) {
        return new OrderDetailResponse(
                OrderRowMapper.text(order, "orderNo"),
                OrderRowMapper.text(order, "status"),
                OrderAmountCalculator.money(OrderRowMapper.decimal(order, "totalAmountYuan")),
                OrderAmountCalculator.money(OrderRowMapper.decimal(order, "discountAmountYuan")),
                OrderAmountCalculator.money(OrderRowMapper.decimal(order, "payAmountYuan")),
                OrderRowMapper.idText(order, "userCouponId"),
                OrderRowMapper.offsetDateTime(order, "expireAt"),
                OrderRowMapper.offsetDateTime(order, "paidAt"),
                OrderRowMapper.offsetDateTime(order, "closingAt"),
                OrderRowMapper.offsetDateTime(order, "closingDeadlineAt"),
                OrderRowMapper.offsetDateTime(order, "cancelledAt"),
                OrderRowMapper.offsetDateTime(order, "closedAt"),
                items
        );
    }

    static OrderPageItemResponse pageItem(Map<String, Object> row) {
        return new OrderPageItemResponse(
                OrderRowMapper.text(row, "orderNo"),
                OrderRowMapper.text(row, "status"),
                OrderAmountCalculator.money(OrderRowMapper.decimal(row, "payAmountYuan")),
                OrderRowMapper.offsetDateTime(row, "expireAt"),
                OrderRowMapper.offsetDateTime(row, "createdAt"),
                OrderRowMapper.text(row, "firstSkuName"),
                OrderRowMapper.text(row, "firstSkuImageUrl"),
                OrderRowMapper.intValue(row, "itemCount", 0)
        );
    }

    static OrderPageItemResponse pageItem(Map<String, Object> order, List<Map<String, Object>> items) {
        Map<String, Object> firstItem = items == null || items.isEmpty() ? Map.of() : items.get(0);
        return new OrderPageItemResponse(
                OrderRowMapper.text(order, "orderNo"),
                OrderRowMapper.text(order, "status"),
                OrderAmountCalculator.money(OrderRowMapper.decimal(order, "payAmountYuan")),
                OrderRowMapper.offsetDateTime(order, "expireAt"),
                OrderRowMapper.offsetDateTime(order, "createdAt"),
                OrderRowMapper.text(firstItem, "skuName"),
                OrderRowMapper.text(firstItem, "skuImageUrl"),
                items == null ? 0 : items.size()
        );
    }
}
