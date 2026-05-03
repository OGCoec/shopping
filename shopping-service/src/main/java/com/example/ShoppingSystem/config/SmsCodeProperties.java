package com.example.ShoppingSystem.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.sms-code")
public class SmsCodeProperties {

    private String templateCode = "";
    private String codeHashSecret = "shopping-local-sms-secret";
    private long codeTtlMinutes = 5L;
    private long cooldownSeconds = 60L;
    private long ipWindowMinutes = 60L;
    private int phoneDailyLimit = 10;
    private int ipHourlyLimit = 60;
    private String supportedDialCode = "86";
}
