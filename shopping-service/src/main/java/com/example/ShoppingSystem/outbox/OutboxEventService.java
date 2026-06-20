package com.example.ShoppingSystem.outbox;

import com.example.ShoppingSystem.common.datasource.DataSourceRoute;
import com.example.ShoppingSystem.common.datasource.RoutedTransactionExecutor;
import com.example.ShoppingSystem.mapper.common.OutboxEventMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class OutboxEventService {

    private final OutboxEventMapper outboxEventMapper;
    private final RoutedTransactionExecutor routedTransactionExecutor;
    private final ObjectMapper objectMapper;

    public OutboxEventService(OutboxEventMapper outboxEventMapper,
                              RoutedTransactionExecutor routedTransactionExecutor,
                              ObjectMapper objectMapper) {
        this.outboxEventMapper = outboxEventMapper;
        this.routedTransactionExecutor = routedTransactionExecutor;
        this.objectMapper = objectMapper;
    }

    public String append(DataSourceRoute route, OutboxEventRequest request) {
        if (route == null) {
            throw new IllegalArgumentException("Outbox route is required.");
        }
        if (request == null) {
            throw new IllegalArgumentException("Outbox event request is required.");
        }
        String eventId = normalizeOrGenerate(request.eventId());
        String eventType = requireText(request.eventType(), "eventType");
        String exchangeName = requireText(request.exchangeName(), "exchangeName");
        String routingKey = requireText(request.routingKey(), "routingKey");
        String payloadJson = toJson(request.payload());
        OffsetDateTime now = OffsetDateTime.now();
        routedTransactionExecutor.executeWithoutResult(route, () -> outboxEventMapper.insertEvent(
                eventId,
                eventType,
                blankToNull(request.aggregateType()),
                blankToNull(request.aggregateId()),
                exchangeName,
                routingKey,
                payloadJson,
                blankToNull(request.idempotencyKey()),
                now
        ));
        return eventId;
    }

    private String normalizeOrGenerate(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() ? UUID.randomUUID().toString() : normalized;
    }

    private String requireText(String value, String field) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Outbox " + field + " is required.");
        }
        return normalized;
    }

    private String blankToNull(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload == null ? new Object() : payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Outbox payload json is invalid.", e);
        }
    }
}
