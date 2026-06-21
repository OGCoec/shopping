package com.example.ShoppingSystem.outbox;

import com.example.ShoppingSystem.common.datasource.DataSourceRoute;
import com.example.ShoppingSystem.common.datasource.RoutedTransactionExecutor;
import com.example.ShoppingSystem.mapper.common.OutboxEventMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "shopping.outbox.dispatcher.enabled", havingValue = "true")
public class OutboxEventDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventDispatcher.class);

    private final OutboxEventMapper outboxEventMapper;
    private final RoutedTransactionExecutor routedTransactionExecutor;
    private final RabbitTemplate rabbitTemplate;
    private final OutboxDispatcherProperties properties;

    public OutboxEventDispatcher(OutboxEventMapper outboxEventMapper,
                                 RoutedTransactionExecutor routedTransactionExecutor,
                                 RabbitTemplate rabbitTemplate,
                                 OutboxDispatcherProperties properties) {
        this.outboxEventMapper = outboxEventMapper;
        this.routedTransactionExecutor = routedTransactionExecutor;
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    @Scheduled(
            initialDelayString = "${shopping.outbox.dispatcher.initial-delay-ms:5000}",
            fixedDelayString = "${shopping.outbox.dispatcher.fixed-delay-ms:5000}"
    )
    public void dispatch() {
        for (DataSourceRoute route : properties.getRoutes()) {
            dispatchRoute(route);
        }
    }

    private void dispatchRoute(DataSourceRoute route) {
        if (route == null) {
            return;
        }
        int limit = Math.max(1, Math.min(properties.getBatchSize(), 500));
        List<Map<String, Object>> events = routedTransactionExecutor.execute(
                route,
                () -> outboxEventMapper.claimBatch(
                        limit,
                        Math.max(1, properties.getMaxRetry()),
                        Math.max(1000L, properties.getProcessingTimeoutMs())
                )
        );
        if (events == null || events.isEmpty()) {
            return;
        }
        for (Map<String, Object> event : events) {
            publishOne(route, event);
        }
    }

    private void publishOne(DataSourceRoute route, Map<String, Object> event) {
        String eventId = text(event, "eventId");
        try {
            String exchangeName = text(event, "exchangeName");
            String routingKey = text(event, "routingKey");
            String payloadJson = text(event, "payloadJson");
            // 鍏宠仈 publisher confirm锛岀‘璁?broker 钀借处鍚庢墠鏍囪 PUBLISHED
            CorrelationData correlationData = new CorrelationData(eventId);
            // Send the stored JSON text as-is (raw bytes, contentType=json) so the
            // consumer's Jackson converter deserializes the target type directly.
            // Using convertAndSend(String) would double-encode the JSON into a quoted string.
            MessageProperties messageProperties = new MessageProperties();
            messageProperties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            messageProperties.setContentEncoding(StandardCharsets.UTF_8.name());
            messageProperties.setMessageId(eventId);
            messageProperties.setHeader("eventId", eventId);
            messageProperties.setHeader("eventType", text(event, "eventType"));
            messageProperties.setHeader("aggregateType", text(event, "aggregateType"));
            messageProperties.setHeader("aggregateId", text(event, "aggregateId"));
            messageProperties.setHeader("idempotencyKey", text(event, "idempotencyKey"));
            messageProperties.setHeader("sourceRoute", route.name());
            Message amqpMessage = MessageBuilder
                    .withBody(payloadJson.getBytes(StandardCharsets.UTF_8))
                    .andProperties(messageProperties)
                    .build();
            rabbitTemplate.send(exchangeName, routingKey, amqpMessage, correlationData);
            awaitConfirm(eventId, correlationData);
            routedTransactionExecutor.executeWithoutResult(
                    route,
                    () -> outboxEventMapper.markPublished(eventId, OffsetDateTime.now())
            );
        } catch (Exception e) {
            OffsetDateTime nextRetryAt = OffsetDateTime.now().plusNanos(
                    Math.max(1000L, properties.getRetryBackoffBaseMs()) * 1_000_000L
            );
            routedTransactionExecutor.executeWithoutResult(
                    route,
                    () -> outboxEventMapper.markRetry(
                            eventId,
                            Math.max(1, properties.getMaxRetry()),
                            nextRetryAt,
                            trimToLimit(e.getMessage(), 2000)
                    )
            );
            log.warn("[Outbox] publish failed, route={}, eventId={}, reason={}", route, eventId, e.getMessage());
        }
    }

    private void awaitConfirm(String eventId, CorrelationData correlationData) throws Exception {
        long timeoutMs = Math.max(1000L, properties.getConfirmTimeoutMs());
        CorrelationData.Confirm confirm =
                correlationData.getFuture().get(timeoutMs, TimeUnit.MILLISECONDS);
        if (confirm == null || !confirm.isAck()) {
            throw new IllegalStateException(
                    "Publisher confirm nack, eventId=" + eventId
                            + ", reason=" + (confirm == null ? "null" : confirm.getReason()));
        }
        if (correlationData.getReturned() != null) {
            throw new IllegalStateException(
                    "Message returned as unroutable, eventId=" + eventId
                            + ", reply=" + correlationData.getReturned().getReplyText());
        }
    }

    private String text(Map<String, Object> row, String key) {
        if (row == null || key == null) {
            return "";
        }
        Object value = row.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String trimToLimit(String value, int limit) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() <= limit) {
            return normalized;
        }
        return normalized.substring(0, limit);
    }
}
