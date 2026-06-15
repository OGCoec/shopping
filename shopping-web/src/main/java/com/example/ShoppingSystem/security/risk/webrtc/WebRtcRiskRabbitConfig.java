package com.example.ShoppingSystem.security.risk.webrtc;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableRabbit
@EnableConfigurationProperties({
        WebRtcRiskRabbitProperties.class,
        WebRtcRiskProperties.class
})
public class WebRtcRiskRabbitConfig {

    @Bean
    public DirectExchange webRtcRiskExchange(WebRtcRiskRabbitProperties properties) {
        return new DirectExchange(properties.getExchange(), true, false);
    }

    @Bean
    public Queue webRtcRiskQueue(WebRtcRiskRabbitProperties properties) {
        return new Queue(properties.getQueue(), true);
    }

    @Bean
    public Queue webRtcRiskRetryQueue(WebRtcRiskRabbitProperties properties) {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("x-dead-letter-exchange", properties.getExchange());
        arguments.put("x-dead-letter-routing-key", properties.getRoutingKey());
        return new Queue(properties.getRetryQueue(), true, false, false, arguments);
    }

    @Bean
    public Queue webRtcRiskDeadLetterQueue(WebRtcRiskRabbitProperties properties) {
        return new Queue(properties.getDeadLetterQueue(), true);
    }

    @Bean
    public Binding webRtcRiskQueueBinding(Queue webRtcRiskQueue,
                                          DirectExchange webRtcRiskExchange,
                                          WebRtcRiskRabbitProperties properties) {
        return BindingBuilder.bind(webRtcRiskQueue).to(webRtcRiskExchange).with(properties.getRoutingKey());
    }

    @Bean
    public Binding webRtcRiskRetryQueueBinding(Queue webRtcRiskRetryQueue,
                                               DirectExchange webRtcRiskExchange,
                                               WebRtcRiskRabbitProperties properties) {
        return BindingBuilder.bind(webRtcRiskRetryQueue).to(webRtcRiskExchange).with(properties.getRetryRoutingKey());
    }

    @Bean
    public Binding webRtcRiskDeadLetterQueueBinding(Queue webRtcRiskDeadLetterQueue,
                                                    DirectExchange webRtcRiskExchange,
                                                    WebRtcRiskRabbitProperties properties) {
        return BindingBuilder.bind(webRtcRiskDeadLetterQueue).to(webRtcRiskExchange).with(properties.getDeadRoutingKey());
    }

    @Bean
    public SimpleRabbitListenerContainerFactory webRtcRiskRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            WebRtcRiskRabbitProperties properties) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setMessageConverter(new Jackson2JsonMessageConverter());
        factory.setConcurrentConsumers(properties.getConcurrency());
        factory.setMaxConcurrentConsumers(properties.getMaxConcurrency());
        factory.setPrefetchCount(properties.getPrefetch());
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
