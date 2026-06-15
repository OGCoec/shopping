package com.example.ShoppingSystem.order.rabbit;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderExpireMessagePublisher {

    private static final String X_DELAY_HEADER = "x-delay";

    private final RabbitTemplate rabbitTemplate;
    private final OrderExpireRabbitProperties properties;

    public OrderExpireMessagePublisher(RabbitTemplate rabbitTemplate,
                                       OrderExpireRabbitProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    public void publish(OrderExpireMessage message) {
        publish(message, properties.getTtlMillis());
    }

    public void publishPaymentCheck(String orderNo, Long userId, Long expireAtEpochMilli) {
        List<Long> delays = properties.effectivePaymentCheckDelaysMillis();
        long delayMillis = delays.get(0);
        OrderPaymentExpireCheckMessage message = new OrderPaymentExpireCheckMessage(
                orderNo,
                userId,
                expireAtEpochMilli,
                tail(delays)
        );
        publishPaymentCheck(message, delayMillis);
    }

    public boolean publishNextPaymentCheck(OrderPaymentExpireCheckMessage message) {
        if (message == null || message.remainingDelayMillis().isEmpty()) {
            return false;
        }
        List<Long> delays = message.remainingDelayMillis();
        long delayMillis = delays.get(0);
        publishPaymentCheck(message.withRemainingDelayMillis(tail(delays)), delayMillis);
        return true;
    }

    public void publishClosingFinalizeCheck(String orderNo, Long userId, Long closingDeadlineEpochMilli) {
        List<Long> delays = properties.effectiveClosingFinalizeDelaysMillis();
        long delayMillis = delays.get(0);
        OrderClosingFinalizeCheckMessage message = new OrderClosingFinalizeCheckMessage(
                orderNo,
                userId,
                closingDeadlineEpochMilli,
                tail(delays)
        );
        publishClosingFinalizeCheck(message, delayMillis);
    }

    public boolean publishNextClosingFinalizeCheck(OrderClosingFinalizeCheckMessage message) {
        if (message == null || message.remainingDelayMillis().isEmpty()) {
            return false;
        }
        List<Long> delays = message.remainingDelayMillis();
        long delayMillis = delays.get(0);
        publishClosingFinalizeCheck(message.withRemainingDelayMillis(tail(delays)), delayMillis);
        return true;
    }

    public boolean publishClosingFinalizeCallbackRetry(OrderClosingFinalizeCheckMessage message) {
        if (message == null) {
            return false;
        }
        List<Long> delays = properties.effectiveClosingFinalizeDelaysMillis();
        publishClosingFinalizeCheck(message, delays.get(0));
        return true;
    }

    public void publishClosingFinalize(OrderExpireMessage message) {
        publish(message, properties.getClosingGraceMillis());
    }

    private void publish(OrderExpireMessage message, long ttlMillis) {
        rabbitTemplate.convertAndSend(properties.getExchange(), properties.getDelayRoutingKey(), message, item -> {
            item.getMessageProperties().setExpiration(String.valueOf(Math.max(1L, ttlMillis)));
            return item;
        });
    }

    private void publishPaymentCheck(OrderPaymentExpireCheckMessage message, long delayMillis) {
        rabbitTemplate.convertAndSend(
                properties.getPaymentCheckExchange(),
                properties.getPaymentCheckRoutingKey(),
                message,
                item -> {
                    item.getMessageProperties().setHeader(X_DELAY_HEADER, toDelayHeader(delayMillis));
                    return item;
                }
        );
    }

    private void publishClosingFinalizeCheck(OrderClosingFinalizeCheckMessage message, long delayMillis) {
        rabbitTemplate.convertAndSend(
                properties.getClosingFinalizeExchange(),
                properties.getClosingFinalizeRoutingKey(),
                message,
                item -> {
                    item.getMessageProperties().setHeader(X_DELAY_HEADER, toDelayHeader(delayMillis));
                    return item;
                }
        );
    }

    private List<Long> tail(List<Long> delays) {
        if (delays == null || delays.size() <= 1) {
            return List.of();
        }
        return List.copyOf(delays.subList(1, delays.size()));
    }

    private int toDelayHeader(long delayMillis) {
        long normalized = Math.max(1L, delayMillis);
        return normalized > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) normalized;
    }
}
