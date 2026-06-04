package com.example.ShoppingSystem.config.datasource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

@Service
public class ProductReadReplicaQueryExecutor {

    private static final Logger log = LoggerFactory.getLogger(ProductReadReplicaQueryExecutor.class);

    private final ProductReadReplicaProperties properties;

    public ProductReadReplicaQueryExecutor(ProductReadReplicaProperties properties) {
        this.properties = properties;
    }

    public <T> T query(Supplier<T> supplier) {
        if (!properties.isEnabled()) {
            return supplier.get();
        }
        try {
            RoutingDataSourceContext.use(DataSourceRoute.PRODUCT_READ);
            return supplier.get();
        } catch (RuntimeException e) {
            if (!properties.isFallbackToPrimary()) {
                throw e;
            }
            log.warn("Product read replica query failed, fallback to primary, reason={}", e.getMessage());
            RoutingDataSourceContext.use(DataSourceRoute.PRIMARY);
            return supplier.get();
        } finally {
            RoutingDataSourceContext.clear();
        }
    }
}
