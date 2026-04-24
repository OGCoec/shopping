package com.example.ShoppingSystem.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * 注册邮箱验证码 RabbitMQ 基础配置。
 * 负责声明交换机、正常队列、重试队列、死信队列，以及消费者并发参数和 JSON 消息转换器。
 */
@Configuration
@EnableRabbit
@EnableConfigurationProperties(RegisterEmailCodeRabbitProperties.class)
public class RegisterEmailCodeRabbitConfig {

    /**
     * 声明注册邮箱验证码业务交换机。
     * 主队列、重试队列和死信队列统一挂在同一个 direct exchange 上，按不同 routingKey 分流。
     *
     * @param properties 注册邮箱验证码 RabbitMQ 配置
     * @return 供注册邮箱验证码链路使用的 direct exchange
     */
    @Bean
    public DirectExchange registerEmailCodeExchange(RegisterEmailCodeRabbitProperties properties) {
        return new DirectExchange(properties.getExchange(), true, false);
    }

    /**
     * 声明注册邮箱验证码主消费队列。
     * 正常的发信任务都会先进入该队列，由监听器按 2-10 的并发范围消费。
     *
     * @param properties 注册邮箱验证码 RabbitMQ 配置
     * @return 正常发信任务队列
     */
    @Bean
    public Queue registerEmailCodeQueue(RegisterEmailCodeRabbitProperties properties) {
        return new Queue(properties.getQueue(), true);
    }

    /**
     * 声明注册邮箱验证码重试队列。
     * 重试队列通过 dead-letter 配置在消息 TTL 到期后重新路由回主队列，
     * 这样消费者无需阻塞线程等待延迟时间。
     *
     * @param properties 注册邮箱验证码 RabbitMQ 配置
     * @return 延迟重试队列
     */
    @Bean
    public Queue registerEmailCodeRetryQueue(RegisterEmailCodeRabbitProperties properties) {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("x-dead-letter-exchange", properties.getExchange());
        arguments.put("x-dead-letter-routing-key", properties.getRoutingKey());
        return new Queue(properties.getRetryQueue(), true, false, false, arguments);
    }

    /**
     * 声明注册邮箱验证码死信队列。
     * 当消息达到最大重试次数仍发送失败时，消费者会显式把消息投递到该队列，供排查 SMTP 故障或业务异常。
     *
     * @param properties 注册邮箱验证码 RabbitMQ 配置
     * @return 发信失败死信队列
     */
    @Bean
    public Queue registerEmailCodeDeadLetterQueue(RegisterEmailCodeRabbitProperties properties) {
        return new Queue(properties.getDeadLetterQueue(), true);
    }

    /**
     * 绑定主消费队列到注册邮箱验证码交换机。
     *
     * @param registerEmailCodeQueue 注册邮箱验证码主队列
     * @param registerEmailCodeExchange 注册邮箱验证码交换机
     * @param properties 注册邮箱验证码 RabbitMQ 配置
     * @return 主队列绑定关系
     */
    @Bean
    public Binding registerEmailCodeQueueBinding(Queue registerEmailCodeQueue,
                                                 DirectExchange registerEmailCodeExchange,
                                                 RegisterEmailCodeRabbitProperties properties) {
        return BindingBuilder.bind(registerEmailCodeQueue)
                .to(registerEmailCodeExchange)
                .with(properties.getRoutingKey());
    }

    /**
     * 绑定重试队列到注册邮箱验证码交换机。
     *
     * @param registerEmailCodeRetryQueue 注册邮箱验证码重试队列
     * @param registerEmailCodeExchange 注册邮箱验证码交换机
     * @param properties 注册邮箱验证码 RabbitMQ 配置
     * @return 重试队列绑定关系
     */
    @Bean
    public Binding registerEmailCodeRetryQueueBinding(Queue registerEmailCodeRetryQueue,
                                                      DirectExchange registerEmailCodeExchange,
                                                      RegisterEmailCodeRabbitProperties properties) {
        return BindingBuilder.bind(registerEmailCodeRetryQueue)
                .to(registerEmailCodeExchange)
                .with(properties.getRetryRoutingKey());
    }

    /**
     * 绑定死信队列到注册邮箱验证码交换机。
     *
     * @param registerEmailCodeDeadLetterQueue 注册邮箱验证码死信队列
     * @param registerEmailCodeExchange 注册邮箱验证码交换机
     * @param properties 注册邮箱验证码 RabbitMQ 配置
     * @return 死信队列绑定关系
     */
    @Bean
    public Binding registerEmailCodeDeadLetterQueueBinding(Queue registerEmailCodeDeadLetterQueue,
                                                           DirectExchange registerEmailCodeExchange,
                                                           RegisterEmailCodeRabbitProperties properties) {
        return BindingBuilder.bind(registerEmailCodeDeadLetterQueue)
                .to(registerEmailCodeExchange)
                .with(properties.getDeadRoutingKey());
    }

    /**
     * 注册 RabbitMQ JSON 消息转换器。
     * 这样生产者和消费者都可以直接基于 RegisterEmailCodeMessage 对象收发消息，无需手动序列化字符串。
     *
     * @return RabbitMQ JSON 消息转换器
     */
    @Bean
    public MessageConverter registerEmailCodeMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * 为注册邮箱验证码消费者单独创建监听容器工厂。
     * 这里把消费者并发度固定在 2-10 区间，并将 prefetch 先设为 1，避免邮件 IO 阻塞时单线程囤积过多未处理消息。
     *
     * @param connectionFactory RabbitMQ 连接工厂
     * @param configurer Spring Boot Rabbit 监听器默认配置器
     * @param messageConverter RabbitMQ JSON 消息转换器
     * @param properties 注册邮箱验证码 RabbitMQ 配置
     * @return 注册邮箱验证码专用监听容器工厂
     */
    @Bean
    public SimpleRabbitListenerContainerFactory registerEmailCodeRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            MessageConverter messageConverter,
            RegisterEmailCodeRabbitProperties properties) {
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
