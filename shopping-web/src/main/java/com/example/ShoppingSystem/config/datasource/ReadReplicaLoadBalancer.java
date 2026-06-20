package com.example.ShoppingSystem.config.datasource;

import com.example.ShoppingSystem.common.datasource.DataSourceRoute;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ReadReplicaLoadBalancer {

    private final ClientIpResolver clientIpResolver;
    private final AtomicInteger roundRobin = new AtomicInteger();

    public ReadReplicaLoadBalancer(ClientIpResolver clientIpResolver) {
        this.clientIpResolver = clientIpResolver;
    }

    public ReadReplicaSelection select(DataSourceRoute firstReplica, DataSourceRoute secondReplica) {
        DataSourceRoute[] replicas = {firstReplica, secondReplica};
        String clientIp = clientIpResolver.resolveClientIp().orElse(null);
        int selectedIndex = clientIp == null
                ? Math.floorMod(roundRobin.getAndIncrement(), replicas.length)
                : Math.floorMod(clientIp.hashCode(), replicas.length);
        int fallbackIndex = selectedIndex == 0 ? 1 : 0;
        return new ReadReplicaSelection(
                replicas[selectedIndex],
                replicas[fallbackIndex],
                selectedIndex + 1,
                fallbackIndex + 1,
                clientIp == null ? "round-robin" : clientIp
        );
    }

    public record ReadReplicaSelection(DataSourceRoute selectedRoute,
                                       DataSourceRoute fallbackRoute,
                                       int selectedReplica,
                                       int fallbackReplica,
                                       String key) {
    }
}
