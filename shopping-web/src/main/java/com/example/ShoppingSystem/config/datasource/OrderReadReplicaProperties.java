package com.example.ShoppingSystem.config.datasource;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shopping.datasource.order-read")
public class OrderReadReplicaProperties extends ReadReplicaGroupProperties {

    public OrderReadReplicaProperties() {
        defaultReplicaUrls(
                "jdbc:postgresql://127.0.0.1:5534/shopping_trade?ApplicationName=shopping-order-read-1",
                "jdbc:postgresql://127.0.0.1:5634/shopping_trade?ApplicationName=shopping-order-read-2"
        );
    }
}
