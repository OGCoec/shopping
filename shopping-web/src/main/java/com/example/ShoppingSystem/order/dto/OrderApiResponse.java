package com.example.ShoppingSystem.order.dto;

public record OrderApiResponse<T>(boolean success,
                                  String code,
                                  String message,
                                  T data) {

    public static <T> OrderApiResponse<T> ok(String code, T data) {
        return new OrderApiResponse<>(true, code, "ok", data);
    }

    public static <T> OrderApiResponse<T> fail(String code, String message) {
        return new OrderApiResponse<>(false, code, message, null);
    }
}
