package com.example.ShoppingSystem.outbox.orderstock;

public final class OrderStockDeductRequestedRouting {

    private OrderStockDeductRequestedRouting() {
    }

    public static final String EVENT_TYPE = "ORDER_STOCK_DEDUCT_REQUESTED";
    public static final String AGGREGATE_TYPE = "TRADE_ORDER";

    public static final String EXCHANGE = "order.stock.deduct.request.exchange";
    public static final String QUEUE = "order.stock.deduct.request.product.queue";
    public static final String DEAD_LETTER_QUEUE = "order.stock.deduct.request.product.dlq";

    public static final String ROUTING_KEY = "order.stock.deduct.product";
    public static final String DEAD_ROUTING_KEY = "order.stock.deduct.product.dead";
}
