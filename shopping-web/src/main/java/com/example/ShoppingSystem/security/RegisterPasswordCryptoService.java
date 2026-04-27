package com.example.ShoppingSystem.security;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 注册密码前端加密服务：
 * 1) 生成临时 RSA 公私钥并写入 Redis；
 * 2) 对私钥做服务端 KEK 二次加密；
 * 3) 校验 nonce/timestamp，解密 passwordCipher。
 */
@Service
public class RegisterPasswordCryptoService {

    private static final Logger log = LoggerFactory.getLogger(RegisterPasswordCryptoService.class);

    private static final String FIELD_ALG = "alg";
    private static final String FIELD_KEY_SIZE = "key_size";
    private static final String FIELD_PUBLIC_JWK = "public_jwk";
    private static final String FIELD_PRIVATE_PKCS8_ENC = "private_pkcs8_enc";
    private static final String FIELD_CREATED_AT_MS = "created_at_ms";
    private static final String FIELD_EXPIRES_AT_MS = "expires_at_ms";

    private static final String ALG_RSA_OAEP_256 = "RSA-OAEP-256";
    private static final String RSA_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    private static final String AES_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final OAEPParameterSpec RSA_OAEP_SHA256_PARAMS = new OAEPParameterSpec(
            "SHA-256",
            "MGF1",
            MGF1ParameterSpec.SHA256,
            PSource.PSpecified.DEFAULT);
    private static final int GCM_TAG_BITS = 128;
    private static final int GCM_IV_BYTES = 12;
    private static final String DEFAULT_KEK_SEED = "shopping-register-password-crypto-dev-kek";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${register.password-crypto.enabled:true}")
    private boolean enabled;

    @Value("${register.password-crypto.redis-key-prefix:register:crypto:keypair:}")
    private String redisKeyPrefix;

    @Value("${register.password-crypto.nonce-key-prefix:register:crypto:nonce:}")
    private String nonceKeyPrefix;

    @Value("${register.password-crypto.key-ttl-seconds:1800}")
    private int keyTtlSeconds;

    @Value("${register.password-crypto.nonce-ttl-seconds:600}")
    private int nonceTtlSeconds;

    @Value("${register.password-crypto.timestamp-window-seconds:600}")
    private int timestampWindowSeconds;

    @Value("${register.password-crypto.rsa-key-size:2048}")
    private int rsaKeySize;

    @Value("${register.password-crypto.kek:${REGISTER_PASSWORD_CRYPTO_KEK:}}")
    private String kekSeed;

    public RegisterPasswordCryptoService(StringRedisTemplate stringRedisTemplate,
                                         ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public PasswordCryptoKey issuePasswordCryptoKey() {
        if (!enabled) {
            throw new IllegalStateException("register password crypto disabled");
        }

        try {
            int effectiveRsaKeySize = Math.max(2048, rsaKeySize);
            long now = System.currentTimeMillis();
            long expiresAt = now + Math.max(30, keyTtlSeconds) * 1000L;
            String kid = IdUtil.nanoId(48);

            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(effectiveRsaKeySize);
            KeyPair keyPair = keyPairGenerator.generateKeyPair();

            Map<String, Object> publicKeyJwk = buildPublicKeyJwk((RSAPublicKey) keyPair.getPublic());
            String privatePkcs8Enc = encryptPrivateKeyPkcs8(keyPair.getPrivate().getEncoded());

            Map<String, String> redisValue = new LinkedHashMap<>();
            redisValue.put(FIELD_ALG, ALG_RSA_OAEP_256);
            redisValue.put(FIELD_KEY_SIZE, String.valueOf(effectiveRsaKeySize));
            redisValue.put(FIELD_PUBLIC_JWK, objectMapper.writeValueAsString(publicKeyJwk));
            redisValue.put(FIELD_PRIVATE_PKCS8_ENC, privatePkcs8Enc);
            redisValue.put(FIELD_CREATED_AT_MS, String.valueOf(now));
            redisValue.put(FIELD_EXPIRES_AT_MS, String.valueOf(expiresAt));

            String key = redisKey(kid);
            stringRedisTemplate.opsForHash().putAll(key, redisValue);
            stringRedisTemplate.expire(key, Duration.ofSeconds(Math.max(30, keyTtlSeconds)));

            return new PasswordCryptoKey(kid, ALG_RSA_OAEP_256, publicKeyJwk, expiresAt);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to issue register password crypto key", ex);
        }
    }

    public DecryptOutcome decryptPasswordCipher(String kid,
                                                String passwordCipher,
                                                String nonce,
                                                Long timestamp,
                                                HttpServletRequest request) {
        if (!enabled) {
            return DecryptOutcome.failed("密码加密服务未启用");
        }
        if (StrUtil.hasBlank(kid, passwordCipher, nonce) || timestamp == null) {
            return DecryptOutcome.failed("密码加密参数缺失，请刷新页面后重试");
        }
        if (!isSafeToken(kid, 16, 96) || !isSafeToken(nonce, 8, 128)) {
            return DecryptOutcome.failed("密码加密参数非法，请刷新页面后重试");
        }
        if (passwordCipher.length() < 32 || passwordCipher.length() > 4096) {
            return DecryptOutcome.failed("密码密文格式非法，请刷新页面后重试");
        }

        long now = System.currentTimeMillis();
        long allowedWindowMs = Math.max(30, timestampWindowSeconds) * 1000L;
        if (Math.abs(now - timestamp) > allowedWindowMs) {
            return DecryptOutcome.failed("请求已过期，请重新提交");
        }

        String nonceKey = nonceRedisKey(kid, nonce);
        Boolean nonceAccepted = stringRedisTemplate.opsForValue().setIfAbsent(
                nonceKey,
                "1",
                Duration.ofSeconds(Math.max(30, nonceTtlSeconds)));
        if (!Boolean.TRUE.equals(nonceAccepted)) {
            return DecryptOutcome.failed("请求重复，请刷新页面后重试");
        }

        Map<Object, Object> raw = stringRedisTemplate.opsForHash().entries(redisKey(kid));
        if (raw == null || raw.isEmpty()) {
            return DecryptOutcome.failed("加密会话已失效，请重新输入密码");
        }
        String privatePkcs8Enc = asString(raw.get(FIELD_PRIVATE_PKCS8_ENC));
        Long expiresAtMs = asLong(raw.get(FIELD_EXPIRES_AT_MS));
        if (StrUtil.isBlank(privatePkcs8Enc) || expiresAtMs == null || now > expiresAtMs) {
            return DecryptOutcome.failed("加密会话已过期，请重新输入密码");
        }

        try {
            byte[] privatePkcs8 = decryptPrivateKeyPkcs8(privatePkcs8Enc);
            PrivateKey privateKey = KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(privatePkcs8));

            Cipher rsaCipher = Cipher.getInstance(RSA_TRANSFORMATION);
            rsaCipher.init(Cipher.DECRYPT_MODE, privateKey, RSA_OAEP_SHA256_PARAMS);
            byte[] rawPasswordBytes = rsaCipher.doFinal(decodeBase64Flexible(passwordCipher));
            String rawPassword = new String(rawPasswordBytes, StandardCharsets.UTF_8);
            if (StrUtil.isBlank(rawPassword)) {
                return DecryptOutcome.failed("密码解密失败，请刷新页面后重试");
            }
            return DecryptOutcome.success(rawPassword);
        } catch (Exception ex) {
            log.error("register password decrypt failed, method={}, uri={}, kid={}, nonce={}, cipherLen={}, timestamp={}",
                    request == null ? "unknown" : request.getMethod(),
                    request == null ? "unknown" : request.getRequestURI(),
                    kid,
                    nonce,
                    passwordCipher == null ? 0 : passwordCipher.length(),
                    timestamp,
                    ex);
            return DecryptOutcome.failed("密码解密失败，请刷新页面后重试");
        }
    }

