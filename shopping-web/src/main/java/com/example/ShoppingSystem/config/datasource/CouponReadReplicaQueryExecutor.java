package com.example.ShoppingSystem.config.datasource;

import com.example.ShoppingSystem.common.datasource.DataSourceRoute;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

@Service
public class CouponReadReplicaQueryExecutor {

    private final CouponReadReplicaProperties properties;
    private final ReadReplicaQueryRunner queryRunner;

    public CouponReadReplicaQueryExecutor(CouponReadReplicaProperties properties,
                                          ReadReplicaQueryRunner queryRunner) {
        this.properties = properties;
        this.queryRunner = queryRunner;
    }

    public <T> T query(Supplier<T> supplier) {
        return queryRunner.query(
                "coupon",
                properties.isEnabled(),
                properties.isFallbackToPrimary(),
                DataSourceRoute.COUPON,
                DataSourceRoute.COUPON_READ_1,
                DataSourceRoute.COUPON_READ_2,
                supplier
        );
    }

    public <T> T queryPrimary(Supplier<T> supplier) {
        return queryRunner.queryPrimary(DataSourceRoute.COUPON, supplier);
    }
}
