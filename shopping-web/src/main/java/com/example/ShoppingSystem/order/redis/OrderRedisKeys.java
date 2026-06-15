package com.example.ShoppingSystem.order.redis;

public final class OrderRedisKeys {

    private static final String HOT_SKU_META_KEY_PREFIX = "shopping:product:hot-sku:meta:";
    private static final String HOT_SKU_STOCK_KEY_PREFIX = "shopping:product:hot-sku:stock:";
    private static final String HOT_SKU_USER_KEY_PREFIX = "shopping:order:hot-sku:user:";
    private static final String HOT_SKU_PENDING_KEY_PREFIX = "shopping:order:hot-sku:pending:";
    private static final String ORDER_DETAIL_KEY_PREFIX = "shopping:order:detail:";
    private static final String ORDER_ITEM_KEY_PREFIX = "shopping:order:item:";
    private static final String USER_ORDER_KEY_PREFIX = "shopping:order:user:";
    private static final String IDEMPOTENCY_KEY_PREFIX = "shopping:order:idempotency:";
    private static final String PAYMENT_CALLBACK_RECEIVED_ORDER_KEY_PREFIX = "shopping:payment:callback:received-order:";

    public static final String ORDER_EXPIRE_ZSET_KEY = "shopping:order:expire";
    public static final String ORDER_CLOSING_ZSET_KEY = "shopping:order:closing";
    public static final String ORDER_ALL_ZSET_KEY = "shopping:order:all";
    public static final String ORDER_PERSIST_DIRTY_ZSET_KEY = "shopping:order:persist:dirty";
    public static final String ORDER_PERSIST_PROCESSING_ZSET_KEY = "shopping:order:persist:processing";
    public static final String ORDER_PERSIST_LOCK_KEY = "shopping:order:persist:lock";

    private OrderRedisKeys() {
    }

    public static String hotSkuMetaKey(String skuId) {
        return HOT_SKU_META_KEY_PREFIX + skuId;
    }

    public static String hotSkuStockKey(String skuId) {
        return HOT_SKU_STOCK_KEY_PREFIX + skuId;
    }

    public static String hotSkuUserKey(String skuId) {
        return HOT_SKU_USER_KEY_PREFIX + skuId;
    }

    public static String hotSkuPendingKey(String orderNo) {
        return HOT_SKU_PENDING_KEY_PREFIX + orderNo;
    }

    public static String orderDetailKey(String orderNo) {
        return ORDER_DETAIL_KEY_PREFIX + orderNo;
    }

    public static String orderItemKey(String orderNo) {
        return ORDER_ITEM_KEY_PREFIX + orderNo;
    }

    public static String userOrderKey(Long userId) {
        return USER_ORDER_KEY_PREFIX + userId + ":orders";
    }

    public static String idempotencyKey(Long userId, String idempotencyKey) {
        return IDEMPOTENCY_KEY_PREFIX + userId + ":" + idempotencyKey;
    }

    public static String paymentCallbackReceivedOrderKey(String orderNo) {
        return PAYMENT_CALLBACK_RECEIVED_ORDER_KEY_PREFIX + orderNo;
    }
}
