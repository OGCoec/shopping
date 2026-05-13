package com.example.ShoppingSystem.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "risk.terminated-account-email-counting-bloom")
public class TerminatedAccountEmailCountingBloomProperties {

    private boolean enabled = true;
    private String key = "risk:account:terminated:email:cbf";
    private int capacity = 600000;
    private int hashCount = 7;
    private int counterBytes = 2;
    private int pageSize = 2000;
    private int asyncCorePoolSize = 1;
    private int asyncMaxPoolSize = 2;
    private int asyncQueueCapacity = 1000;
}
