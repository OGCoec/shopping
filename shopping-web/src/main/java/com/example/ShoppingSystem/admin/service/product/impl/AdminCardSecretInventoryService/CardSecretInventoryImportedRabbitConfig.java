package com.example.ShoppingSystem.admin.service.product.impl.AdminCardSecretInventoryService;

import com.example.ShoppingSystem.outbox.cardsecretinventory.CardSecretInventoryImportedRouting;
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
public class CardSecretInventoryImportedRabbitConfig {

    @Bean
    public DirectExchange cardSecretInventoryImportedExchange() {
        return new DirectExchange(CardSecretInventoryImportedRouting.EXCHANGE, true, false);
    }

    @Bean
    public Queue cardSecretInventoryImportedProductQueue() {
        return QueueBuilder.durable(CardSecretInventoryImportedRouting.QUEUE)
                .deadLetterExchange(CardSecretInventoryImportedRouting.EXCHANGE)
                .deadLetterRoutingKey(CardSecretInventoryImportedRouting.DEAD_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue cardSecretInventoryImportedProductDeadLetterQueue() {
        return new Queue(CardSecretInventoryImportedRouting.DEAD_LETTER_QUEUE, true);
    }

    @Bean
    public Binding cardSecretInventoryImportedProductQueueBinding(Queue cardSecretInventoryImportedProductQueue,
                                                                  DirectExchange cardSecretInventoryImportedExchange) {
        return BindingBuilder.bind(cardSecretInventoryImportedProductQueue)
                .to(cardSecretInventoryImportedExchange)
                .with(CardSecretInventoryImportedRouting.ROUTING_KEY);
    }

    @Bean
    public Binding cardSecretInventoryImportedProductDeadLetterQueueBinding(
            Queue cardSecretInventoryImportedProductDeadLetterQueue,
            DirectExchange cardSecretInventoryImportedExchange) {
        return BindingBuilder.bind(cardSecretInventoryImportedProductDeadLetterQueue)
                .to(cardSecretInventoryImportedExchange)
                .with(CardSecretInventoryImportedRouting.DEAD_ROUTING_KEY);
    }

    @Bean
    public SimpleRabbitListenerContainerFactory cardSecretInventoryImportedRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            SimpleRabbitListenerContainerFactoryConfigurer configurer) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setMessageConverter(new Jackson2JsonMessageConverter());
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
