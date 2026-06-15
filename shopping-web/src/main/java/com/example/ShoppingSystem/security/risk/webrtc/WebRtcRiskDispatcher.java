package com.example.ShoppingSystem.security.risk.webrtc;

import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class WebRtcRiskDispatcher {

    private final RabbitTemplate rabbitTemplate;
    private final WebRtcRiskRabbitProperties properties;

    public WebRtcRiskDispatcher(RabbitTemplate rabbitTemplate,
                                WebRtcRiskRabbitProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    public void dispatch(WebRtcRiskMessage message) {
        rabbitTemplate.convertAndSend(properties.getExchange(), properties.getRoutingKey(), message);
    }

    public void publishRetry(WebRtcRiskMessage message, long delayMillis) {
        MessagePostProcessor delayProcessor = rabbitMessage -> {
            rabbitMessage.getMessageProperties().setExpiration(String.valueOf(delayMillis));
            return rabbitMessage;
        };
        rabbitTemplate.convertAndSend(properties.getExchange(), properties.getRetryRoutingKey(), message, delayProcessor);
    }

    public void publishDeadLetter(WebRtcRiskMessage message) {
        rabbitTemplate.convertAndSend(properties.getExchange(), properties.getDeadRoutingKey(), message);
    }
}
