package com.example.ShoppingSystem.order.rabbit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.Objects;

@Configuration
public class OrderRabbitPublishConfirmConfig {

    private static final Logger log = LoggerFactory.getLogger(OrderRabbitPublishConfirmConfig.class);

    public OrderRabbitPublishConfirmConfig(RabbitTemplate rabbitTemplate) {
        rabbitTemplate.setConfirmCallback(this::handleConfirm);
        rabbitTemplate.setReturnsCallback(this::handleReturned);
    }

    private void handleConfirm(CorrelationData correlationData, boolean ack, String cause) {
        if (!(correlationData instanceof OrderRabbitCorrelationData data)) {
            return;
        }
        if (ack) {
            log.debug(
                    "[OrderRabbit] publish confirmed, phase={}, orderNo={}, exchange={}, routingKey={}, delayMillis={}, closingDeadlineEpochMilli={}, correlationId={}",
                    data.phase(),
                    data.orderNo(),
                    data.exchange(),
                    data.routingKey(),
                    data.delayMillis(),
                    data.closingDeadlineEpochMilli(),
                    data.getId()
            );
            return;
        }
        log.warn(
                "[OrderRabbit] publish nacked, phase={}, orderNo={}, exchange={}, routingKey={}, delayMillis={}, closingDeadlineEpochMilli={}, correlationId={}, cause={}",
                data.phase(),
                data.orderNo(),
                data.exchange(),
                data.routingKey(),
                data.delayMillis(),
                data.closingDeadlineEpochMilli(),
                data.getId(),
                cause
        );
    }

    private void handleReturned(ReturnedMessage returned) {
        Message message = returned.getMessage();
        Map<String, Object> headers = message == null ? Map.of() : message.getMessageProperties().getHeaders();
        String phase = headerText(headers, OrderRabbitCorrelationData.HEADER_PHASE);
        if (!Objects.equals(OrderRabbitCorrelationData.PHASE_CLOSING_FINALIZE, phase)) {
            return;
        }
        log.warn(
                "[OrderRabbit] publish returned, phase={}, orderNo={}, exchange={}, routingKey={}, replyCode={}, replyText={}, correlationId={}, delayMillis={}, closingDeadlineEpochMilli={}",
                phase,
                headerText(headers, OrderRabbitCorrelationData.HEADER_ORDER_NO),
                returned.getExchange(),
                returned.getRoutingKey(),
                returned.getReplyCode(),
                returned.getReplyText(),
                headerText(headers, OrderRabbitCorrelationData.HEADER_CORRELATION_ID),
                headerLong(headers, OrderRabbitCorrelationData.HEADER_DELAY_MILLIS),
                headerLong(headers, OrderRabbitCorrelationData.HEADER_CLOSING_DEADLINE_EPOCH_MILLI)
        );
    }

    private String headerText(Map<String, Object> headers, String key) {
        Object value = headers == null ? null : headers.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private Long headerLong(Map<String, Object> headers, String key) {
        Object value = headers == null ? null : headers.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = value == null ? "" : String.valueOf(value).trim();
        if (text.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
