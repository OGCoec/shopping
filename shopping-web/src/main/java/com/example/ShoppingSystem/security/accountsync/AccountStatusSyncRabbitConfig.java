package com.example.ShoppingSystem.security.accountsync;

import com.example.ShoppingSystem.outbox.accountsync.AccountStatusSyncRouting;
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
 * RISK -> CORE 账号状态同步队列配置。
 * 消费失败不重新入队，直接进入死信队列，重试由 Inbox FAILED 状态机和 Outbox 重投共同保证。
 */
@Configuration
@EnableRabbit
public class AccountStatusSyncRabbitConfig {

    @Bean
    public DirectExchange accountStatusSyncExchange() {
        return new DirectExchange(AccountStatusSyncRouting.EXCHANGE, true, false);
    }

    @Bean
    public Queue accountStatusSyncQueue() {
        return QueueBuilder.durable(AccountStatusSyncRouting.QUEUE)
                .deadLetterExchange(AccountStatusSyncRouting.EXCHANGE)
                .deadLetterRoutingKey(AccountStatusSyncRouting.DEAD_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue accountStatusSyncDeadLetterQueue() {
        return new Queue(AccountStatusSyncRouting.DEAD_LETTER_QUEUE, true);
    }

    @Bean
    public Binding accountStatusSyncQueueBinding(Queue accountStatusSyncQueue,
                                                 DirectExchange accountStatusSyncExchange) {
        return BindingBuilder.bind(accountStatusSyncQueue)
                .to(accountStatusSyncExchange)
                .with(AccountStatusSyncRouting.ROUTING_KEY);
    }

    @Bean
    public Binding accountStatusSyncDeadLetterQueueBinding(Queue accountStatusSyncDeadLetterQueue,
                                                           DirectExchange accountStatusSyncExchange) {
        return BindingBuilder.bind(accountStatusSyncDeadLetterQueue)
                .to(accountStatusSyncExchange)
                .with(AccountStatusSyncRouting.DEAD_ROUTING_KEY);
    }

    @Bean
    public SimpleRabbitListenerContainerFactory accountStatusSyncRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            SimpleRabbitListenerContainerFactoryConfigurer configurer) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setMessageConverter(new Jackson2JsonMessageConverter());
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}