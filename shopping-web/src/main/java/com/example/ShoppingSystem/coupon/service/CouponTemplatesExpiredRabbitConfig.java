package com.example.ShoppingSystem.coupon.service;

import com.example.ShoppingSystem.outbox.couponexpire.CouponTemplatesExpiredRouting;
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
public class CouponTemplatesExpiredRabbitConfig {

    @Bean
    public DirectExchange couponTemplatesExpiredExchange() {
        return new DirectExchange(CouponTemplatesExpiredRouting.EXCHANGE, true, false);
    }

    @Bean
    public Queue couponTemplatesExpiredTradeQueue() {
        return QueueBuilder.durable(CouponTemplatesExpiredRouting.QUEUE)
                .deadLetterExchange(CouponTemplatesExpiredRouting.EXCHANGE)
                .deadLetterRoutingKey(CouponTemplatesExpiredRouting.DEAD_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue couponTemplatesExpiredTradeDeadLetterQueue() {
        return new Queue(CouponTemplatesExpiredRouting.DEAD_LETTER_QUEUE, true);
    }

    @Bean
    public Binding couponTemplatesExpiredTradeQueueBinding(Queue couponTemplatesExpiredTradeQueue,
                                                           DirectExchange couponTemplatesExpiredExchange) {
        return BindingBuilder.bind(couponTemplatesExpiredTradeQueue)
                .to(couponTemplatesExpiredExchange)
                .with(CouponTemplatesExpiredRouting.ROUTING_KEY);
    }

    @Bean
    public Binding couponTemplatesExpiredTradeDeadLetterQueueBinding(Queue couponTemplatesExpiredTradeDeadLetterQueue,
                                                                     DirectExchange couponTemplatesExpiredExchange) {
        return BindingBuilder.bind(couponTemplatesExpiredTradeDeadLetterQueue)
                .to(couponTemplatesExpiredExchange)
                .with(CouponTemplatesExpiredRouting.DEAD_ROUTING_KEY);
    }

    @Bean
    public SimpleRabbitListenerContainerFactory couponTemplatesExpiredRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            SimpleRabbitListenerContainerFactoryConfigurer configurer) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setMessageConverter(new Jackson2JsonMessageConverter());
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
