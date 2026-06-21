package com.example.ShoppingSystem.outbox.cardsecretinventory;

public final class CardSecretInventoryImportedRouting {

    private CardSecretInventoryImportedRouting() {
    }

    public static final String EVENT_TYPE = "CARD_SECRET_INVENTORY_IMPORTED";
    public static final String AGGREGATE_TYPE = "PRODUCT_SKU";

    public static final String EXCHANGE = "card.secret.inventory.imported.exchange";
    public static final String QUEUE = "card.secret.inventory.imported.product.queue";
    public static final String DEAD_LETTER_QUEUE = "card.secret.inventory.imported.product.dlq";

    public static final String ROUTING_KEY = "card.secret.inventory.imported.product";
    public static final String DEAD_ROUTING_KEY = "card.secret.inventory.imported.product.dead";
}
