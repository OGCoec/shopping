package com.example.ShoppingSystem.Utils.totp;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Locale;

public final class TotpCodeGenerator {

    public static final int DEFAULT_TIME_STEP_SECONDS = 30;
    public static final int DEFAULT_DIGITS = 6;

    private static final String HMAC_SHA1 = "HmacSHA1";
    private static final int[] POWERS_OF_10 = {
            1,
            10,
            100,
            1_000,
            10_000,
            100_000,
            1_000_000,
            10_000_000,
            100_000_000
    };

    private TotpCodeGenerator() {
    }

    public static String generate(String base32Secret) {
        return generate(base32Secret, Instant.now());
    }

    public static String generate(String base32Secret, Instant instant) {
        return generate(base32Secret, timeStepAt(instant));
    }

    public static String generate(String base32Secret, long timeStep) {
        return generate(base32Secret, timeStep, DEFAULT_DIGITS);
    }

    public static String generate(String base32Secret, long timeStep, int digits) {
        validateDigits(digits);
        if (timeStep < 0) {
            throw new IllegalArgumentException("timeStep must not be negative");
        }

        byte[] secretBytes = decodeBase32(base32Secret);
        byte[] counterBytes = ByteBuffer.allocate(Long.BYTES).putLong(timeStep).array();
        byte[] hash = hmacSha1(secretBytes, counterBytes);

        int offset = hash[hash.length - 1] & 0x0f;
        int binary = ((hash[offset] & 0x7f) << 24)
                | ((hash[offset + 1] & 0xff) << 16)
                | ((hash[offset + 2] & 0xff) << 8)
                | (hash[offset + 3] & 0xff);

        int otp = binary % POWERS_OF_10[digits];
        return String.format(Locale.ROOT, "%0" + digits + "d", otp);
    }

    public static long timeStepAt(Instant instant) {
        if (instant == null) {
            throw new IllegalArgumentException("instant must not be null");
        }
        long epochSecond = instant.getEpochSecond();
        if (epochSecond < 0) {
            throw new IllegalArgumentException("instant must not be before Unix epoch");
        }
        return epochSecond / DEFAULT_TIME_STEP_SECONDS;
    }

    static byte[] decodeBase32(String base32Secret) {
        if (base32Secret == null || base32Secret.isBlank()) {
            throw new IllegalArgumentException("base32Secret must not be blank");
        }

        String normalized = base32Secret
                .replace(" ", "")
                .replace("-", "")
                .replace("=", "")
                .toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("base32Secret must contain Base32 characters");
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int buffer = 0;
        int bitsLeft = 0;
        for (int i = 0; i < normalized.length(); i++) {
            int value = base32Value(normalized.charAt(i));
            buffer = (buffer << 5) | value;
            bitsLeft += 5;

            if (bitsLeft >= 8) {
                output.write((buffer >> (bitsLeft - 8)) & 0xff);
                bitsLeft -= 8;
            }
        }
        return output.toByteArray();
    }

    private static int base32Value(char c) {
        if (c >= 'A' && c <= 'Z') {
            return c - 'A';
        }
        if (c >= '2' && c <= '7') {
            return c - '2' + 26;
        }
        throw new IllegalArgumentException("Invalid Base32 character: " + c);
    }

    private static byte[] hmacSha1(byte[] secretBytes, byte[] counterBytes) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA1);
            mac.init(new SecretKeySpec(secretBytes, HMAC_SHA1));
            return mac.doFinal(counterBytes);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate TOTP code", e);
        }
    }

    private static void validateDigits(int digits) {
        if (digits < 6 || digits > 8) {
            throw new IllegalArgumentException("digits must be between 6 and 8");
        }
    }
}
