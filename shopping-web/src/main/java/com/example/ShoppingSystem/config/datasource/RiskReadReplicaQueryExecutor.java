package com.example.ShoppingSystem.config.datasource;

import com.example.ShoppingSystem.common.datasource.DataSourceRoute;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

@Service
public class RiskReadReplicaQueryExecutor {

    private final RiskReadReplicaProperties properties;
    private final ReadReplicaQueryRunner queryRunner;

    public RiskReadReplicaQueryExecutor(RiskReadReplicaProperties properties,
                                        ReadReplicaQueryRunner queryRunner) {
        this.properties = properties;
        this.queryRunner = queryRunner;
    }

    public <T> T query(Supplier<T> supplier) {
        return queryRunner.query(
                "risk",
                properties.isEnabled(),
                properties.isFallbackToPrimary(),
                DataSourceRoute.RISK,
                DataSourceRoute.RISK_READ_1,
                DataSourceRoute.RISK_READ_2,
                supplier
        );
    }

    public <T> T queryPrimary(Supplier<T> supplier) {
        return queryRunner.queryPrimary(DataSourceRoute.RISK, supplier);
    }
}
