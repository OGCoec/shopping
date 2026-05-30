package com.example.ShoppingSystem.coupon.rabbit;

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
@EnableConfigurationProperties(CouponClaimRabbitProperties.class)
public class CouponClaimRabbitConfig {

    @Bean
    public DirectExchange couponClaimExchange(CouponClaimRabbitProperties properties) {
        return new DirectExchange(properties.getExchange(), true, false);
    }

    @Bean
    public Queue couponClaimQueue(CouponClaimRabbitProperties properties) {
        return new Queue(properties.getQueue(), true);
    }

    @Bean
    public Queue couponClaimRetryQueue(CouponClaimRabbitProperties properties) {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("x-dead-letter-exchange", properties.getExchange());
        arguments.put("x-dead-letter-routing-key", properties.getRoutingKey());
        return new Queue(properties.getRetryQueue(), true, false, false, arguments);
    }

    @Bean
    public Queue couponClaimDeadLetterQueue(CouponClaimRabbitProperties properties) {
        return new Queue(properties.getDeadLetterQueue(), true);
    }

    @Bean
    public Binding couponClaimQueueBinding(Queue couponClaimQueue,
                                           DirectExchange couponClaimExchange,
                                           CouponClaimRabbitProperties properties) {
        return BindingBuilder.bind(couponClaimQueue).to(couponClaimExchange).with(properties.getRoutingKey());
    }

    @Bean
    public Binding couponClaimRetryQueueBinding(Queue couponClaimRetryQueue,
                                                DirectExchange couponClaimExchange,
                                                CouponClaimRabbitProperties properties) {
        return BindingBuilder.bind(couponClaimRetryQueue).to(couponClaimExchange).with(properties.getRetryRoutingKey());
    }

    @Bean
    public Binding couponClaimDeadLetterQueueBinding(Queue couponClaimDeadLetterQueue,
                                                     DirectExchange couponClaimExchange,
                                                     CouponClaimRabbitProperties properties) {
        return BindingBuilder.bind(couponClaimDeadLetterQueue).to(couponClaimExchange).with(properties.getDeadRoutingKey());
    }

    @Bean
    public SimpleRabbitListenerContainerFactory couponClaimRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            CouponClaimRabbitProperties properties) {
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
