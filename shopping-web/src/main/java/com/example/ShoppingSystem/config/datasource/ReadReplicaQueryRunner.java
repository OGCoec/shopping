package com.example.ShoppingSystem.config.datasource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

@Service
public class ReadReplicaQueryRunner {

    private static final Logger log = LoggerFactory.getLogger(ReadReplicaQueryRunner.class);

    private final ReadReplicaLoadBalancer loadBalancer;

    public ReadReplicaQueryRunner(ReadReplicaLoadBalancer loadBalancer) {
        this.loadBalancer = loadBalancer;
    }

    public <T> T query(String domain,
                       boolean enabled,
                       boolean fallbackToPrimary,
                       DataSourceRoute primaryRoute,
                       DataSourceRoute firstReplicaRoute,
                       DataSourceRoute secondReplicaRoute,
                       Supplier<T> supplier) {
        if (!enabled) {
            return queryOn(primaryRoute, supplier);
        }
        ReadReplicaLoadBalancer.ReadReplicaSelection selection = loadBalancer.select(firstReplicaRoute, secondReplicaRoute);
        RuntimeException selectedFailure;
        try {
            return queryOn(selection.selectedRoute(), supplier);
        } catch (RuntimeException e) {
            selectedFailure = e;
            log.warn(
                    "Read replica query failed, domain={}, selectedReplica={}, fallbackReplica={}, fallbackToPrimary={}, reason={}",
                    domain,
                    selection.selectedReplica(),
                    selection.fallbackReplica(),
                    fallbackToPrimary,
                    e.getMessage()
            );
        }
        try {
            return queryOn(selection.fallbackRoute(), supplier);
        } catch (RuntimeException e) {
            e.addSuppressed(selectedFailure);
            log.warn(
                    "Read replica fallback query failed, domain={}, selectedReplica={}, fallbackReplica={}, fallbackToPrimary={}, reason={}",
                    domain,
                    selection.selectedReplica(),
                    selection.fallbackReplica(),
                    fallbackToPrimary,
                    e.getMessage()
            );
            if (!fallbackToPrimary) {
                throw e;
            }
            return queryOn(primaryRoute, supplier);
        }
    }

    public <T> T queryPrimary(DataSourceRoute primaryRoute, Supplier<T> supplier) {
        return queryOn(primaryRoute, supplier);
    }

    private <T> T queryOn(DataSourceRoute route, Supplier<T> supplier) {
        DataSourceRoute previousRoute = RoutingDataSourceContext.snapshot();
        try {
            RoutingDataSourceContext.use(route);
            return supplier.get();
        } finally {
            RoutingDataSourceContext.restore(previousRoute);
        }
    }
}
