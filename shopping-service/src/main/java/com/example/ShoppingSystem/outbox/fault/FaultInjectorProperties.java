package com.example.ShoppingSystem.outbox.fault;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "app.outbox.fault")
public class FaultInjectorProperties {

    private boolean enabled = false;
    private Map<String, Double> consumerProbabilities = new HashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Map<String, Double> getConsumerProbabilities() {
        return consumerProbabilities;
    }

    public void setConsumerProbabilities(Map<String, Double> consumerProbabilities) {
        this.consumerProbabilities = consumerProbabilities == null ? new HashMap<>() : consumerProbabilities;
    }
}