package com.example.ShoppingSystem.admin.service.product.impl.AdminCardSecretInventoryService;

import com.example.ShoppingSystem.Utils.HybridIdCodec;
import com.example.ShoppingSystem.Utils.HybridSemaphoreIdWorker;
import com.example.ShoppingSystem.Utils.ProductSkuIdCodec;
import com.example.ShoppingSystem.admin.dto.AdminSessionMeResponse;
import com.example.ShoppingSystem.admin.dto.AdminCardSecretImportResponse;
import com.example.ShoppingSystem.admin.service.common.AdminServiceException;
import com.example.ShoppingSystem.admin.service.config.AdminCardSecretCryptoConfigService;
import com.example.ShoppingSystem.admin.service.config.impl.AdminManagedEnvService.AdminManagedEnvServiceImpl;
import com.example.ShoppingSystem.mapper.product.CardSecretInventoryMapper;
import com.example.ShoppingSystem.mapper.product.OrderProductSkuMapper;
import com.example.ShoppingSystem.mapper.product.ProductSpuMapper;
import com.example.ShoppingSystem.product.service.PublicProductDetailCacheService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
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

import com.example.ShoppingSystem.admin.service.product.AdminCardSecretInventoryService;
import com.example.ShoppingSystem.admin.service.config.AdminManagedEnvService;
import com.example.ShoppingSystem.admin.service.product.AdminProductDetailCacheService;
import com.example.ShoppingSystem.admin.service.product.AdminProductSpuIndexService;
import com.example.ShoppingSystem.admin.service.product.CardSecretInventoryInsertItem;
@Service
public class AdminCardSecretInventoryServiceImpl implements AdminCardSecretInventoryService {

    private static final int MAX_IMPORT_LINES = 10_000;
    private static final int MAX_SECRET_LENGTH = 8192;
    private static final int MAX_BATCH_NO_LENGTH = 64;
    private static final int MAX_ADMIN_USERNAME_LENGTH = 128;
    private static final int MAX_ADMIN_EMAIL_LENGTH = 255;
    private static final int MAX_ADMIN_PHONE_LENGTH = 64;
    private static final int AES_KEY_BYTES = 32;
    private static final int HMAC_KEY_MIN_BYTES = 32;
    private static final int GCM_NONCE_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final String DUPLICATE_POLICY_SKIP = "SKIP_DUPLICATE";
    private static final String IMPORT_SOURCE_TEXT_INPUT = "TEXT_INPUT";
    private static final String IMPORT_SOURCE_TXT_FILE = "TXT_FILE";
    private static final String IMPORT_SOURCE_MIXED = "MIXED";

    private final CardSecretInventoryMapper cardSecretInventoryMapper;
    private final ProductSpuMapper productSpuMapper;
    private final OrderProductSkuMapper orderProductSkuMapper;
    private final AdminManagedEnvService managedEnvService;
    private final AdminProductDetailCacheService detailCacheService;
    private final PublicProductDetailCacheService publicDetailCacheService;
    private final AdminProductSpuIndexService productIndexService;
    private final HybridSemaphoreIdWorker hybridSemaphoreIdWorker;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    public AdminCardSecretInventoryServiceImpl(CardSecretInventoryMapper cardSecretInventoryMapper,
                                           ProductSpuMapper productSpuMapper,
                                           OrderProductSkuMapper orderProductSkuMapper,
                                           AdminManagedEnvService managedEnvService,
                                           AdminProductDetailCacheService detailCacheService,
                                           PublicProductDetailCacheService publicDetailCacheService,
                                           AdminProductSpuIndexService productIndexService,
                                           HybridSemaphoreIdWorker hybridSemaphoreIdWorker,
                                           ObjectMapper objectMapper) {
        this.cardSecretInventoryMapper = cardSecretInventoryMapper;
        this.productSpuMapper = productSpuMapper;
        this.orderProductSkuMapper = orderProductSkuMapper;
        this.managedEnvService = managedEnvService;
        this.detailCacheService = detailCacheService;
        this.publicDetailCacheService = publicDetailCacheService;
        this.productIndexService = productIndexService;
        this.hybridSemaphoreIdWorker = hybridSemaphoreIdWorker;
        this.objectMapper = objectMapper;
    }

