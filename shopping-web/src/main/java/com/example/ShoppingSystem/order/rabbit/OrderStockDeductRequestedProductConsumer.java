package com.example.ShoppingSystem.order.rabbit;

import com.example.ShoppingSystem.Utils.HybridIdCodec;
import com.example.ShoppingSystem.common.datasource.DataSourceRoute;
import com.example.ShoppingSystem.order.service.OrderCreateContext;
import com.example.ShoppingSystem.order.service.OrderServiceException;
import com.example.ShoppingSystem.order.service.OrderSkuSnapshot;
import com.example.ShoppingSystem.order.service.OrderStockDeductResultWriter;
import com.example.ShoppingSystem.order.service.inventory.OrderInventoryDeductResult;
import com.example.ShoppingSystem.order.service.inventory.OrderInventoryStrategy;
import com.example.ShoppingSystem.order.service.inventory.OrderInventoryType;
import com.example.ShoppingSystem.outbox.annotation.IdempotentConsumer;
import com.example.ShoppingSystem.outbox.fault.FaultInjector;
import com.example.ShoppingSystem.outbox.orderstock.OrderStockDeductRequestedMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class OrderStockDeductRequestedProductConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderStockDeductRequestedProductConsumer.class);

    private final Map<OrderInventoryType, OrderInventoryStrategy> inventoryStrategies;
    private final OrderStockDeductResultWriter orderStockDeductResultWriter;

    private final FaultInjector faultInjector;
    public OrderStockDeductRequestedProductConsumer(List<OrderInventoryStrategy> strategies,
                                                     OrderStockDeductResultWriter orderStockDeductResultWriter,
                                                     FaultInjector faultInjector) {
        this.inventoryStrategies = strategies.stream()
                .collect(Collectors.toUnmodifiableMap(OrderInventoryStrategy::type, Function.identity()));
        this.orderStockDeductResultWriter = orderStockDeductResultWriter;
        this.faultInjector = faultInjector;
    }

    @RabbitListener(
            queues = "#{orderStockDeductRequestedProductQueue.name}",
            containerFactory = "orderStockDeductRequestedRabbitListenerContainerFactory"
    )
    @IdempotentConsumer(route = DataSourceRoute.PRODUCT, consumer = "order-stock-deduct-product",
            eventId = "#message.eventId", transactional = true)
    public void consume(OrderStockDeductRequestedMessage message) {
        if (!hasOrderIdentity(message)) {
            log.warn("[OrderStockDeduct] invalid message skipped, message={}", message);
            return;
        }
        faultInjector.maybeFail("order-stock-deduct-product", message == null ? null : message.getLoadtestFault());
        OrderCreateContext context;
        try {
            context = toContext(message);
        } catch (IllegalArgumentException e) {
            publishResult(message, OrderInventoryDeductResult.fail(
                    "ORDER_STOCK_DEDUCT_REQUEST_INVALID",
                    "Order stock deduct request is invalid."));
            return;
        }
        OrderInventoryStrategy strategy = inventoryStrategies.get(context.inventoryType());
        if (strategy == null) {
            throw new OrderServiceException("ORDER_INVENTORY_STRATEGY_NOT_FOUND",
                    "Order inventory strategy is missing.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        OrderInventoryDeductResult result = strategy.deduct(context);
        publishResult(message, result);
        log.info("[OrderStockDeduct] product stock deduct handled, orderNo={}, success={}, code={}, eventId={}",
                message.getOrderNo(), result.success(), result.code(), message.getEventId());
    }

    private void publishResult(OrderStockDeductRequestedMessage message, OrderInventoryDeductResult result) {
        orderStockDeductResultWriter.publishResult(
                message.getOrderNo(),
                message.getUserId(),
                result,
                OffsetDateTime.now()
        );
    }

    private OrderCreateContext toContext(OrderStockDeductRequestedMessage message) {
        byte[] skuId = HybridIdCodec.fromHex(message.getSkuIdHex());
        String skuIdText = text(message.getSkuIdText());
        if (skuIdText.isBlank()) {
            skuIdText = HybridIdCodec.toBase62(skuId);
        }
        if (message.getQuantity() <= 0) {
            throw new IllegalArgumentException("Quantity is invalid.");
        }
        OrderInventoryType inventoryType = inventoryType(message);
        OrderSkuSnapshot sku = new OrderSkuSnapshot(
                skuId,
                skuIdText,
                message.getSpuId(),
                message.getCategoryId(),
                text(message.getSkuCode()),
                text(message.getSkuName()),
                text(message.getSpecJson()),
                text(message.getSkuImageUrl()),
                money(message.getPriceYuan()),
                message.isPointExchangeEnabled(),
                message.getPointExchangePoints(),
                message.isHotSku()
        );
        return new OrderCreateContext(
                message.getOrderNo().trim(),
                message.getUserId(),
                sku,
                message.getQuantity(),
                text(message.getIdempotencyKey()),
                text(message.getRawUserCouponId()),
                epochMillis(message.getCreatedAtEpochMillis()),
                epochMillis(message.getExpireAtEpochMillis()),
                inventoryType
        );
    }

    private OrderInventoryType inventoryType(OrderStockDeductRequestedMessage message) {
        String value = text(message.getInventoryType());
        if (value.isBlank()) {
            return message.isHotSku() ? OrderInventoryType.HOT : OrderInventoryType.NORMAL;
        }
        return OrderInventoryType.valueOf(value);
    }

    private OffsetDateTime epochMillis(long value) {
        long millis = value <= 0L ? System.currentTimeMillis() : value;
        return OffsetDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneOffset.UTC);
    }

    private BigDecimal money(String value) {
        String text = text(value);
        return text.isBlank() ? BigDecimal.ZERO : new BigDecimal(text);
    }

    private boolean hasOrderIdentity(OrderStockDeductRequestedMessage message) {
        return message != null
                && message.getEventId() != null && !message.getEventId().isBlank()
                && message.getOrderNo() != null && !message.getOrderNo().isBlank()
                && message.getUserId() != null;
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}