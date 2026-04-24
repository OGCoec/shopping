package com.example.ShoppingSystem.quota.writeback;

import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/**
 * RabbitMQ dispatcher implementation for IP risk writeback commands.
 */
@Service
public class RabbitIpRiskWritebackDispatcher implements IpRiskWritebackDispatcher {

    private final RabbitTemplate rabbitTemplate;
    private final IpRiskWritebackRabbitProperties rabbitProperties;

    public RabbitIpRiskWritebackDispatcher(RabbitTemplate rabbitTemplate,
                                           IpRiskWritebackRabbitProperties rabbitProperties) {
        this.rabbitTemplate = rabbitTemplate;
        this.rabbitProperties = rabbitProperties;
    }

    @Override
    public void dispatch(IpRiskWritebackCommand command) {
        rabbitTemplate.convertAndSend(
                rabbitProperties.getExchange(),
                rabbitProperties.getRoutingKey(),
                command
        );
    }

    @Override
    public void publishRetry(IpRiskWritebackCommand command, long delayMilli) {
        MessagePostProcessor delayProcessor = rabbitMessage -> {
            rabbitMessage.getMessageProperties().setExpiration(String.valueOf(delayMilli));
            return rabbitMessage;
        };
        rabbitTemplate.convertAndSend(
                rabbitProperties.getExchange(),
                rabbitProperties.getRetryRoutingKey(),
                command,
                delayProcessor
        );
    }

    @Override
    public void publishDeadLetter(IpRiskWritebackCommand command) {
        rabbitTemplate.convertAndSend(
                rabbitProperties.getExchange(),
                rabbitProperties.getDeadRoutingKey(),
                command
        );
    }
}
