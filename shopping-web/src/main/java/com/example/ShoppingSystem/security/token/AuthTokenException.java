package com.example.ShoppingSystem.security.token;

public class AuthTokenException extends RuntimeException {

    private final int status;
    private final String error;

    public AuthTokenException(int status, String error, String message) {
        super(message);
        this.status = status;
        this.error = error;
    }

    public int status() {
        return status;
    }

    public String error() {
        return error;
    }
}
