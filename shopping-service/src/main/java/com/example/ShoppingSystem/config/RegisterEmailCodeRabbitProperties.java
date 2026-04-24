package com.example.ShoppingSystem.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 注册邮箱验证码 RabbitMQ 配置属性。
 * 统一管理交换机、队列、路由键、消费者并发度和重试策略，
 * 避免业务代码中散落魔法字符串和硬编码参数。
 */
@Data
@ConfigurationProperties(prefix = "app.rabbitmq.register-email")
public class RegisterEmailCodeRabbitProperties {

    private String exchange = "register.email.code.exchange";
    private String queue = "register.email.code.queue";
    private String retryQueue = "register.email.code.retry.queue";
    private String deadLetterQueue = "register.email.code.dlq";
    private String routingKey = "register.email.code.send";
    private String retryRoutingKey = "register.email.code.retry";
    private String deadRoutingKey = "register.email.code.dead";
    private int concurrency = 2;
    private int maxConcurrency = 10;
    private int prefetch = 1;
    private int maxRetryCount = 2;
}
