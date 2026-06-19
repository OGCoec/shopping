package com.example.ShoppingSystem.config.datasource;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shopping.datasource.product-read")
public class ProductReadReplicaProperties extends ReadReplicaGroupProperties {

    public ProductReadReplicaProperties() {
        defaultReplicaUrls(
                "jdbc:postgresql://127.0.0.1:5535/shopping_product?ApplicationName=shopping-product-read-1",
                "jdbc:postgresql://127.0.0.1:5635/shopping_product?ApplicationName=shopping-product-read-2"
        );
    }
}
