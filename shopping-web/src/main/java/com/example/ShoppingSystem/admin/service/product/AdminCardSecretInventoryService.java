package com.example.ShoppingSystem.admin.service.product;

import com.example.ShoppingSystem.Utils.HybridIdCodec;
import com.example.ShoppingSystem.Utils.HybridSemaphoreIdWorker;
import com.example.ShoppingSystem.Utils.ProductSkuIdCodec;
import com.example.ShoppingSystem.admin.dto.AdminSessionMeResponse;
import com.example.ShoppingSystem.admin.dto.AdminCardSecretImportResponse;
import com.example.ShoppingSystem.admin.service.common.AdminServiceException;
import com.example.ShoppingSystem.admin.service.config.AdminCardSecretCryptoConfigService;
import com.example.ShoppingSystem.admin.service.config.impl.AdminManagedEnvService.AdminManagedEnvServiceImpl;
import com.example.ShoppingSystem.mapper.product.CardSecretInventoryMapper;
import com.example.ShoppingSystem.product.service.PublicProductDetailCacheService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public interface AdminCardSecretInventoryService {
    public AdminCardSecretImportResponse importSecrets(Long spuId,
                                                       String skuId,
                                                       String secretText,
                                                       MultipartFile file,
                                                       String batchNo,
                                                       String duplicatePolicy,
                                                       AdminSessionMeResponse adminSession);
}
