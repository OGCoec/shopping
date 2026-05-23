package com.example.ShoppingSystem.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "captcha.tianai.resource")
public class TianaiCaptchaResourceProperties {

    private String backgroundDir = "captcha/tianai/rotate/background";
    private String sliderTemplateDir = "captcha/tianai/rotate/spilt";
}
