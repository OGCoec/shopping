package com.example.ShoppingSystem.common.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Unified API response body.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {

    private boolean success;
    private String code;
    private String message;
    private FailureType failureType;
    private T data;

    public static <T> Result<T> success(T data) {
        return success("SUCCESS", data);
    }

    public static <T> Result<T> success(String message, T data) {
        return Result.<T>builder()
                .success(true)
                .code("SUCCESS")
                .message(message)
                .data(data)
                .build();
    }

    public static <T> Result<T> failure(FailureType failureType, String message) {
        return failure(failureType, message, null);
    }

    public static <T> Result<T> failure(FailureType failureType, String message, T data) {
        return Result.<T>builder()
                .success(false)
                .code(failureType == null ? "FAILURE" : failureType.name())
                .message(message)
                .failureType(failureType)
                .data(data)
                .build();
    }
}
