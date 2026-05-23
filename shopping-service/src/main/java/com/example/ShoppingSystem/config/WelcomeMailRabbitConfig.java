package com.example.ShoppingSystem.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableRabbit
@EnableConfigurationProperties(WelcomeMailRabbitProperties.class)
public class WelcomeMailRabbitConfig {

    @Bean
    public DirectExchange welcomeMailExchange(WelcomeMailRabbitProperties properties) {
        return new DirectExchange(properties.getExchange(), true, false);
    }

    @Bean
    public Queue welcomeMailQueue(WelcomeMailRabbitProperties properties) {
        return new Queue(properties.getQueue(), true);
    }

    @Bean
    public Queue welcomeMailRetryQueue(WelcomeMailRabbitProperties properties) {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("x-dead-letter-exchange", properties.getExchange());
        arguments.put("x-dead-letter-routing-key", properties.getRoutingKey());
        return new Queue(properties.getRetryQueue(), true, false, false, arguments);
    }

    @Bean
    public Queue welcomeMailDeadLetterQueue(WelcomeMailRabbitProperties properties) {
        return new Queue(properties.getDeadLetterQueue(), true);
    }

    @Bean
    public Binding welcomeMailQueueBinding(Queue welcomeMailQueue,
                                           DirectExchange welcomeMailExchange,
                                           WelcomeMailRabbitProperties properties) {
        return BindingBuilder.bind(welcomeMailQueue)
                .to(welcomeMailExchange)
                .with(properties.getRoutingKey());
    }

    @Bean
    public Binding welcomeMailRetryQueueBinding(Queue welcomeMailRetryQueue,
                                                DirectExchange welcomeMailExchange,
                                                WelcomeMailRabbitProperties properties) {
        return BindingBuilder.bind(welcomeMailRetryQueue)
                .to(welcomeMailExchange)
                .with(properties.getRetryRoutingKey());
    }

    @Bean
    public Binding welcomeMailDeadLetterQueueBinding(Queue welcomeMailDeadLetterQueue,
                                                     DirectExchange welcomeMailExchange,
                                                     WelcomeMailRabbitProperties properties) {
        return BindingBuilder.bind(welcomeMailDeadLetterQueue)
                .to(welcomeMailExchange)
                .with(properties.getDeadRoutingKey());
    }

    @Bean
    public SimpleRabbitListenerContainerFactory welcomeMailRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            @Qualifier("registerEmailCodeMessageConverter") MessageConverter messageConverter,
            WelcomeMailRabbitProperties properties) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setConcurrentConsumers(properties.getConcurrency());
        factory.setMaxConcurrentConsumers(properties.getMaxConcurrency());
        factory.setPrefetchCount(properties.getPrefetch());
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
