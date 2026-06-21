package com.example.ShoppingSystem.admin.service.product.impl.AdminCardSecretInventoryService;

import com.example.ShoppingSystem.common.datasource.DataSourceRoute;
import com.example.ShoppingSystem.mapper.product.CardSecretInventoryMapper;
import com.example.ShoppingSystem.outbox.OutboxEventRequest;
import com.example.ShoppingSystem.outbox.annotation.OutboxEventCollector;
import com.example.ShoppingSystem.outbox.annotation.TransactionalOutbox;
import com.example.ShoppingSystem.outbox.cardsecretinventory.CardSecretInventoryImportedMessage;
import com.example.ShoppingSystem.outbox.cardsecretinventory.CardSecretInventoryImportedRouting;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class CardSecretInventoryImportWriter {

    private final CardSecretInventoryMapper cardSecretInventoryMapper;
    private final OutboxEventCollector outboxEvents;

    public CardSecretInventoryImportWriter(CardSecretInventoryMapper cardSecretInventoryMapper,
                                           OutboxEventCollector outboxEvents) {
        this.cardSecretInventoryMapper = cardSecretInventoryMapper;
        this.outboxEvents = outboxEvents;
    }

    @TransactionalOutbox(DataSourceRoute.TRADE)
    public ImportResult importAndEnqueueStockIncrease(Long spuId,
                                                      byte[] skuId,
                                                      String skuIdHex,
                                                      String batchNo,
                                                      String itemsJson,
                                                      String batchFingerprint) {
        Map<String, Object> result = cardSecretInventoryMapper.batchInsertIgnoreDuplicates(spuId, skuId, itemsJson);
        int insertedCount = intValue(result == null ? null : result.get("insertedCount"));
        if (insertedCount > 0) {
            String idempotencyKey = "card-secret-stock-increase:" + spuId + ":" + skuIdHex + ":" + batchFingerprint;
            CardSecretInventoryImportedMessage event = new CardSecretInventoryImportedMessage(
                    idempotencyKey,
                    spuId,
                    skuIdHex,
                    insertedCount,
                    batchNo,
                    batchFingerprint,
                    System.currentTimeMillis()
            );
            outboxEvents.register(new OutboxEventRequest(
                    event.getEventId(),
                    CardSecretInventoryImportedRouting.EVENT_TYPE,
                    CardSecretInventoryImportedRouting.AGGREGATE_TYPE,
                    skuIdHex,
                    CardSecretInventoryImportedRouting.EXCHANGE,
                    CardSecretInventoryImportedRouting.ROUTING_KEY,
                    event,
                    idempotencyKey
            ));
        }
        return new ImportResult(
                intValue(result == null ? null : result.get("requestedCount")),
                insertedCount
        );
    }

    private int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    public record ImportResult(int requestedCount, int insertedCount) {
    }
}
