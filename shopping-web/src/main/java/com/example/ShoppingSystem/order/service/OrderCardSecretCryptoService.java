package com.example.ShoppingSystem.order.service;

import com.example.ShoppingSystem.admin.service.config.AdminCardSecretCryptoConfigService;
import com.example.ShoppingSystem.admin.service.config.impl.AdminManagedEnvService.AdminManagedEnvServiceImpl;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.Locale;

public interface OrderCardSecretCryptoService {
    public String decrypt(String ciphertextBase64, String nonceBase64, String keyVersion);
}
