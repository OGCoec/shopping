package com.example.ShoppingSystem.security.token;

public record AuthTokenAuthenticationResult(boolean success,
                                            AuthUserContext context,
                                            int status,
                                            String error,
                                            String message) {

    public static AuthTokenAuthenticationResult authenticated(AuthUserContext context) {
        return new AuthTokenAuthenticationResult(true, context, 200, null, "authenticated");
    }

    public static AuthTokenAuthenticationResult failed(int status, String error, String message) {
        return new AuthTokenAuthenticationResult(false, null, status, error, message);
    }
}
