package com.example.ShoppingSystem.order.rabbit;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.CustomExchange;
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
@EnableConfigurationProperties(OrderExpireRabbitProperties.class)
public class OrderExpireRabbitConfig {

    @Bean
    public DirectExchange orderExpireExchange(OrderExpireRabbitProperties properties) {
        return new DirectExchange(properties.getExchange(), true, false);
    }

    @Bean
    public CustomExchange orderPaymentCheckExchange(OrderExpireRabbitProperties properties) {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("x-delayed-type", "direct");
        return new CustomExchange(
                properties.getPaymentCheckExchange(),
                "x-delayed-message",
                true,
                false,
                arguments
        );
    }

    @Bean
    public CustomExchange orderClosingFinalizeExchange(OrderExpireRabbitProperties properties) {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("x-delayed-type", "direct");
        return new CustomExchange(
                properties.getClosingFinalizeExchange(),
                "x-delayed-message",
                true,
                false,
                arguments
        );
    }

    @Bean
    public Queue orderExpireDelayQueue(OrderExpireRabbitProperties properties) {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("x-dead-letter-exchange", properties.getExchange());
        arguments.put("x-dead-letter-routing-key", properties.getDeadRoutingKey());
        return new Queue(properties.getDelayQueue(), true, false, false, arguments);
    }

    @Bean
    public Queue orderExpireDeadLetterQueue(OrderExpireRabbitProperties properties) {
        return new Queue(properties.getDeadLetterQueue(), true);
    }

    @Bean
    public Queue orderPaymentCheckQueue(OrderExpireRabbitProperties properties) {
        return new Queue(properties.getPaymentCheckQueue(), true);
    }

    @Bean
    public Queue orderClosingFinalizeQueue(OrderExpireRabbitProperties properties) {
        return new Queue(properties.getClosingFinalizeQueue(), true);
    }

    @Bean
    public Binding orderExpireDelayQueueBinding(Queue orderExpireDelayQueue,
                                                DirectExchange orderExpireExchange,
                                                OrderExpireRabbitProperties properties) {
        return BindingBuilder.bind(orderExpireDelayQueue).to(orderExpireExchange).with(properties.getDelayRoutingKey());
    }

    @Bean
    public Binding orderExpireDeadLetterQueueBinding(Queue orderExpireDeadLetterQueue,
                                                     DirectExchange orderExpireExchange,
                                                     OrderExpireRabbitProperties properties) {
        return BindingBuilder.bind(orderExpireDeadLetterQueue).to(orderExpireExchange).with(properties.getDeadRoutingKey());
    }

    @Bean
    public Binding orderPaymentCheckQueueBinding(Queue orderPaymentCheckQueue,
                                                  CustomExchange orderPaymentCheckExchange,
                                                  OrderExpireRabbitProperties properties) {
        return BindingBuilder.bind(orderPaymentCheckQueue)
                .to(orderPaymentCheckExchange)
                .with(properties.getPaymentCheckRoutingKey())
                .noargs();
    }

    @Bean
    public Binding orderClosingFinalizeQueueBinding(Queue orderClosingFinalizeQueue,
                                                    CustomExchange orderClosingFinalizeExchange,
                                                    OrderExpireRabbitProperties properties) {
        return BindingBuilder.bind(orderClosingFinalizeQueue)
                .to(orderClosingFinalizeExchange)
                .with(properties.getClosingFinalizeRoutingKey())
                .noargs();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory orderExpireRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            OrderExpireRabbitProperties properties) {
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
