package com.example.ShoppingSystem.security.passwordresetsync;

import com.example.ShoppingSystem.outbox.passwordresetsync.PasswordResetSucceededRouting;
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
 * 找回密码成功 CORE -> RISK 同步队列配置。
 * 消费失败不重新入队，直接进入死信队列，重试由 Inbox FAILED 状态机和 Outbox 重投共同保证。
 */
@Configuration
@EnableRabbit
public class PasswordResetSucceededRabbitConfig {

    @Bean
    public DirectExchange passwordResetSucceededExchange() {
        return new DirectExchange(PasswordResetSucceededRouting.EXCHANGE, true, false);
    }

    @Bean
    public Queue passwordResetSucceededQueue() {
        return QueueBuilder.durable(PasswordResetSucceededRouting.QUEUE)
                .deadLetterExchange(PasswordResetSucceededRouting.EXCHANGE)
                .deadLetterRoutingKey(PasswordResetSucceededRouting.DEAD_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue passwordResetSucceededDeadLetterQueue() {
        return new Queue(PasswordResetSucceededRouting.DEAD_LETTER_QUEUE, true);
    }

    @Bean
    public Binding passwordResetSucceededQueueBinding(Queue passwordResetSucceededQueue,
                                                      DirectExchange passwordResetSucceededExchange) {
        return BindingBuilder.bind(passwordResetSucceededQueue)
                .to(passwordResetSucceededExchange)
                .with(PasswordResetSucceededRouting.ROUTING_KEY);
    }

    @Bean
    public Binding passwordResetSucceededDeadLetterQueueBinding(Queue passwordResetSucceededDeadLetterQueue,
                                                                DirectExchange passwordResetSucceededExchange) {
        return BindingBuilder.bind(passwordResetSucceededDeadLetterQueue)
                .to(passwordResetSucceededExchange)
                .with(PasswordResetSucceededRouting.DEAD_ROUTING_KEY);
    }

    @Bean
    public SimpleRabbitListenerContainerFactory passwordResetSucceededRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            SimpleRabbitListenerContainerFactoryConfigurer configurer) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setMessageConverter(new Jackson2JsonMessageConverter());
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}