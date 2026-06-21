package com.example.ShoppingSystem.outbox.fault;

import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

@Component
public class FaultInjector {

    private final FaultInjectorProperties properties;

    public static final String FAULT_THROW = "THROW";

    public FaultInjector(FaultInjectorProperties properties) {
        this.properties = properties;
    }

    public void maybeFail(String consumerName, String loadtestFault) {
        if (!properties.isEnabled()) {
            return;
        }
        if (FAULT_THROW.equalsIgnoreCase(loadtestFault != null ? loadtestFault.trim() : "")) {
            throwFault(consumerName);
            return;
        }
        Double probability = properties.getConsumerProbabilities().get(consumerName);
        if (probability != null && probability > 0.0 && ThreadLocalRandom.current().nextDouble() < probability) {
            throwFault(consumerName);
        }
    }

    private void throwFault(String consumerName) {
        throw new IllegalStateException("INJECTED_FAULT:" + consumerName);
    }
}