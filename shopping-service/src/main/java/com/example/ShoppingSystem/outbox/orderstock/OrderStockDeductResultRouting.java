package com.example.ShoppingSystem.outbox.orderstock;

public final class OrderStockDeductResultRouting {

    private OrderStockDeductResultRouting() {
    }

    public static final String EVENT_TYPE = "ORDER_STOCK_DEDUCT_RESULT";
    public static final String AGGREGATE_TYPE = "TRADE_ORDER";

    public static final String EXCHANGE = "order.stock.deduct.result.exchange";
    public static final String QUEUE = "order.stock.deduct.result.trade.queue";
    public static final String DEAD_LETTER_QUEUE = "order.stock.deduct.result.trade.dlq";

    public static final String ROUTING_KEY = "order.stock.deduct.result.trade";
    public static final String DEAD_ROUTING_KEY = "order.stock.deduct.result.trade.dead";
}
