package com.example.ShoppingSystem.order.rabbit;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "app.rabbitmq.order-expire")
public class OrderExpireRabbitProperties {

    private String exchange = "order.expire.exchange";
    private String delayQueue = "order.expire.delay.queue";
    private String deadLetterQueue = "order.expire.dlq";
    private String delayRoutingKey = "order.expire.delay";
    private String deadRoutingKey = "order.expire.dead";
    private String paymentCheckExchange = "order.payment.expire.delay.exchange";
    private String paymentCheckQueue = "order.payment.expire.check.queue";
    private String paymentCheckRoutingKey = "order.payment.expire.check";
    private String closingFinalizeExchange = "order.closing.finalize.delay.exchange";
    private String closingFinalizeQueue = "order.closing.finalize.check.queue";
    private String closingFinalizeRoutingKey = "order.closing.finalize.check";
    private List<Long> paymentCheckDelaysMillis = new ArrayList<>(List.of(
            10_000L,
            10_000L,
            10_000L,
            15_000L,
            15_000L,
            30_000L,
            30_000L,
            60_000L,
            120_000L
    ));
    private List<Long> closingFinalizeDelaysMillis = new ArrayList<>(List.of(
            30_000L,
            30_000L,
            60_000L,
            60_000L,
            120_000L
    ));
    private long ttlMillis = 300_000L;
    private long closingGraceMillis = 300_000L;
    private int concurrency = 2;
    private int maxConcurrency = 10;
    private int prefetch = 10;

    public List<Long> effectivePaymentCheckDelaysMillis() {
        List<Long> normalized = normalizeDelays(paymentCheckDelaysMillis);
        if (!normalized.isEmpty()) {
            return normalized;
        }
        return List.of(Math.max(1L, ttlMillis));
    }

    public List<Long> effectiveClosingFinalizeDelaysMillis() {
        List<Long> normalized = normalizeDelays(closingFinalizeDelaysMillis);
        if (!normalized.isEmpty()) {
            return normalized;
        }
        return List.of(Math.max(1L, closingGraceMillis));
    }

    public long paymentCheckWindowMillis() {
        return effectivePaymentCheckDelaysMillis().stream()
                .mapToLong(Long::longValue)
                .sum();
    }

    public long closingFinalizeWindowMillis() {
        return effectiveClosingFinalizeDelaysMillis().stream()
                .mapToLong(Long::longValue)
                .sum();
    }

    private List<Long> normalizeDelays(List<Long> delays) {
        return delays == null
                ? List.of()
                : delays.stream()
                .filter(value -> value != null && value > 0)
                .map(value -> Math.max(1L, value))
                .toList();
    }
}
