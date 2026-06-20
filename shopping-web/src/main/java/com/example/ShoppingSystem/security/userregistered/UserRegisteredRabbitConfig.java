package com.example.ShoppingSystem.security.userregistered;

import com.example.ShoppingSystem.outbox.userregistered.UserRegisteredRouting;
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

/**
 * 注册成功 CORE -> RISK 同步队列配置。
 * 消费失败不重新入队，直接进入死信队列，重试由 Inbox FAILED 状态机和 Outbox 重投共同保证。
 */
@Configuration
@EnableRabbit
public class UserRegisteredRabbitConfig {

    @Bean
    public DirectExchange userRegisteredExchange() {
        return new DirectExchange(UserRegisteredRouting.EXCHANGE, true, false);
    }

    @Bean
    public Queue userRegisteredQueue() {
        return QueueBuilder.durable(UserRegisteredRouting.QUEUE)
                .deadLetterExchange(UserRegisteredRouting.EXCHANGE)
                .deadLetterRoutingKey(UserRegisteredRouting.DEAD_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue userRegisteredDeadLetterQueue() {
        return new Queue(UserRegisteredRouting.DEAD_LETTER_QUEUE, true);
    }

    @Bean
    public Binding userRegisteredQueueBinding(Queue userRegisteredQueue,
                                              DirectExchange userRegisteredExchange) {
        return BindingBuilder.bind(userRegisteredQueue)
                .to(userRegisteredExchange)
                .with(UserRegisteredRouting.ROUTING_KEY);
    }

    @Bean
    public Binding userRegisteredDeadLetterQueueBinding(Queue userRegisteredDeadLetterQueue,
                                                        DirectExchange userRegisteredExchange) {
        return BindingBuilder.bind(userRegisteredDeadLetterQueue)
                .to(userRegisteredExchange)
                .with(UserRegisteredRouting.DEAD_ROUTING_KEY);
    }

    @Bean
    public SimpleRabbitListenerContainerFactory userRegisteredRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            SimpleRabbitListenerContainerFactoryConfigurer configurer) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setMessageConverter(new Jackson2JsonMessageConverter());
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}