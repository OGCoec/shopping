package com.example.ShoppingSystem.config.datasource;

public final class RoutingDataSourceContext {

    private static final ThreadLocal<DataSourceRoute> CURRENT_ROUTE = new ThreadLocal<>();

    private RoutingDataSourceContext() {
    }

    public static void use(DataSourceRoute route) {
        CURRENT_ROUTE.set(route == null ? DataSourceRoute.PRIMARY : route);
    }

    public static DataSourceRoute current() {
        DataSourceRoute route = CURRENT_ROUTE.get();
        return route == null ? DataSourceRoute.PRIMARY : route;
    }

    public static void clear() {
        CURRENT_ROUTE.remove();
    }
}
