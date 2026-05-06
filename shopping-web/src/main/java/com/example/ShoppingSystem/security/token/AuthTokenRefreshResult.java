package com.example.ShoppingSystem.security.token;

public record AuthTokenRefreshResult(boolean success,
                                     int status,
                                     String error,
                                     String message) {

    public static AuthTokenRefreshResult ok() {
        return new AuthTokenRefreshResult(true, 200, null, "refreshed");
    }

    public static AuthTokenRefreshResult failed(int status, String error, String message) {
        return new AuthTokenRefreshResult(false, status, error, message);
    }
}
