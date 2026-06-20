package com.example.ShoppingSystem.service.user.auth.passwordreset;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.redisdata.PasswordResetRedisKeys;
import com.example.ShoppingSystem.service.user.auth.passwordreset.model.PasswordResetCryptoKey;
import com.example.ShoppingSystem.service.user.auth.passwordreset.model.PasswordResetDecryptOutcome;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
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

public interface PasswordResetCryptoService {
    public PasswordResetCryptoKey issueKey();

    public PasswordResetDecryptOutcome decryptPayload(String kid,
                                                      String payloadCipher,
                                                      String nonce,
                                                      Long timestamp);
}
