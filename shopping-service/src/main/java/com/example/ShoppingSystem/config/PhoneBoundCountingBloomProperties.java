package com.example.ShoppingSystem.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "login.phone-bound-counting-bloom")
public class PhoneBoundCountingBloomProperties {

    private boolean enabled = true;
    private String key = "login:phone:bound:cbf";
    private String memberKeyPrefix = "login:phone:bound:member:";
    private int capacity = 600000;
    private int hashCount = 7;
    private int counterBytes = 2;
    private int pageSize = 2000;
    private int asyncCorePoolSize = 1;
    private int asyncMaxPoolSize = 2;
    private int asyncQueueCapacity = 1000;
}
