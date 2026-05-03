package com.example.ShoppingSystem.Utils.totp;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class OtpAuthUriUtils {

    public static final String DEFAULT_ALGORITHM = "SHA1";
    public static final int DEFAULT_DIGITS = 6;
    public static final int DEFAULT_PERIOD_SECONDS = 30;

    private OtpAuthUriUtils() {
    }

    public static String buildTotpUri(String issuer, String accountName, String base32Secret) {
        return buildTotpUri(
                issuer,
                accountName,
                base32Secret,
                DEFAULT_ALGORITHM,
                DEFAULT_DIGITS,
                DEFAULT_PERIOD_SECONDS
        );
    }

    public static String buildTotpUri(
            String issuer,
            String accountName,
            String base32Secret,
            String algorithm,
            int digits,
            int periodSeconds
    ) {
        String normalizedIssuer = requireText(issuer, "issuer");
        String normalizedAccountName = requireText(accountName, "accountName");
        String normalizedSecret = normalizeSecret(base32Secret);
        String normalizedAlgorithm = normalizeAlgorithm(algorithm);
        validateDigits(digits);
        validatePeriod(periodSeconds);

        String label = encode(normalizedIssuer) + ":" + encode(normalizedAccountName);
        return "otpauth://totp/" + label
                + "?secret=" + encode(normalizedSecret)
                + "&issuer=" + encode(normalizedIssuer)
                + "&algorithm=" + encode(normalizedAlgorithm)
                + "&digits=" + digits
                + "&period=" + periodSeconds;
    }

    private static String normalizeSecret(String base32Secret) {
        return requireText(base32Secret, "base32Secret")
                .replace(" ", "")
                .replace("-", "")
                .replace("=", "")
                .toUpperCase(Locale.ROOT);
    }

    private static String normalizeAlgorithm(String algorithm) {
        String normalized = requireText(algorithm, "algorithm").toUpperCase(Locale.ROOT);
        if (!normalized.equals("SHA1") && !normalized.equals("SHA256") && !normalized.equals("SHA512")) {
            throw new IllegalArgumentException("algorithm must be SHA1, SHA256, or SHA512");
        }
        return normalized;
    }

    private static void validateDigits(int digits) {
        if (digits < 6 || digits > 8) {
            throw new IllegalArgumentException("digits must be between 6 and 8");
        }
    }

    private static void validatePeriod(int periodSeconds) {
        if (periodSeconds <= 0) {
            throw new IllegalArgumentException("periodSeconds must be greater than 0");
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
