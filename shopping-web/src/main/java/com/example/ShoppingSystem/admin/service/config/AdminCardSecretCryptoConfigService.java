package com.example.ShoppingSystem.admin.service.config;

import com.example.ShoppingSystem.admin.dto.AdminCardSecretCryptoConfigField;
import com.example.ShoppingSystem.admin.dto.AdminCardSecretCryptoConfigResponse;
import com.example.ShoppingSystem.admin.dto.AdminCardSecretCryptoConfigUpdateRequest;
import com.example.ShoppingSystem.admin.dto.AdminCardSecretCryptoGenerateRequest;
import com.example.ShoppingSystem.admin.service.common.AdminServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.regex.Pattern;

public interface AdminCardSecretCryptoConfigService {
    public static final String ACTIVE_KEY_VERSION_ENV = "CARD_SECRET_ACTIVE_KEY_VERSION";

    public static final String AES_KEY_ENV_PREFIX = "CARD_SECRET_AES_KEY_";

    public static final String HMAC_KEY_ENV_PREFIX = "CARD_SECRET_HMAC_KEY_";

    public AdminCardSecretCryptoConfigResponse config();

    public AdminCardSecretCryptoConfigResponse updateConfig(AdminCardSecretCryptoConfigUpdateRequest request);

    public AdminCardSecretCryptoConfigResponse generate(AdminCardSecretCryptoGenerateRequest request);
}
