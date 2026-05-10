package com.example.ShoppingSystem.admin.service;

import org.springframework.http.HttpStatus;

public class AdminServiceException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public AdminServiceException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
