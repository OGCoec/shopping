package com.example.ShoppingSystem.common.datasource;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

@Service
public class RoutedTransactionExecutor {

    private final TransactionTemplate transactionTemplate;

    public RoutedTransactionExecutor(TransactionTemplate transactionTemplate) {
        this.transactionTemplate = transactionTemplate;
    }

    public <T> T execute(DataSourceRoute route, Supplier<T> supplier) {
        DataSourceRoute previousRoute = RoutingDataSourceContext.snapshot();
        try {
            RoutingDataSourceContext.use(route);
            return transactionTemplate.execute(status -> supplier.get());
        } finally {
            RoutingDataSourceContext.restore(previousRoute);
        }
    }

    public void executeWithoutResult(DataSourceRoute route, Runnable runnable) {
        execute(route, () -> {
            runnable.run();
            return null;
        });
    }
}
