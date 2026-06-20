package com.example.ShoppingSystem.outbox;

public record OutboxEventRequest(String eventId,
                                 String eventType,
                                 String aggregateType,
                                 String aggregateId,
                                 String exchangeName,
                                 String routingKey,
                                 Object payload,
                                 String idempotencyKey) {
}
