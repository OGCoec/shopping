package com.example.ShoppingSystem.config.datasource;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shopping.datasource.coupon-read")
public class CouponReadReplicaProperties extends ReadReplicaGroupProperties {

    public CouponReadReplicaProperties() {
        defaultReplicaUrls(
                "jdbc:postgresql://127.0.0.1:5536/shopping_coupon?ApplicationName=shopping-coupon-read-1",
                "jdbc:postgresql://127.0.0.1:5636/shopping_coupon?ApplicationName=shopping-coupon-read-2"
        );
    }
}
