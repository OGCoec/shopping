package com.example.ShoppingSystem.config.datasource;

import com.example.ShoppingSystem.common.datasource.DataSourceRoute;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

@Service
public class OrderReadReplicaQueryExecutor {

    private final OrderReadReplicaProperties properties;
    private final ReadReplicaQueryRunner queryRunner;

    public OrderReadReplicaQueryExecutor(OrderReadReplicaProperties properties,
                                         ReadReplicaQueryRunner queryRunner) {
        this.properties = properties;
        this.queryRunner = queryRunner;
    }

    public <T> T query(Supplier<T> supplier) {
        return queryRunner.query(
                "order",
                properties.isEnabled(),
                properties.isFallbackToPrimary(),
                DataSourceRoute.TRADE,
                DataSourceRoute.ORDER_READ_1,
                DataSourceRoute.ORDER_READ_2,
                supplier
        );
    }

    public <T> T queryPrimary(Supplier<T> supplier) {
        return queryRunner.queryPrimary(DataSourceRoute.TRADE, supplier);
    }
}
