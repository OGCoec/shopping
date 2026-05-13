package com.example.ShoppingSystem.security;

public record OAuth2PreAuthRiskDecision(boolean allowed,
                                        int status,
                                        String error,
                                        String message) {

    public static OAuth2PreAuthRiskDecision allow() {
        return new OAuth2PreAuthRiskDecision(true, 200, "OK", "ok");
    }

    public static OAuth2PreAuthRiskDecision block(int status, String error, String message) {
        return new OAuth2PreAuthRiskDecision(false, status, error, message);
    }
}
