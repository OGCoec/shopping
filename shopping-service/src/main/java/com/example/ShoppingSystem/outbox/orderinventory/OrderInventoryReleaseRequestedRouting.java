package com.example.ShoppingSystem.outbox.orderinventory;

public final class OrderInventoryReleaseRequestedRouting {

    private OrderInventoryReleaseRequestedRouting() {
    }

    public static final String EVENT_TYPE = "ORDER_INVENTORY_RELEASE_REQUESTED";
    public static final String AGGREGATE_TYPE = "TRADE_ORDER";

    public static final String EXCHANGE = "order.inventory.release.exchange";
    public static final String QUEUE = "order.inventory.release.product.queue";
    public static final String DEAD_LETTER_QUEUE = "order.inventory.release.product.dlq";

    public static final String ROUTING_KEY = "order.inventory.release.product";
    public static final String DEAD_ROUTING_KEY = "order.inventory.release.product.dead";
}
