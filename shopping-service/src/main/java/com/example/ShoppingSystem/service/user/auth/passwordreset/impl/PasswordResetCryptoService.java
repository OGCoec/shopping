package com.example.ShoppingSystem.service.user.auth.passwordreset.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.redisdata.PasswordResetRedisKeys;
import com.example.ShoppingSystem.service.user.auth.passwordreset.model.PasswordResetCryptoKey;
import com.example.ShoppingSystem.service.user.auth.passwordreset.model.PasswordResetDecryptOutcome;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PasswordResetCryptoService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetCryptoService.class);

    private static final String ALG_RSA_OAEP_256 = "RSA-OAEP-256";
    private static final String RSA_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    private static final OAEPParameterSpec RSA_OAEP_SHA256_PARAMS = new OAEPParameterSpec(
            "SHA-256",
            "MGF1",
            MGF1ParameterSpec.SHA256,
            PSource.PSpecified.DEFAULT);

    private static final String FIELD_ALG = "alg";
    private static final String FIELD_PUBLIC_JWK = "public_jwk";
    private static final String FIELD_PRIVATE_PKCS8 = "private_pkcs8";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public PasswordResetCryptoService(StringRedisTemplate stringRedisTemplate,
                                      ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    public PasswordResetCryptoKey issueKey() {
        try {
            String kid = IdUtil.nanoId(48);
            long ttlSeconds = Duration.ofMinutes(PasswordResetRedisKeys.CRYPTO_KEY_TTL_MINUTES).toSeconds();
            long expiresAt = System.currentTimeMillis() + ttlSeconds * 1000L;

            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();

            Map<String, Object> publicJwk = buildPublicKeyJwk((RSAPublicKey) keyPair.getPublic());
            Map<String, String> redisValue = new LinkedHashMap<>();
            redisValue.put(FIELD_ALG, ALG_RSA_OAEP_256);
            redisValue.put(FIELD_PUBLIC_JWK, objectMapper.writeValueAsString(publicJwk));
            redisValue.put(FIELD_PRIVATE_PKCS8, encodeBase64Url(keyPair.getPrivate().getEncoded()));

            String redisKey = PasswordResetRedisKeys.CRYPTO_KEY_PREFIX + kid;
            stringRedisTemplate.opsForHash().putAll(redisKey, redisValue);
            stringRedisTemplate.expire(redisKey, Duration.ofSeconds(ttlSeconds));
            return new PasswordResetCryptoKey(kid, ALG_RSA_OAEP_256, publicJwk, expiresAt);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to issue password reset crypto key", ex);
        }
    }

    public PasswordResetDecryptOutcome decryptPayload(String kid,
                                                      String payloadCipher,
                                                      String nonce,
                                                      Long timestamp) {
        if (StrUtil.hasBlank(kid, payloadCipher, nonce) || timestamp == null) {
            return PasswordResetDecryptOutcome.failed("Password encryption parameters are missing.");
        }
        if (!isSafeToken(kid, 16, 96) || !isSafeToken(nonce, 8, 128)) {
            return PasswordResetDecryptOutcome.failed("Password encryption parameters are invalid.");
        }
        if (payloadCipher.length() < 32 || payloadCipher.length() > 4096) {
            return PasswordResetDecryptOutcome.failed("Encrypted password payload is invalid.");
        }

        long now = System.currentTimeMillis();
        long allowedWindowMs = Duration.ofMinutes(PasswordResetRedisKeys.CRYPTO_KEY_TTL_MINUTES).toMillis();
        if (Math.abs(now - timestamp) > allowedWindowMs) {
            return PasswordResetDecryptOutcome.failed("Password encryption session expired.");
        }

        Boolean nonceAccepted = stringRedisTemplate.opsForValue().setIfAbsent(
                PasswordResetRedisKeys.CRYPTO_NONCE_PREFIX + kid + ":" + nonce,
                "1",
                Duration.ofMinutes(PasswordResetRedisKeys.CRYPTO_KEY_TTL_MINUTES));
        if (!Boolean.TRUE.equals(nonceAccepted)) {
            return PasswordResetDecryptOutcome.failed("Password reset request was repeated.");
        }

        Map<Object, Object> raw = stringRedisTemplate.opsForHash()
                .entries(PasswordResetRedisKeys.CRYPTO_KEY_PREFIX + kid);
        String privatePkcs8 = raw == null ? "" : asString(raw.get(FIELD_PRIVATE_PKCS8));
        if (StrUtil.isBlank(privatePkcs8)) {
            return PasswordResetDecryptOutcome.failed("Password encryption session expired.");
        }

        try {
            PrivateKey privateKey = KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(decodeBase64Flexible(privatePkcs8)));
            Cipher cipher = Cipher.getInstance(RSA_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, privateKey, RSA_OAEP_SHA256_PARAMS);
            byte[] payloadBytes = cipher.doFinal(decodeBase64Flexible(payloadCipher));
            String payload = new String(payloadBytes, StandardCharsets.UTF_8);
            if (StrUtil.isBlank(payload)) {
                return PasswordResetDecryptOutcome.failed("Password payload decrypt failed.");
            }
            return PasswordResetDecryptOutcome.success(payload);
        } catch (Exception ex) {
            log.warn("Password reset payload decrypt failed, kid={}, cipherLen={}", kid, payloadCipher.length(), ex);
            return PasswordResetDecryptOutcome.failed("Password payload decrypt failed.");
        }
    }

    private Map<String, Object> buildPublicKeyJwk(RSAPublicKey publicKey) {
        Map<String, Object> jwk = new LinkedHashMap<>();
        jwk.put("kty", "RSA");
        jwk.put("alg", ALG_RSA_OAEP_256);
        jwk.put("use", "enc");
        jwk.put("key_ops", List.of("encrypt"));
        jwk.put("ext", true);
        jwk.put("n", encodeBase64Url(unsigned(publicKey.getModulus().toByteArray())));
        jwk.put("e", encodeBase64Url(unsigned(publicKey.getPublicExponent().toByteArray())));
        return jwk;
    }

    private byte[] unsigned(byte[] value) {
        if (value != null && value.length > 1 && value[0] == 0) {
            return Arrays.copyOfRange(value, 1, value.length);
        }
        return value == null ? new byte[0] : value;
    }

    private boolean isSafeToken(String value, int minLen, int maxLen) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.length() < minLen || trimmed.length() > maxLen) {
            return false;
        }
        for (int index = 0; index < trimmed.length(); index += 1) {
            char current = trimmed.charAt(index);
            boolean accepted = (current >= 'a' && current <= 'z')
                    || (current >= 'A' && current <= 'Z')
                    || (current >= '0' && current <= '9')
                    || current == '-' || current == '_';
            if (!accepted) {
                return false;
            }
        }
        return true;
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String encodeBase64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private byte[] decodeBase64Flexible(String value) {
        String normalized = StrUtil.blankToDefault(value, "").trim();
        Base64.Decoder decoder = normalized.contains("+") || normalized.contains("/")
                ? Base64.getDecoder()
                : Base64.getUrlDecoder();
        return decoder.decode(normalized);
    }
}