    public AdminCardSecretImportResponse importSecrets(Long spuId,
                                                       String skuId,
                                                       String secretText,
                                                       MultipartFile file,
                                                       String batchNo,
                                                       String duplicatePolicy,
                                                       AdminSessionMeResponse adminSession) {
        validateDuplicatePolicy(duplicatePolicy);
        byte[] skuIdBytes = decodeSkuId(skuId);
        validateSpuId(spuId);

        ParsedSecretLines parsed = parseSecrets(secretText, file);
        if (parsed.receivedLineCount() == 0) {
            throw new AdminServiceException(
                    "ADMIN_CARD_SECRET_IMPORT_EMPTY",
                    "secretText or file is required.",
                    HttpStatus.BAD_REQUEST
            );
        }
        if (parsed.uniqueSecrets().isEmpty()) {
            throw new AdminServiceException(
                    "ADMIN_CARD_SECRET_IMPORT_NO_VALID_LINE",
                    "No valid card secret line found.",
                    HttpStatus.BAD_REQUEST
            );
        }

        String finalBatchNo = normalizeBatchNo(batchNo);
        CryptoKeys cryptoKeys = loadCryptoKeys();
        String skuIdHex = ProductSkuIdCodec.toHex(skuIdBytes);
        ImportActor importActor = importActor(adminSession);
        String importSource = importSource(secretText, file);
        Map<String, Object> skuRow = productSpuMapper.findSkuBySpuIdAndSkuId(spuId, skuIdBytes);
        if (skuRow == null || skuRow.isEmpty()) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_SKU_NOT_FOUND",
                    "SKU does not exist or does not belong to this SPU.",
                    HttpStatus.NOT_FOUND
            );
        }
        List<CardSecretInventoryInsertItem> items = encryptSecrets(
                parsed.uniqueSecrets(),
                skuIdHex,
                finalBatchNo,
                cryptoKeys,
                importSource,
                importActor
        );
        Map<String, Object> result = cardSecretInventoryMapper.batchInsertIgnoreDuplicates(spuId, skuIdBytes, toJson(items));
        int insertedCount = intValue(result == null ? null : result.get("insertedCount"));
        int duplicateInDbCount = Math.max(0, items.size() - insertedCount);
        if (insertedCount > 0) {
            orderProductSkuMapper.increaseNormalSkuStock(skuIdBytes, insertedCount);
        }
        int skuStockQuantity = intValue(skuRow.get("stockQuantity")) + insertedCount;
        invalidateSkuProductAfterCommit(spuId);
        return new AdminCardSecretImportResponse(
                spuId,
                skuId,
                finalBatchNo,
                parsed.receivedLineCount(),
                parsed.blankLineCount(),
                parsed.duplicateInRequestCount(),
                items.size(),
                insertedCount,
                duplicateInDbCount,
                0,
                insertedCount,
                skuStockQuantity
        );
    }

    private void validateDuplicatePolicy(String duplicatePolicy) {
        String normalized = duplicatePolicy == null ? "" : duplicatePolicy.trim();
        if (normalized.isEmpty() || DUPLICATE_POLICY_SKIP.equalsIgnoreCase(normalized)) {
            return;
        }
        throw new AdminServiceException(
                "ADMIN_CARD_SECRET_DUPLICATE_POLICY_INVALID",
                "duplicatePolicy only supports SKIP_DUPLICATE.",
                HttpStatus.BAD_REQUEST
        );
    }

    private byte[] decodeSkuId(String skuId) {
        try {
            return ProductSkuIdCodec.fromBase62(skuId == null ? "" : skuId.trim());
        } catch (IllegalArgumentException ex) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_SKU_INVALID",
                    "SKU ID is invalid.",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private void validateSpuId(Long spuId) {
        if (spuId == null || spuId <= 0) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_SPU_ID_INVALID",
                    "SPU ID is invalid.",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private ParsedSecretLines parseSecrets(String secretText, MultipartFile file) {
        LineAccumulator accumulator = new LineAccumulator();
        if (secretText != null && !secretText.isEmpty()) {
            addTextLines(secretText, accumulator);
        }
        if (file != null && !file.isEmpty()) {
            validateTextFile(file);
            try {
                addTextLines(new String(file.getBytes(), StandardCharsets.UTF_8), accumulator);
            } catch (IOException ex) {
                throw new AdminServiceException(
                        "ADMIN_CARD_SECRET_FILE_INVALID",
                        "Card secret file cannot be read.",
                        HttpStatus.BAD_REQUEST
                );
            }
        }
        return accumulator.toParsed();
    }

    private String importSource(String secretText, MultipartFile file) {
        boolean textPresent = StringUtils.hasText(secretText);
        boolean filePresent = file != null && !file.isEmpty();
        if (textPresent && filePresent) {
            return IMPORT_SOURCE_MIXED;
        }
        return filePresent ? IMPORT_SOURCE_TXT_FILE : IMPORT_SOURCE_TEXT_INPUT;
    }

    private void addTextLines(String text, LineAccumulator accumulator) {
        String[] lines = text.split("\\R", -1);
        for (int index = 0; index < lines.length; index += 1) {
            String line = lines[index];
            if (index == 0 && line.startsWith("\uFEFF")) {
                line = line.substring(1);
            }
            accumulator.add(line);
        }
    }

    private void validateTextFile(MultipartFile file) {
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().trim().toLowerCase(Locale.ROOT);
        String contentType = file.getContentType() == null ? "" : file.getContentType().trim().toLowerCase(Locale.ROOT);
        boolean textContent = contentType.startsWith("text/");
        boolean textFilename = filename.endsWith(".txt");
        if (!textContent && !textFilename) {
            throw new AdminServiceException(
                    "ADMIN_CARD_SECRET_FILE_INVALID",
                    "Card secret file must be a text file.",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private String normalizeBatchNo(String batchNo) {
        if (!StringUtils.hasText(batchNo)) {
            return "CS" + HybridIdCodec.toBase62(hybridSemaphoreIdWorker.nextId());
        }
        String raw = batchNo;
        if (raw.indexOf('\n') >= 0 || raw.indexOf('\r') >= 0) {
            throw new AdminServiceException(
                    "ADMIN_CARD_SECRET_BATCH_NO_INVALID",
                    "batchNo must not contain line breaks.",
                    HttpStatus.BAD_REQUEST
            );
        }
        String normalized = raw.trim();
        if (normalized.isEmpty() || normalized.length() > MAX_BATCH_NO_LENGTH) {
            throw new AdminServiceException(
                    "ADMIN_CARD_SECRET_BATCH_NO_INVALID",
                    "batchNo length must be 1-64 characters.",
                    HttpStatus.BAD_REQUEST
            );
        }
        return normalized;
    }

    private CryptoKeys loadCryptoKeys() {
        String keyVersion = managedEnvService.readSystemEnvValue(AdminCardSecretCryptoConfigService.ACTIVE_KEY_VERSION_ENV)
                .map(String::trim)
                .filter(StringUtils::hasText)
                .orElseThrow(() -> cryptoNotConfigured("Active card secret key version is not configured."));
        if (!keyVersion.matches("^v[1-9][0-9]{0,2}$")) {
            throw cryptoNotConfigured("Active card secret key version is invalid.");
        }
        String versionSuffix = keyVersion.toUpperCase(Locale.ROOT);
        String aesKeyEnv = AdminCardSecretCryptoConfigService.AES_KEY_ENV_PREFIX + versionSuffix;
        String hmacKeyEnv = AdminCardSecretCryptoConfigService.HMAC_KEY_ENV_PREFIX + versionSuffix;
        byte[] aesKey = decodeKey(aesKeyEnv, AES_KEY_BYTES, AES_KEY_BYTES);
        byte[] hmacKey = decodeKey(hmacKeyEnv, HMAC_KEY_MIN_BYTES, null);
        return new CryptoKeys(keyVersion, aesKey, hmacKey);
    }

    private byte[] decodeKey(String envName, int minBytes, Integer exactBytes) {
        String value = managedEnvService.readSystemEnvValue(envName)
                .map(String::trim)
                .filter(StringUtils::hasText)
                .orElseThrow(() -> cryptoNotConfigured("Card secret crypto key is not configured: " + envName));
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException ex) {
            throw cryptoNotConfigured("Card secret crypto key must be standard Base64: " + envName);
        }
        if (exactBytes != null && decoded.length != exactBytes) {
            throw cryptoNotConfigured("Card secret AES key must decode to exactly 32 bytes.");
        }
        if (decoded.length < minBytes) {
            throw cryptoNotConfigured("Card secret HMAC key must decode to at least 32 bytes.");
        }
        return decoded;
    }

    private AdminServiceException cryptoNotConfigured(String message) {
        return new AdminServiceException(
                "ADMIN_CARD_SECRET_CRYPTO_NOT_CONFIGURED",
                message,
                HttpStatus.BAD_REQUEST
        );
    }

    private List<CardSecretInventoryInsertItem> encryptSecrets(List<String> secrets,
                                                               String skuIdHex,
                                                               String batchNo,
                                                               CryptoKeys cryptoKeys,
                                                               String importSource,
                                                               ImportActor importActor) {
        List<CardSecretInventoryInsertItem> items = new ArrayList<>(secrets.size());
        for (String secret : secrets) {
            EncryptedSecret encrypted = encryptSecret(secret, cryptoKeys);
            items.add(new CardSecretInventoryInsertItem(
                    HybridIdCodec.toHex(hybridSemaphoreIdWorker.nextId()),
                    skuIdHex,
                    batchNo,
                    encrypted.ciphertext(),
                    encrypted.nonce(),
                    hmacSecret(secret, cryptoKeys.hmacKey()),
                    cryptoKeys.keyVersion(),
                    importSource,
                    importActor.username(),
                    importActor.email(),
                    importActor.phone()
            ));
        }
        return items;
    }

    private ImportActor importActor(AdminSessionMeResponse adminSession) {
        if (adminSession == null || !adminSession.authenticated()) {
            return new ImportActor(null, null, null);
        }
        return new ImportActor(
                normalizeNullableText(adminSession.username(), MAX_ADMIN_USERNAME_LENGTH),
                normalizeNullableText(adminSession.email(), MAX_ADMIN_EMAIL_LENGTH),
                normalizeNullableText(adminSession.phone(), MAX_ADMIN_PHONE_LENGTH)
        );
    }

    private String normalizeNullableText(String value, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        normalized = normalized
                .replace("\r", "")
                .replace("\n", "");
        return normalized.length() > maxLength ? normalized.substring(0, maxLength) : normalized;
    }

    private EncryptedSecret encryptSecret(String secret, CryptoKeys cryptoKeys) {
        byte[] nonce = new byte[GCM_NONCE_BYTES];
        secureRandom.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cryptoKeys.aesKey(), "AES"), new GCMParameterSpec(GCM_TAG_BITS, nonce));
            byte[] ciphertext = cipher.doFinal(secret.getBytes(StandardCharsets.UTF_8));
            return new EncryptedSecret(
                    Base64.getEncoder().encodeToString(ciphertext),
                    Base64.getEncoder().encodeToString(nonce)
            );
        } catch (GeneralSecurityException ex) {
            throw new AdminServiceException(
                    "ADMIN_CARD_SECRET_CRYPTO_FAILED",
                    "Card secret encryption failed.",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    private String hmacSecret(String secret, byte[] hmacKey) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacKey, "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(secret.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new AdminServiceException(
                    "ADMIN_CARD_SECRET_CRYPTO_FAILED",
                    "Card secret hash failed.",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new AdminServiceException(
                    "ADMIN_CARD_SECRET_IMPORT_SERIALIZE_FAILED",
                    "Card secret import payload serialize failed.",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    private int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private boolean booleanValue(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private void invalidateSkuProductAfterCommit(Long spuId) {
        detailCacheService.invalidateAfterCommit(List.of(spuId));
        publicDetailCacheService.invalidateAfterCommit(List.of(spuId));
        productIndexService.syncProductsAfterCommit(List.of(spuId));
    }

    private record CryptoKeys(String keyVersion, byte[] aesKey, byte[] hmacKey) {
    }

    private record EncryptedSecret(String ciphertext, String nonce) {
    }

    private record ImportActor(String username, String email, String phone) {
    }

    private record ParsedSecretLines(List<String> uniqueSecrets,
                                     int receivedLineCount,
                                     int blankLineCount,
                                     int duplicateInRequestCount) {
    }

    private class LineAccumulator {

        private final Map<String, Boolean> unique = new LinkedHashMap<>();
        private int receivedLineCount;
        private int blankLineCount;
        private int duplicateInRequestCount;

        void add(String rawLine) {
            receivedLineCount += 1;
            if (receivedLineCount > MAX_IMPORT_LINES) {
                throw new AdminServiceException(
                        "ADMIN_CARD_SECRET_FILE_INVALID",
                        "Card secret import supports at most 10000 lines.",
                        HttpStatus.BAD_REQUEST
                );
            }
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isEmpty()) {
                blankLineCount += 1;
                return;
            }
            if (line.length() > MAX_SECRET_LENGTH) {
                throw new AdminServiceException(
                        "ADMIN_CARD_SECRET_LINE_INVALID",
                        "Card secret line length must be 1-8192 characters.",
                        HttpStatus.BAD_REQUEST
                );
            }
            if (unique.putIfAbsent(line, Boolean.TRUE) != null) {
                duplicateInRequestCount += 1;
            }
        }

        ParsedSecretLines toParsed() {
            return new ParsedSecretLines(
                    List.copyOf(unique.keySet()),
                    receivedLineCount,
                    blankLineCount,
                    duplicateInRequestCount
            );
        }
    }
}
