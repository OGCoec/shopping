package com.example.ShoppingSystem.service.user.auth.login;

public class UserAuthAccountUnavailableException extends RuntimeException {

    private final String status;

    public UserAuthAccountUnavailableException(String status) {
        super("Account is temporarily unavailable.");
        this.status = status;
    }

    public String getStatus() {
        return status;
    }
}
