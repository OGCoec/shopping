package com.example.ShoppingSystem.order.rabbit;

import com.example.ShoppingSystem.outbox.orderstock.OrderStockDeductRequestedRouting;
import com.example.ShoppingSystem.outbox.orderstock.OrderStockDeductResultRouting;
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
public class OrderStockDeductRabbitConfig {

    @Bean
    public DirectExchange orderStockDeductRequestedExchange() {
        return new DirectExchange(OrderStockDeductRequestedRouting.EXCHANGE, true, false);
    }

    @Bean
    public Queue orderStockDeductRequestedProductQueue() {
        return QueueBuilder.durable(OrderStockDeductRequestedRouting.QUEUE)
                .deadLetterExchange(OrderStockDeductRequestedRouting.EXCHANGE)
                .deadLetterRoutingKey(OrderStockDeductRequestedRouting.DEAD_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue orderStockDeductRequestedProductDeadLetterQueue() {
        return new Queue(OrderStockDeductRequestedRouting.DEAD_LETTER_QUEUE, true);
    }

    @Bean
    public Binding orderStockDeductRequestedProductQueueBinding(Queue orderStockDeductRequestedProductQueue,
                                                                DirectExchange orderStockDeductRequestedExchange) {
        return BindingBuilder.bind(orderStockDeductRequestedProductQueue)
                .to(orderStockDeductRequestedExchange)
                .with(OrderStockDeductRequestedRouting.ROUTING_KEY);
    }

    @Bean
    public Binding orderStockDeductRequestedProductDeadLetterQueueBinding(
            Queue orderStockDeductRequestedProductDeadLetterQueue,
            DirectExchange orderStockDeductRequestedExchange) {
        return BindingBuilder.bind(orderStockDeductRequestedProductDeadLetterQueue)
                .to(orderStockDeductRequestedExchange)
                .with(OrderStockDeductRequestedRouting.DEAD_ROUTING_KEY);
    }

    @Bean
    public SimpleRabbitListenerContainerFactory orderStockDeductRequestedRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            SimpleRabbitListenerContainerFactoryConfigurer configurer) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setMessageConverter(new Jackson2JsonMessageConverter());
        factory.setDefaultRequeueRejected(false);
        return factory;
    }

    @Bean
    public DirectExchange orderStockDeductResultExchange() {
        return new DirectExchange(OrderStockDeductResultRouting.EXCHANGE, true, false);
    }

    @Bean
    public Queue orderStockDeductResultTradeQueue() {
        return QueueBuilder.durable(OrderStockDeductResultRouting.QUEUE)
                .deadLetterExchange(OrderStockDeductResultRouting.EXCHANGE)
                .deadLetterRoutingKey(OrderStockDeductResultRouting.DEAD_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue orderStockDeductResultTradeDeadLetterQueue() {
        return new Queue(OrderStockDeductResultRouting.DEAD_LETTER_QUEUE, true);
    }

    @Bean
    public Binding orderStockDeductResultTradeQueueBinding(Queue orderStockDeductResultTradeQueue,
                                                           DirectExchange orderStockDeductResultExchange) {
        return BindingBuilder.bind(orderStockDeductResultTradeQueue)
                .to(orderStockDeductResultExchange)
                .with(OrderStockDeductResultRouting.ROUTING_KEY);
    }

    @Bean
    public Binding orderStockDeductResultTradeDeadLetterQueueBinding(
            Queue orderStockDeductResultTradeDeadLetterQueue,
            DirectExchange orderStockDeductResultExchange) {
        return BindingBuilder.bind(orderStockDeductResultTradeDeadLetterQueue)
                .to(orderStockDeductResultExchange)
                .with(OrderStockDeductResultRouting.DEAD_ROUTING_KEY);
    }

    @Bean
    public SimpleRabbitListenerContainerFactory orderStockDeductResultRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            SimpleRabbitListenerContainerFactoryConfigurer configurer) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setMessageConverter(new Jackson2JsonMessageConverter());
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}