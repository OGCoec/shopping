package com.example.ShoppingSystem.config.datasource;

import org.springframework.stereotype.Service;

import java.util.function.Supplier;

@Service
public class ShardDataSourceExecutor {

    public <T> T query(DataSourceRoute route, Supplier<T> supplier) {
        return run(route, supplier);
    }

    public <T> T run(DataSourceRoute route, Supplier<T> supplier) {
        DataSourceRoute previousRoute = RoutingDataSourceContext.snapshot();
        try {
            RoutingDataSourceContext.use(route);
            return supplier.get();
        } finally {
            RoutingDataSourceContext.restore(previousRoute);
        }
    }

    public void run(DataSourceRoute route, Runnable runnable) {
        run(route, () -> {
            runnable.run();
            return null;
        });
    }
}
