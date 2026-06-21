package com.example.ShoppingSystem.mapper.common;

import java.time.OffsetDateTime;

public class OutboxEventRow {

    private final String eventId;
    private final String eventType;
    private final String aggregateType;
    private final String aggregateId;
    private final String exchangeName;
    private final String routingKey;
    private final String payloadJson;
    private final String idempotencyKey;
    private final OffsetDateTime createdAt;

    public OutboxEventRow(String eventId,
                          String eventType,
                          String aggregateType,
                          String aggregateId,
                          String exchangeName,
                          String routingKey,
                          String payloadJson,
                          String idempotencyKey,
                          OffsetDateTime createdAt) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.exchangeName = exchangeName;
        this.routingKey = routingKey;
        this.payloadJson = payloadJson;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = createdAt;
    }

    public String getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getExchangeName() {
        return exchangeName;
    }

    public String getRoutingKey() {
        return routingKey;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
