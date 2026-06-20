package com.example.ShoppingSystem.admin.service.config.impl.AdminCardSecretCryptoConfigService;

import com.example.ShoppingSystem.admin.dto.AdminCardSecretCryptoConfigField;
import com.example.ShoppingSystem.admin.dto.AdminCardSecretCryptoConfigResponse;
import com.example.ShoppingSystem.admin.dto.AdminCardSecretCryptoConfigUpdateRequest;
import com.example.ShoppingSystem.admin.dto.AdminCardSecretCryptoGenerateRequest;
import com.example.ShoppingSystem.admin.service.common.AdminServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.regex.Pattern;

import com.example.ShoppingSystem.admin.service.config.AdminCardSecretCryptoConfigService;
import com.example.ShoppingSystem.admin.service.config.AdminManagedEnvService;
@Service
public class AdminCardSecretCryptoConfigServiceImpl implements AdminCardSecretCryptoConfigService {

    public static final String ACTIVE_KEY_VERSION_ENV = "CARD_SECRET_ACTIVE_KEY_VERSION";
    public static final String AES_KEY_ENV_PREFIX = "CARD_SECRET_AES_KEY_";
    public static final String HMAC_KEY_ENV_PREFIX = "CARD_SECRET_HMAC_KEY_";

    private static final String DEFAULT_KEY_VERSION = "v1";
    private static final int AES_KEY_BYTES = 32;
    private static final int HMAC_KEY_MIN_BYTES = 32;
    private static final int MAX_VALUE_LENGTH = 1024;
    private static final String NOT_CONFIGURED = "未配置";
    private static final Pattern KEY_VERSION_PATTERN = Pattern.compile("^v[1-9][0-9]{0,2}$");

    private final AdminManagedEnvService managedEnvService;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Object monitor = new Object();

    public AdminCardSecretCryptoConfigServiceImpl(AdminManagedEnvService managedEnvService) {
        this.managedEnvService = managedEnvService;
    }

    public AdminCardSecretCryptoConfigResponse config() {
        String activeKeyVersion = readActiveKeyVersion();
        return response(activeKeyVersion);
    }

    public AdminCardSecretCryptoConfigResponse updateConfig(AdminCardSecretCryptoConfigUpdateRequest request) {
        String activeKeyVersion = normalizeKeyVersion(request == null ? null : request.activeKeyVersion(), true);
        String aesKeyBase64 = normalizeRequiredValue(request == null ? null : request.aesKeyBase64(), "ADMIN_CARD_SECRET_AES_KEY_REQUIRED");
        String hmacKeyBase64 = normalizeRequiredValue(request == null ? null : request.hmacKeyBase64(), "ADMIN_CARD_SECRET_HMAC_KEY_REQUIRED");
        validateBase64Bytes(aesKeyBase64, AES_KEY_BYTES, AES_KEY_BYTES, "ADMIN_CARD_SECRET_AES_KEY_INVALID");
        validateBase64Bytes(hmacKeyBase64, HMAC_KEY_MIN_BYTES, null, "ADMIN_CARD_SECRET_HMAC_KEY_INVALID");

        synchronized (monitor) {
            writeManagedEnv(ACTIVE_KEY_VERSION_ENV, activeKeyVersion);
            writeManagedEnv(aesKeyEnv(activeKeyVersion), aesKeyBase64);
            writeManagedEnv(hmacKeyEnv(activeKeyVersion), hmacKeyBase64);
        }
        return response(activeKeyVersion);
    }

    public AdminCardSecretCryptoConfigResponse generate(AdminCardSecretCryptoGenerateRequest request) {
        String keyVersion = normalizeKeyVersion(request == null ? null : request.keyVersion(), false);
        boolean activate = request == null || request.activate() == null || request.activate();
        String aesKeyBase64 = generateKeyBase64(AES_KEY_BYTES);
        String hmacKeyBase64 = generateKeyBase64(HMAC_KEY_MIN_BYTES);

        synchronized (monitor) {
            if (activate) {
                writeManagedEnv(ACTIVE_KEY_VERSION_ENV, keyVersion);
            }
            writeManagedEnv(aesKeyEnv(keyVersion), aesKeyBase64);
            writeManagedEnv(hmacKeyEnv(keyVersion), hmacKeyBase64);
        }
        return response(activate ? keyVersion : readActiveKeyVersion());
    }

    private AdminCardSecretCryptoConfigResponse response(String activeKeyVersion) {
        String normalizedVersion = normalizeKeyVersion(activeKeyVersion, true);
        String aesKeyEnv = aesKeyEnv(normalizedVersion);
        String hmacKeyEnv = hmacKeyEnv(normalizedVersion);
        return new AdminCardSecretCryptoConfigResponse(
                normalizedVersion,
                ACTIVE_KEY_VERSION_ENV,
                buildField(aesKeyEnv, AES_KEY_BYTES, null),
                buildField(hmacKeyEnv, null, HMAC_KEY_MIN_BYTES),
                managedEnvService.envTarget(),
                managedEnvService.envTarget(),
                managedEnvService.envStoreType(),
                true
        );
    }

