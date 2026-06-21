package com.example.ShoppingSystem.admin.service.product.impl.AdminCardSecretInventoryService;

import com.example.ShoppingSystem.common.datasource.DataSourceRoute;
import com.example.ShoppingSystem.mapper.product.OrderProductSkuMapper;
import com.example.ShoppingSystem.outbox.annotation.IdempotentConsumer;
import com.example.ShoppingSystem.outbox.fault.FaultInjector;
import com.example.ShoppingSystem.outbox.cardsecretinventory.CardSecretInventoryImportedMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.HexFormat;

@Component
public class CardSecretInventoryImportedProductConsumer {

    private static final Logger log = LoggerFactory.getLogger(CardSecretInventoryImportedProductConsumer.class);

    private final OrderProductSkuMapper orderProductSkuMapper;

    private final FaultInjector faultInjector;
    public CardSecretInventoryImportedProductConsumer(OrderProductSkuMapper orderProductSkuMapper, FaultInjector faultInjector) {
        this.orderProductSkuMapper = orderProductSkuMapper;
        this.faultInjector = faultInjector;
    }

    @RabbitListener(
            queues = "#{cardSecretInventoryImportedProductQueue.name}",
            containerFactory = "cardSecretInventoryImportedRabbitListenerContainerFactory"
    )
    @IdempotentConsumer(route = DataSourceRoute.PRODUCT, consumer = "card-secret-inventory-imported-product",
            eventId = "#message.eventId", transactional = true)
    public void consume(CardSecretInventoryImportedMessage message) {
        if (!isUsable(message)) {
            log.warn("[CardSecretInventoryImported] invalid message skipped, message={}", message);
            return;
        }
        faultInjector.maybeFail("card-secret-inventory-imported-product", message == null ? null : message.getLoadtestFault());
        int updated = orderProductSkuMapper.increaseNormalSkuStock(
                HexFormat.of().parseHex(message.getSkuIdHex()),
                message.getInsertedCount()
        );
        log.info("[CardSecretInventoryImported] product stock increased, spuId={}, skuIdHex={}, quantity={}, updated={}, eventId={}",
                message.getSpuId(), message.getSkuIdHex(), message.getInsertedCount(), updated, message.getEventId());
    }

    private boolean isUsable(CardSecretInventoryImportedMessage message) {
        return message != null
                && message.getEventId() != null && !message.getEventId().isBlank()
                && message.getSpuId() != null && message.getSpuId() > 0
                && message.getSkuIdHex() != null && message.getSkuIdHex().matches("^[0-9A-Fa-f]{32}$")
                && message.getInsertedCount() > 0;
    }
}
