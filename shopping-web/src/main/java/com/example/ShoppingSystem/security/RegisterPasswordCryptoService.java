package com.example.ShoppingSystem.security;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
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

public interface RegisterPasswordCryptoService {
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

    public boolean isEnabled();

    public PasswordCryptoKey issuePasswordCryptoKey();

    public DecryptOutcome decryptPasswordCipher(String kid,
                                                String passwordCipher,
                                                String nonce,
                                                Long timestamp,
                                                HttpServletRequest request);
}
