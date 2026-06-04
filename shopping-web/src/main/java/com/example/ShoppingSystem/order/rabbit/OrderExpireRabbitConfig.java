package com.example.ShoppingSystem.order.rabbit;

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
@EnableConfigurationProperties(OrderExpireRabbitProperties.class)
public class OrderExpireRabbitConfig {

    @Bean
    public DirectExchange orderExpireExchange(OrderExpireRabbitProperties properties) {
        return new DirectExchange(properties.getExchange(), true, false);
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
