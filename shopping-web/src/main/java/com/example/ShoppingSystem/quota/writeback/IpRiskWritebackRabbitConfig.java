package com.example.ShoppingSystem.quota.writeback;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * RabbitMQ infrastructure for IP risk writeback async pipeline.
 */
@Configuration
@EnableRabbit
@EnableConfigurationProperties({
        IpRiskWritebackRabbitProperties.class,
        IpRiskWritebackProperties.class
})
public class IpRiskWritebackRabbitConfig {

    @Bean
    public DirectExchange ipRiskWritebackExchange(IpRiskWritebackRabbitProperties properties) {
        return new DirectExchange(properties.getExchange(), true, false);
    }

    @Bean
    public Queue ipRiskWritebackQueue(IpRiskWritebackRabbitProperties properties) {
        return new Queue(properties.getQueue(), true);
    }

    @Bean
    public Queue ipRiskWritebackRetryQueue(IpRiskWritebackRabbitProperties properties) {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("x-dead-letter-exchange", properties.getExchange());
        arguments.put("x-dead-letter-routing-key", properties.getRoutingKey());
        return new Queue(properties.getRetryQueue(), true, false, false, arguments);
    }

    @Bean
    public Queue ipRiskWritebackDeadLetterQueue(IpRiskWritebackRabbitProperties properties) {
        return new Queue(properties.getDeadLetterQueue(), true);
    }

    @Bean
    public Binding ipRiskWritebackQueueBinding(Queue ipRiskWritebackQueue,
                                               DirectExchange ipRiskWritebackExchange,
                                               IpRiskWritebackRabbitProperties properties) {
        return BindingBuilder.bind(ipRiskWritebackQueue)
                .to(ipRiskWritebackExchange)
                .with(properties.getRoutingKey());
    }

    @Bean
    public Binding ipRiskWritebackRetryQueueBinding(Queue ipRiskWritebackRetryQueue,
                                                    DirectExchange ipRiskWritebackExchange,
                                                    IpRiskWritebackRabbitProperties properties) {
        return BindingBuilder.bind(ipRiskWritebackRetryQueue)
                .to(ipRiskWritebackExchange)
                .with(properties.getRetryRoutingKey());
    }

    @Bean
    public Binding ipRiskWritebackDeadLetterQueueBinding(Queue ipRiskWritebackDeadLetterQueue,
                                                         DirectExchange ipRiskWritebackExchange,
                                                         IpRiskWritebackRabbitProperties properties) {
        return BindingBuilder.bind(ipRiskWritebackDeadLetterQueue)
                .to(ipRiskWritebackExchange)
                .with(properties.getDeadRoutingKey());
    }

    @Bean
    public SimpleRabbitListenerContainerFactory ipRiskWritebackRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            IpRiskWritebackRabbitProperties properties) {
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
