package com.example.ShoppingSystem.common.datasource;

public final class DataSourceRouteGroups {

    private DataSourceRouteGroups() {
    }

    public static DataSourceRoute primaryRoute(DataSourceRoute route) {
        if (route == null) {
            return DataSourceRoute.PRIMARY;
        }
        return switch (route) {
            case PRIMARY, CORE -> DataSourceRoute.CORE;
            case TRADE, ORDER_READ, ORDER_READ_1, ORDER_READ_2 -> DataSourceRoute.TRADE;
            case PRODUCT, PRODUCT_READ, PRODUCT_READ_1, PRODUCT_READ_2 -> DataSourceRoute.PRODUCT;
            case COUPON, COUPON_READ, COUPON_READ_1, COUPON_READ_2 -> DataSourceRoute.COUPON;
            case RISK, RISK_READ, RISK_READ_1, RISK_READ_2 -> DataSourceRoute.RISK;
        };
    }

    public static boolean sameDomain(DataSourceRoute left, DataSourceRoute right) {
        return primaryRoute(left) == primaryRoute(right);
    }
}
