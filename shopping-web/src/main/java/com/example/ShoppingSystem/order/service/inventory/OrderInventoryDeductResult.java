package com.example.ShoppingSystem.order.service.inventory;

public record OrderInventoryDeductResult(boolean success,
                                         String code,
                                         String message,
                                         Integer remainingQuantity) {

    public static OrderInventoryDeductResult success(Integer remainingQuantity) {
        return new OrderInventoryDeductResult(true, "ORDER_INVENTORY_DEDUCTED", "ok", remainingQuantity);
    }

    public static OrderInventoryDeductResult fail(String code, String message) {
        return new OrderInventoryDeductResult(false, code, message, null);
    }
}
