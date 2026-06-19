package com.example.ShoppingSystem.config.datasource;

import org.springframework.stereotype.Service;

import java.util.function.Supplier;

@Service
public class ProductReadReplicaQueryExecutor {

    private final ProductReadReplicaProperties properties;
    private final ReadReplicaQueryRunner queryRunner;

    public ProductReadReplicaQueryExecutor(ProductReadReplicaProperties properties,
                                           ReadReplicaQueryRunner queryRunner) {
        this.properties = properties;
        this.queryRunner = queryRunner;
    }

    public <T> T query(Supplier<T> supplier) {
        return queryRunner.query(
                "product",
                properties.isEnabled(),
                properties.isFallbackToPrimary(),
                DataSourceRoute.PRODUCT,
                DataSourceRoute.PRODUCT_READ_1,
                DataSourceRoute.PRODUCT_READ_2,
                supplier
        );
    }

    public <T> T queryPrimary(Supplier<T> supplier) {
        return queryRunner.queryPrimary(DataSourceRoute.PRODUCT, supplier);
    }
}
