package com.example.ShoppingSystem.security.accountrecovery;

import com.example.ShoppingSystem.outbox.accountrecovery.AccountRiskRecoveryStartedRouting;
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
public class AccountRiskRecoveryStartedRabbitConfig {

    @Bean
    public DirectExchange accountRiskRecoveryStartedExchange() {
        return new DirectExchange(AccountRiskRecoveryStartedRouting.EXCHANGE, true, false);
    }

    @Bean
    public Queue accountRiskRecoveryStartedQueue() {
        return QueueBuilder.durable(AccountRiskRecoveryStartedRouting.QUEUE)
                .deadLetterExchange(AccountRiskRecoveryStartedRouting.EXCHANGE)
                .deadLetterRoutingKey(AccountRiskRecoveryStartedRouting.DEAD_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue accountRiskRecoveryStartedDeadLetterQueue() {
        return new Queue(AccountRiskRecoveryStartedRouting.DEAD_LETTER_QUEUE, true);
    }

    @Bean
    public Binding accountRiskRecoveryStartedQueueBinding(Queue accountRiskRecoveryStartedQueue,
                                                          DirectExchange accountRiskRecoveryStartedExchange) {
        return BindingBuilder.bind(accountRiskRecoveryStartedQueue)
                .to(accountRiskRecoveryStartedExchange)
                .with(AccountRiskRecoveryStartedRouting.ROUTING_KEY);
    }

    @Bean
    public Binding accountRiskRecoveryStartedDeadLetterQueueBinding(Queue accountRiskRecoveryStartedDeadLetterQueue,
                                                                    DirectExchange accountRiskRecoveryStartedExchange) {
        return BindingBuilder.bind(accountRiskRecoveryStartedDeadLetterQueue)
                .to(accountRiskRecoveryStartedExchange)
                .with(AccountRiskRecoveryStartedRouting.DEAD_ROUTING_KEY);
    }

    @Bean
    public SimpleRabbitListenerContainerFactory accountRiskRecoveryStartedRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            SimpleRabbitListenerContainerFactoryConfigurer configurer) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setMessageConverter(new Jackson2JsonMessageConverter());
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}