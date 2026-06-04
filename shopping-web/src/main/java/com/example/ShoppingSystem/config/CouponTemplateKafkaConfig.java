package com.example.ShoppingSystem.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;

@EnableKafka
@Configuration
@ConditionalOnProperty(prefix = "shopping.admin.coupon-template-cdc", name = "enabled", havingValue = "true")
public class CouponTemplateKafkaConfig {
}