    private Map<String, Object> buildPublicKeyJwk(RSAPublicKey publicKey) {
        Map<String, Object> jwk = new LinkedHashMap<>();
        jwk.put("kty", "RSA");
        jwk.put("alg", ALG_RSA_OAEP_256);
        jwk.put("use", "enc");
        jwk.put("key_ops", List.of("encrypt"));
        jwk.put("ext", true);
        jwk.put("n", encodeBase64Url(publicKey.getModulus().toByteArray()));
        jwk.put("e", encodeBase64Url(publicKey.getPublicExponent().toByteArray()));
        return jwk;
    }

    private String encryptPrivateKeyPkcs8(byte[] privatePkcs8) throws Exception {
        byte[] iv = new byte[GCM_IV_BYTES];
        secureRandom.nextBytes(iv);
        Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, resolveKek(), new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] encrypted = cipher.doFinal(privatePkcs8);
        return encodeBase64Url(iv) + "." + encodeBase64Url(encrypted);
    }

    private byte[] decryptPrivateKeyPkcs8(String encryptedPayload) throws Exception {
        String[] parts = encryptedPayload.split("\\.");
        if (parts.length != 2) {
            throw new IllegalArgumentException("invalid private key payload");
        }
        byte[] iv = decodeBase64Flexible(parts[0]);
        byte[] encrypted = decodeBase64Flexible(parts[1]);
        Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, resolveKek(), new GCMParameterSpec(GCM_TAG_BITS, iv));
        return cipher.doFinal(encrypted);
    }

    private SecretKeySpec resolveKek() throws Exception {
        String seed = StrUtil.blankToDefault(kekSeed, DEFAULT_KEK_SEED);
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(seed.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(digest, "AES");
    }

    private String redisKey(String kid) {
        return redisKeyPrefix + kid;
    }

    private String nonceRedisKey(String kid, String nonce) {
        return nonceKeyPrefix + kid + ":" + nonce;
    }

    private boolean isSafeToken(String value, int minLen, int maxLen) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
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

    private String encodeBase64Url(byte[] input) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(trimLeadingZero(input));
    }

    private byte[] decodeBase64Flexible(String input) {
        String normalized = Objects.requireNonNullElse(input, "").trim();
        normalized = normalized.replace('-', '+').replace('_', '/');
        int mod = normalized.length() % 4;
        if (mod > 0) {
            normalized += "=".repeat(4 - mod);
        }
        return Base64.getDecoder().decode(normalized);
    }

    private byte[] trimLeadingZero(byte[] bytes) {
        if (bytes == null || bytes.length <= 1 || bytes[0] != 0) {
            return bytes;
        }
        int start = 0;
        while (start < bytes.length - 1 && bytes[start] == 0) {
            start += 1;
        }
        byte[] trimmed = new byte[bytes.length - start];
        System.arraycopy(bytes, start, trimmed, 0, trimmed.length);
        return trimmed;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }

    public record PasswordCryptoKey(String kid,
                                    String alg,
                                    Map<String, Object> publicKeyJwk,
                                    long expiresAtEpochMillis) {
    }

    public record DecryptOutcome(boolean success,
                                 String rawPassword,
                                 String message) {
        public static DecryptOutcome success(String rawPassword) {
            return new DecryptOutcome(true, rawPassword, "ok");
        }

        public static DecryptOutcome failed(String message) {
            return new DecryptOutcome(false, null, message);
        }
    }
}
