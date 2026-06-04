package com.example.ShoppingSystem.order.service;

import com.example.ShoppingSystem.admin.service.config.AdminCardSecretCryptoConfigService;
import com.example.ShoppingSystem.admin.service.config.AdminManagedEnvService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.Locale;

@Service
public class OrderCardSecretCryptoService {

    private static final int AES_KEY_BYTES = 32;
    private static final int GCM_TAG_BITS = 128;

    private final AdminManagedEnvService managedEnvService;

    public OrderCardSecretCryptoService(AdminManagedEnvService managedEnvService) {
        this.managedEnvService = managedEnvService;
    }

    public String decrypt(String ciphertextBase64, String nonceBase64, String keyVersion) {
        byte[] aesKey = loadAesKey(keyVersion);
        byte[] ciphertext = decodeBase64(ciphertextBase64, "ORDER_CARD_SECRET_DECRYPT_FAILED", "Card secret ciphertext is invalid.");
        byte[] nonce = decodeBase64(nonceBase64, "ORDER_CARD_SECRET_DECRYPT_FAILED", "Card secret nonce is invalid.");
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new GCMParameterSpec(GCM_TAG_BITS, nonce));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException ex) {
            throw new OrderServiceException(
                    "ORDER_CARD_SECRET_DECRYPT_FAILED",
                    "Card secret decrypt failed.",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    private byte[] loadAesKey(String keyVersion) {
        String normalizedVersion = normalizeKeyVersion(keyVersion);
        String envName = AdminCardSecretCryptoConfigService.AES_KEY_ENV_PREFIX + normalizedVersion.toUpperCase(Locale.ROOT);
        String value = managedEnvService.readSystemEnvValue(envName)
                .map(String::trim)
                .filter(StringUtils::hasText)
                .orElseThrow(() -> cryptoNotConfigured("Card secret AES key is not configured: " + envName));
        byte[] decoded = decodeBase64(value, "ORDER_CARD_SECRET_CRYPTO_NOT_CONFIGURED", "Card secret AES key must be standard Base64.");
        if (decoded.length != AES_KEY_BYTES) {
            throw cryptoNotConfigured("Card secret AES key must decode to exactly 32 bytes.");
        }
        return decoded;
    }

    private String normalizeKeyVersion(String keyVersion) {
        String value = keyVersion == null ? "" : keyVersion.trim();
        if (!value.matches("^v[1-9][0-9]{0,2}$")) {
            throw cryptoNotConfigured("Card secret key version is invalid.");
        }
        return value;
    }

    private byte[] decodeBase64(String value, String code, String message) {
        try {
            return Base64.getDecoder().decode(value == null ? "" : value.trim());
        } catch (IllegalArgumentException ex) {
            throw new OrderServiceException(code, message, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private OrderServiceException cryptoNotConfigured(String message) {
        return new OrderServiceException(
                "ORDER_CARD_SECRET_CRYPTO_NOT_CONFIGURED",
                message,
                HttpStatus.BAD_REQUEST
        );
    }
}
