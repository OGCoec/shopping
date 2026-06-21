package com.example.ShoppingSystem.order.service;

import com.example.ShoppingSystem.common.datasource.DataSourceRoute;
import com.example.ShoppingSystem.order.service.inventory.OrderInventoryDeductResult;
import com.example.ShoppingSystem.outbox.OutboxEventRequest;
import com.example.ShoppingSystem.outbox.annotation.OutboxEventCollector;
import com.example.ShoppingSystem.outbox.annotation.TransactionalOutbox;
import com.example.ShoppingSystem.outbox.orderstock.OrderStockDeductResultMessage;
import com.example.ShoppingSystem.outbox.orderstock.OrderStockDeductResultRouting;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Component
public class OrderStockDeductResultWriter {

    private final OutboxEventCollector outboxEventCollector;

    public OrderStockDeductResultWriter(OutboxEventCollector outboxEventCollector) {
        this.outboxEventCollector = outboxEventCollector;
    }

    @TransactionalOutbox(DataSourceRoute.PRODUCT)
    public void publishResult(String orderNo,
                              Long userId,
                              OrderInventoryDeductResult result,
                              OffsetDateTime occurredAt) {
        OrderInventoryDeductResult normalized = result == null
                ? OrderInventoryDeductResult.fail("ORDER_STOCK_DEDUCT_RESULT_MISSING", "Order stock deduct result is missing.")
                : result;
        String eventId = stockDeductResultEventId(orderNo);
        OrderStockDeductResultMessage message = new OrderStockDeductResultMessage(
                eventId,
                orderNo,
                userId,
                normalized.success(),
                normalized.code(),
                normalized.message(),
                normalized.remainingQuantity(),
                epochMillis(occurredAt)
        );
        outboxEventCollector.register(new OutboxEventRequest(
                eventId,
                OrderStockDeductResultRouting.EVENT_TYPE,
                OrderStockDeductResultRouting.AGGREGATE_TYPE,
                orderNo,
                OrderStockDeductResultRouting.EXCHANGE,
                OrderStockDeductResultRouting.ROUTING_KEY,
                message,
                eventId
        ));
    }

    public static String stockDeductResultEventId(String orderNo) {
        String normalized = orderNo == null ? "" : orderNo.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Order number is required.");
        }
        return "order-stock-deduct-result:" + normalized;
    }

    private long epochMillis(OffsetDateTime value) {
        return (value == null ? OffsetDateTime.now() : value).toInstant().toEpochMilli();
    }
}