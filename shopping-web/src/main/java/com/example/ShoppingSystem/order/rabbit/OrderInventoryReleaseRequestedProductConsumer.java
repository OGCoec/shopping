package com.example.ShoppingSystem.order.rabbit;

import com.example.ShoppingSystem.common.datasource.DataSourceRoute;
import com.example.ShoppingSystem.order.service.OrderInventoryReleaseService;
import com.example.ShoppingSystem.order.service.OrderRedisSnapshot;
import com.example.ShoppingSystem.outbox.annotation.IdempotentConsumer;
import com.example.ShoppingSystem.outbox.fault.FaultInjector;
import com.example.ShoppingSystem.outbox.orderinventory.OrderInventoryReleaseRequestedMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OrderInventoryReleaseRequestedProductConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderInventoryReleaseRequestedProductConsumer.class);

    private final OrderInventoryReleaseService orderInventoryReleaseService;

    private final FaultInjector faultInjector;
    public OrderInventoryReleaseRequestedProductConsumer(OrderInventoryReleaseService orderInventoryReleaseService,
                                                            FaultInjector faultInjector) {
        this.orderInventoryReleaseService = orderInventoryReleaseService;
        this.faultInjector = faultInjector;
    }

    @RabbitListener(
            queues = "#{orderInventoryReleaseRequestedProductQueue.name}",
            containerFactory = "orderInventoryReleaseRequestedRabbitListenerContainerFactory"
    )
    @IdempotentConsumer(route = DataSourceRoute.PRODUCT, consumer = "order-inventory-release-product",
            eventId = "#message.eventId", transactional = true)
    public void consume(OrderInventoryReleaseRequestedMessage message) {
        if (!isUsable(message)) {
            log.warn("[OrderInventoryRelease] invalid message skipped, message={}", message);
            return;
        }
        faultInjector.maybeFail("order-inventory-release-product", message == null ? null : message.getLoadtestFault());
        Map<String, Object> order = new LinkedHashMap<>(message.getOrder() == null ? Map.of() : message.getOrder());
        order.putIfAbsent("orderNo", message.getOrderNo());
        order.putIfAbsent("userId", message.getUserId());
        int releasedItems = orderInventoryReleaseService.releaseAll(
                List.of(new OrderRedisSnapshot(order, message.getItems()))
        );
        log.info("[OrderInventoryRelease] product inventory released, orderNo={}, reason={}, items={}, eventId={}",
                message.getOrderNo(), message.getReason(), releasedItems, message.getEventId());
    }

    private boolean isUsable(OrderInventoryReleaseRequestedMessage message) {
        return message != null
                && message.getEventId() != null && !message.getEventId().isBlank()
                && message.getOrderNo() != null && !message.getOrderNo().isBlank()
                && message.getItems() != null && !message.getItems().isEmpty();
    }
}
