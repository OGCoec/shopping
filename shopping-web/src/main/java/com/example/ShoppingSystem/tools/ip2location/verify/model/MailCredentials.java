package com.example.ShoppingSystem.tools.ip2location.verify.model;

public record MailCredentials(String email, String clientId, String refreshToken) {

    public static MailCredentials parse(String credentials) {
        if (credentials == null || credentials.isBlank()) {
            throw new IllegalArgumentException("Credentials string cannot be empty");
        }
        String[] parts = credentials.split("----");
        if (parts.length == 3) {
            return new MailCredentials(parts[0].trim(), parts[1].trim(), parts[2].trim());
        }
        if (parts.length >= 4) {
            return new MailCredentials(parts[0].trim(), parts[2].trim(), parts[3].trim());
        }
        throw new IllegalArgumentException("Credentials format must be email----client_id----refresh_token or email----password----client_id----refresh_token");
    }
}