    private AdminCardSecretCryptoConfigField buildField(String envName,
                                                       Integer requiredDecodedBytes,
                                                       Integer minDecodedBytes) {
        String value = managedEnvService.readSystemEnvValue(envName).orElse(null);
        boolean configured = StringUtils.hasText(value);
        return new AdminCardSecretCryptoConfigField(
                envName,
                configured ? maskValue(value) : NOT_CONFIGURED,
                configured,
                requiredDecodedBytes,
                minDecodedBytes
        );
    }

    private String readActiveKeyVersion() {
        return managedEnvService.readSystemEnvValue(ACTIVE_KEY_VERSION_ENV)
                .map(value -> normalizeKeyVersion(value, true))
                .orElse(DEFAULT_KEY_VERSION);
    }

    private String normalizeKeyVersion(String value, boolean allowDefault) {
        if (!StringUtils.hasText(value)) {
            if (allowDefault) {
                return DEFAULT_KEY_VERSION;
            }
            throw new AdminServiceException(
                    "ADMIN_CARD_SECRET_KEY_VERSION_REQUIRED",
                    "Card secret key version is required.",
                    HttpStatus.BAD_REQUEST
            );
        }
        String trimmed = normalizeValue(value, "ADMIN_CARD_SECRET_KEY_VERSION_INVALID");
        if (!KEY_VERSION_PATTERN.matcher(trimmed).matches()) {
            throw new AdminServiceException(
                    "ADMIN_CARD_SECRET_KEY_VERSION_INVALID",
                    "Card secret key version must match ^v[1-9][0-9]{0,2}$.",
                    HttpStatus.BAD_REQUEST
            );
        }
        return trimmed;
    }

    private String normalizeRequiredValue(String value, String missingCode) {
        if (!StringUtils.hasText(value)) {
            throw new AdminServiceException(
                    missingCode,
                    "Card secret crypto key value is required.",
                    HttpStatus.BAD_REQUEST
            );
        }
        return normalizeValue(value, "ADMIN_CARD_SECRET_CRYPTO_CONFIG_INVALID");
    }

    private String normalizeValue(String value, String invalidCode) {
        String rawValue = value == null ? "" : value;
        if (rawValue.indexOf('\n') >= 0 || rawValue.indexOf('\r') >= 0) {
            throw new AdminServiceException(
                    invalidCode,
                    "Card secret crypto config value must not contain line breaks.",
                    HttpStatus.BAD_REQUEST
            );
        }
        String trimmed = rawValue.trim();
        if (trimmed.length() > MAX_VALUE_LENGTH) {
            throw new AdminServiceException(
                    "ADMIN_CARD_SECRET_CRYPTO_CONFIG_TOO_LONG",
                    "Card secret crypto config value length must not exceed 1024 characters.",
                    HttpStatus.BAD_REQUEST
            );
        }
        return trimmed;
    }

    private void validateBase64Bytes(String value,
                                     int minBytes,
                                     Integer exactBytes,
                                     String errorCode) {
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException ex) {
            throw new AdminServiceException(
                    errorCode,
                    "Card secret crypto key must be standard Base64.",
                    HttpStatus.BAD_REQUEST
            );
        }
        if (exactBytes != null && decoded.length != exactBytes) {
            throw new AdminServiceException(
                    errorCode,
                    "Card secret AES key must decode to exactly 32 bytes.",
                    HttpStatus.BAD_REQUEST
            );
        }
        if (decoded.length < minBytes) {
            throw new AdminServiceException(
                    errorCode,
                    "Card secret HMAC key must decode to at least 32 bytes.",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private String generateKeyBase64(int byteLength) {
        byte[] bytes = new byte[byteLength];
        secureRandom.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private void writeManagedEnv(String envName, String value) {
        managedEnvService.writeSystemEnv(
                envName,
                value,
                "ADMIN_CARD_SECRET_WINDOWS_ENV_UNSUPPORTED",
                "ADMIN_CARD_SECRET_WINDOWS_ENV_WRITE_FAILED",
                "ADMIN_CARD_SECRET_WINDOWS_ENV_WRITE_INTERRUPTED"
        );
    }

    private String aesKeyEnv(String keyVersion) {
        return AES_KEY_ENV_PREFIX + keyVersion.toUpperCase(Locale.ROOT);
    }

    private String hmacKeyEnv(String keyVersion) {
        return HMAC_KEY_ENV_PREFIX + keyVersion.toUpperCase(Locale.ROOT);
    }

    private String maskValue(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return NOT_CONFIGURED;
        }
        String value = rawValue.trim();
        int length = value.length();
        if (length <= 4) {
            return "****";
        }
        if (length <= 8) {
            return value.substring(0, 2) + "****" + value.substring(length - 2);
        }
        return value.substring(0, 4) + "****" + value.substring(length - 4);
    }
}
