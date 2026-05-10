package com.example.ShoppingSystem.admin.dto;

public record AdminApiResponse<T>(boolean success,
                                  String code,
                                  String message,
                                  T data) {

    public static <T> AdminApiResponse<T> ok(T data) {
        return new AdminApiResponse<>(true, "ADMIN_OK", "ok", data);
    }

    public static <T> AdminApiResponse<T> fail(String code, String message) {
        return new AdminApiResponse<>(false, code, message, null);
    }
}
