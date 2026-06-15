package com.example.ShoppingSystem.order.rabbit;

import com.example.ShoppingSystem.order.service.PaymentCallbackDispatchProperties;
import com.example.ShoppingSystem.order.service.PaymentCallbackStreamProperties;
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
@EnableConfigurationProperties({
        PaymentCallbackRabbitProperties.class,
        PaymentCallbackDispatchProperties.class,
        PaymentCallbackStreamProperties.class
})
public class PaymentCallbackRabbitConfig {

    @Bean
    public DirectExchange paymentCallbackExchange(PaymentCallbackRabbitProperties properties) {
        return new DirectExchange(properties.getExchange(), true, false);
    }

    @Bean
    public Queue paymentCallbackDispatchQueue(PaymentCallbackRabbitProperties properties) {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("x-dead-letter-exchange", properties.getExchange());
        arguments.put("x-dead-letter-routing-key", properties.getDeadRoutingKey());
        return new Queue(properties.getQueue(), true, false, false, arguments);
    }

    @Bean
    public Queue paymentCallbackDeadLetterQueue(PaymentCallbackRabbitProperties properties) {
        return new Queue(properties.getDeadLetterQueue(), true);
    }

    @Bean
    public Binding paymentCallbackDispatchBinding(Queue paymentCallbackDispatchQueue,
                                                  DirectExchange paymentCallbackExchange,
                                                  PaymentCallbackRabbitProperties properties) {
        return BindingBuilder.bind(paymentCallbackDispatchQueue)
                .to(paymentCallbackExchange)
                .with(properties.getRoutingKey());
    }

    @Bean
    public Binding paymentCallbackDeadLetterBinding(Queue paymentCallbackDeadLetterQueue,
                                                    DirectExchange paymentCallbackExchange,
                                                    PaymentCallbackRabbitProperties properties) {
        return BindingBuilder.bind(paymentCallbackDeadLetterQueue)
                .to(paymentCallbackExchange)
                .with(properties.getDeadRoutingKey());
    }

    @Bean
    public SimpleRabbitListenerContainerFactory paymentCallbackRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            PaymentCallbackRabbitProperties properties) {
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
