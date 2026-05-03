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
@EnableConfigurationProperties({
        SmsCodeRabbitProperties.class,
        SmsCodeProperties.class
})
public class SmsCodeRabbitConfig {

    @Bean
    public DirectExchange smsCodeExchange(SmsCodeRabbitProperties properties) {
        return new DirectExchange(properties.getExchange(), true, false);
    }

    @Bean
    public Queue smsCodeQueue(SmsCodeRabbitProperties properties) {
        return new Queue(properties.getQueue(), true);
    }

    @Bean
    public Queue smsCodeRetryQueue(SmsCodeRabbitProperties properties) {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("x-dead-letter-exchange", properties.getExchange());
        arguments.put("x-dead-letter-routing-key", properties.getRoutingKey());
        return new Queue(properties.getRetryQueue(), true, false, false, arguments);
    }

    @Bean
    public Queue smsCodeDeadLetterQueue(SmsCodeRabbitProperties properties) {
        return new Queue(properties.getDeadLetterQueue(), true);
    }

    @Bean
    public Binding smsCodeQueueBinding(Queue smsCodeQueue,
                                       DirectExchange smsCodeExchange,
                                       SmsCodeRabbitProperties properties) {
        return BindingBuilder.bind(smsCodeQueue)
                .to(smsCodeExchange)
                .with(properties.getRoutingKey());
    }

    @Bean
    public Binding smsCodeRetryQueueBinding(Queue smsCodeRetryQueue,
                                            DirectExchange smsCodeExchange,
                                            SmsCodeRabbitProperties properties) {
        return BindingBuilder.bind(smsCodeRetryQueue)
                .to(smsCodeExchange)
                .with(properties.getRetryRoutingKey());
    }

    @Bean
    public Binding smsCodeDeadLetterQueueBinding(Queue smsCodeDeadLetterQueue,
                                                 DirectExchange smsCodeExchange,
                                                 SmsCodeRabbitProperties properties) {
        return BindingBuilder.bind(smsCodeDeadLetterQueue)
                .to(smsCodeExchange)
                .with(properties.getDeadRoutingKey());
    }

    @Bean
    public SimpleRabbitListenerContainerFactory smsCodeRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            @Qualifier("registerEmailCodeMessageConverter") MessageConverter messageConverter,
            SmsCodeRabbitProperties properties) {
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
