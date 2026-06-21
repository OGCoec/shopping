package com.example.ShoppingSystem.order.rabbit;

import com.example.ShoppingSystem.outbox.orderinventory.OrderInventoryReleaseRequestedRouting;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableRabbit
public class OrderInventoryReleaseRequestedRabbitConfig {

    @Bean
    public DirectExchange orderInventoryReleaseRequestedExchange() {
        return new DirectExchange(OrderInventoryReleaseRequestedRouting.EXCHANGE, true, false);
    }

    @Bean
    public Queue orderInventoryReleaseRequestedProductQueue() {
        return QueueBuilder.durable(OrderInventoryReleaseRequestedRouting.QUEUE)
                .deadLetterExchange(OrderInventoryReleaseRequestedRouting.EXCHANGE)
                .deadLetterRoutingKey(OrderInventoryReleaseRequestedRouting.DEAD_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue orderInventoryReleaseRequestedProductDeadLetterQueue() {
        return new Queue(OrderInventoryReleaseRequestedRouting.DEAD_LETTER_QUEUE, true);
    }

    @Bean
    public Binding orderInventoryReleaseRequestedProductQueueBinding(Queue orderInventoryReleaseRequestedProductQueue,
                                                                     DirectExchange orderInventoryReleaseRequestedExchange) {
        return BindingBuilder.bind(orderInventoryReleaseRequestedProductQueue)
                .to(orderInventoryReleaseRequestedExchange)
                .with(OrderInventoryReleaseRequestedRouting.ROUTING_KEY);
    }

    @Bean
    public Binding orderInventoryReleaseRequestedProductDeadLetterQueueBinding(
            Queue orderInventoryReleaseRequestedProductDeadLetterQueue,
            DirectExchange orderInventoryReleaseRequestedExchange) {
        return BindingBuilder.bind(orderInventoryReleaseRequestedProductDeadLetterQueue)
                .to(orderInventoryReleaseRequestedExchange)
                .with(OrderInventoryReleaseRequestedRouting.DEAD_ROUTING_KEY);
    }

    @Bean
    public SimpleRabbitListenerContainerFactory orderInventoryReleaseRequestedRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            SimpleRabbitListenerContainerFactoryConfigurer configurer) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setMessageConverter(new Jackson2JsonMessageConverter());
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
