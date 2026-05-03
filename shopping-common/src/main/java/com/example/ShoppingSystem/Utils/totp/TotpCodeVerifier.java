package com.example.ShoppingSystem.Utils.totp;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.OptionalLong;

public final class TotpCodeVerifier {

    public static final int DEFAULT_WINDOW = 1;

    private TotpCodeVerifier() {
    }

    public static boolean verify(String base32Secret, String code) {
        return verify(base32Secret, code, Instant.now(), DEFAULT_WINDOW);
    }

    public static boolean verify(String base32Secret, String code, Instant now) {
        return verify(base32Secret, code, now, DEFAULT_WINDOW);
    }

    public static boolean verify(String base32Secret, String code, Instant now, int window) {
        return findMatchedTimeStep(base32Secret, code, now, window).isPresent();
    }

    public static OptionalLong findMatchedTimeStep(String base32Secret, String code, Instant now, int window) {
        validateWindow(window);
        String normalizedCode = normalizeCode(code);
        if (normalizedCode.isEmpty()) {
            return OptionalLong.empty();
        }

        long currentTimeStep = TotpCodeGenerator.timeStepAt(now);
        for (long offset = -window; offset <= window; offset++) {
            long candidateTimeStep = currentTimeStep + offset;
            if (candidateTimeStep < 0) {
                continue;
            }

            String expectedCode = TotpCodeGenerator.generate(
                    base32Secret,
                    candidateTimeStep,
                    normalizedCode.length()
            );
            if (constantTimeEquals(expectedCode, normalizedCode)) {
                return OptionalLong.of(candidateTimeStep);
            }
        }
        return OptionalLong.empty();
    }

    private static String normalizeCode(String code) {
        if (code == null) {
            return "";
        }
        String normalized = code.trim().replace(" ", "");
        if (!normalized.matches("\\d{6,8}")) {
            return "";
        }
        return normalized;
    }

    private static void validateWindow(int window) {
        if (window < 0 || window > 2) {
            throw new IllegalArgumentException("window must be between 0 and 2");
        }
    }

    private static boolean constantTimeEquals(String expectedCode, String actualCode) {
        return MessageDigest.isEqual(
                expectedCode.getBytes(StandardCharsets.US_ASCII),
                actualCode.getBytes(StandardCharsets.US_ASCII)
        );
    }
}
