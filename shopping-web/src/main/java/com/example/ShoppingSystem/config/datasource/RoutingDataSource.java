package com.example.ShoppingSystem.config.datasource;

import com.example.ShoppingSystem.common.datasource.RoutingDataSourceContext;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

public class RoutingDataSource extends AbstractRoutingDataSource {

    @Override
    protected Object determineCurrentLookupKey() {
        return RoutingDataSourceContext.current();
    }
}
