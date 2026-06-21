package com.example.ShoppingSystem.outbox;

import com.example.ShoppingSystem.common.datasource.DataSourceRoute;
import com.example.ShoppingSystem.common.datasource.RoutedTransactionExecutor;
import com.example.ShoppingSystem.mapper.common.OutboxEventMapper;
import com.example.ShoppingSystem.mapper.common.OutboxEventRow;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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
        List<String> eventIds = appendBatch(route, List.of(request));
        return eventIds.get(0);
    }

    public List<String> appendBatch(DataSourceRoute route, Collection<OutboxEventRequest> requests) {
        if (route == null) {
            throw new IllegalArgumentException("Outbox route is required.");
        }
        if (requests == null) {
            throw new IllegalArgumentException("Outbox event requests are required.");
        }
        if (requests.isEmpty()) {
            return List.of();
        }
        OffsetDateTime now = OffsetDateTime.now();
        List<OutboxEventRow> rows = new ArrayList<>(requests.size());
        List<String> eventIds = new ArrayList<>(requests.size());
        for (OutboxEventRequest request : requests) {
            if (request == null) {
                throw new IllegalArgumentException("Outbox event request is required.");
            }
            String eventId = normalizeOrGenerate(request.eventId());
            rows.add(new OutboxEventRow(
                    eventId,
                    requireText(request.eventType(), "eventType"),
                    blankToNull(request.aggregateType()),
                    blankToNull(request.aggregateId()),
                    requireText(request.exchangeName(), "exchangeName"),
                    requireText(request.routingKey(), "routingKey"),
                    toJson(request.payload()),
                    blankToNull(request.idempotencyKey()),
                    now
            ));
            eventIds.add(eventId);
        }
        routedTransactionExecutor.executeWithoutResult(route, () -> outboxEventMapper.insertEvents(rows));
        return List.copyOf(eventIds);
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
