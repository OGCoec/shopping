package com.example.ShoppingSystem.config.datasource;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shopping.datasource.risk-read")
public class RiskReadReplicaProperties extends ReadReplicaGroupProperties {

    public RiskReadReplicaProperties() {
        defaultReplicaUrls(
                "jdbc:postgresql://127.0.0.1:5537/shopping_risk?ApplicationName=shopping-risk-read-1",
                "jdbc:postgresql://127.0.0.1:5637/shopping_risk?ApplicationName=shopping-risk-read-2"
        );
    }
}
