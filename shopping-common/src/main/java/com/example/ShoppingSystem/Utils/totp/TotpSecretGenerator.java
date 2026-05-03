package com.example.ShoppingSystem.Utils.totp;

import java.security.SecureRandom;

public final class TotpSecretGenerator {

    public static final int DEFAULT_SECRET_BITS = 256;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final char[] BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();

    private TotpSecretGenerator() {
    }

    public static String generateBase32Secret() {
        return generateBase32Secret(DEFAULT_SECRET_BITS);
    }

    public static String generateBase32Secret(int secretBits) {
        if (secretBits < 128 || secretBits % 8 != 0) {
            throw new IllegalArgumentException("secretBits must be at least 128 and divisible by 8");
        }
        byte[] bytes = new byte[secretBits / 8];
        SECURE_RANDOM.nextBytes(bytes);
        return encodeBase32(bytes);
    }

    private static String encodeBase32(byte[] bytes) {
        StringBuilder result = new StringBuilder((bytes.length * 8 + 4) / 5);
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : bytes) {
            buffer = (buffer << 8) | (b & 0xff);
            bitsLeft += 8;

            while (bitsLeft >= 5) {
                result.append(BASE32_ALPHABET[(buffer >> (bitsLeft - 5)) & 0x1f]);
                bitsLeft -= 5;
            }
        }

        if (bitsLeft > 0) {
            result.append(BASE32_ALPHABET[(buffer << (5 - bitsLeft)) & 0x1f]);
        }
        return result.toString();
    }
}
