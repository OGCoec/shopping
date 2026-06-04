package com.example.ShoppingSystem.order.service;

import org.springframework.http.HttpStatus;

public class OrderServiceException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public OrderServiceException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }
}
