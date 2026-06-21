package com.example.ShoppingSystem.admin.service.product.impl.AdminCardSecretQueryService;

import com.example.ShoppingSystem.Utils.HybridIdCodec;
import com.example.ShoppingSystem.Utils.ProductSkuIdCodec;
import com.example.ShoppingSystem.admin.dto.AdminCardSecretQueryDtos.AdminCardSecretDeliveryItemResponse;
import com.example.ShoppingSystem.admin.dto.AdminCardSecretQueryDtos.AdminCardSecretDeliveryPageResponse;
import com.example.ShoppingSystem.admin.dto.AdminCardSecretQueryDtos.AdminCardSecretInventoryItemResponse;
import com.example.ShoppingSystem.admin.dto.AdminCardSecretQueryDtos.AdminCardSecretInventoryPageResponse;
import com.example.ShoppingSystem.admin.dto.AdminCardSecretQueryDtos.AdminCardSecretRevealResponse;
import com.example.ShoppingSystem.admin.dto.AdminSessionMeResponse;
import com.example.ShoppingSystem.admin.service.common.AdminPaginationValidator;
import com.example.ShoppingSystem.admin.service.common.AdminServiceException;
import com.example.ShoppingSystem.admin.service.config.AdminCardSecretCryptoConfigService;
import com.example.ShoppingSystem.admin.service.config.impl.AdminManagedEnvService.AdminManagedEnvServiceImpl;
import com.example.ShoppingSystem.mapper.product.CardSecretInventoryMapper;
import com.example.ShoppingSystem.mapper.product.ProductSpuMapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.example.ShoppingSystem.admin.service.product.AdminCardSecretQueryService;
import com.example.ShoppingSystem.admin.service.config.AdminManagedEnvService;
@Service
public class AdminCardSecretQueryServiceImpl implements AdminCardSecretQueryService {

    private static final Logger log = LoggerFactory.getLogger(AdminCardSecretQueryService.class);

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int AES_KEY_BYTES = 32;
    private static final int GCM_TAG_BITS = 128;

    private static final Set<String> INVENTORY_STATUSES = Set.of("UNUSED", "SOLD", "DISABLED");
    private static final Set<String> DELIVERY_STATUSES = Set.of("DELIVERED", "REVOKED", "REFUNDED", "REPLACED");
    private static final Set<String> ORDER_STATUSES = Set.of("STOCK_CONFIRMING", "PENDING_PAYMENT", "CLOSING", "PAID", "CANCELLED", "CLOSED");
    private static final Set<String> IMPORT_SOURCES = Set.of("TEXT_INPUT", "TXT_FILE", "MIXED");

    private final CardSecretInventoryMapper cardSecretInventoryMapper;
    private final ProductSpuMapper productSpuMapper;
    private final AdminManagedEnvService managedEnvService;

    public AdminCardSecretQueryServiceImpl(CardSecretInventoryMapper cardSecretInventoryMapper,
                                       ProductSpuMapper productSpuMapper,
                                       AdminManagedEnvService managedEnvService) {
        this.cardSecretInventoryMapper = cardSecretInventoryMapper;
        this.productSpuMapper = productSpuMapper;
        this.managedEnvService = managedEnvService;
    }

    public AdminCardSecretInventoryPageResponse inventoryPage(Integer rawPage,
                                                              Integer rawPageSize,
                                                              Long spuId,
                                                              String skuId,
                                                              String batchNo,
                                                              String inventoryStatus,
                                                              String deliveryStatus,
                                                              String orderNo,
                                                              Long userId,
                                                              String orderStatus,
                                                              Boolean createdByMe,
                                                              String createdByAdminUsername,
                                                              String importSource,
                                                              AdminSessionMeResponse currentAdmin) {
        int page = AdminPaginationValidator.normalizePage(rawPage);
        int pageSize = normalizePageSize(rawPageSize);
        byte[] skuIdBytes = decodeSkuId(skuId);
        String normalizedBatchNo = normalizeText(batchNo, 64);
        String normalizedInventoryStatus = normalizeEnum(inventoryStatus, INVENTORY_STATUSES, "ADMIN_CARD_SECRET_INVENTORY_STATUS_INVALID");
        String normalizedDeliveryStatus = normalizeEnum(deliveryStatus, DELIVERY_STATUSES, "ADMIN_CARD_SECRET_DELIVERY_STATUS_INVALID");
        String normalizedOrderNo = normalizeText(orderNo, 64);
        String normalizedOrderStatus = normalizeEnum(orderStatus, ORDER_STATUSES, "ADMIN_CARD_SECRET_ORDER_STATUS_INVALID");
        String normalizedCreatedByAdminUsername = normalizeText(createdByAdminUsername, 128);
        String normalizedImportSource = normalizeEnum(importSource, IMPORT_SOURCES, "ADMIN_CARD_SECRET_IMPORT_SOURCE_INVALID");
        AdminActor actor = adminActor(currentAdmin);
        boolean onlyMine = Boolean.TRUE.equals(createdByMe);
        List<String> skuIdHexes = skuIdHexFilter(validPositive(spuId), skuIdBytes);
        if (skuIdHexes != null && skuIdHexes.isEmpty()) {
            return new AdminCardSecretInventoryPageResponse(page, pageSize, 0, 0, List.of());
        }

        PageInfo<Map<String, Object>> pageInfo = page(page, pageSize, () ->
                cardSecretInventoryMapper.pageInventoryForAdmin(
                        validPositive(spuId),
                        skuIdBytes,
                        skuIdHexes,
                        normalizedBatchNo,
                        normalizedInventoryStatus,
                        normalizedDeliveryStatus,
                        normalizedOrderNo,
                        validPositive(userId),
                        normalizedOrderStatus,
                        onlyMine,
                        actor.available(),
                        actor.username(),
                        actor.email(),
                        actor.phone(),
                        normalizedCreatedByAdminUsername,
                        normalizedImportSource
                )
        );
        List<AdminCardSecretInventoryItemResponse> records = mergeSkuSummaries(pageInfo.getList()).stream()
                .map(this::inventoryItem)
                .toList();
        return new AdminCardSecretInventoryPageResponse(page, pageSize, pageInfo.getTotal(), pageInfo.getPages(), records);
    }

    public AdminCardSecretDeliveryPageResponse deliveryPage(Integer rawPage,
                                                            Integer rawPageSize,
                                                            Long spuId,
                                                            String skuId,
                                                            String orderNo,
                                                            Long userId,
                                                            String deliveryStatus,
                                                            String orderStatus,
                                                            Boolean createdByMe,
                                                            String createdByAdminUsername,
                                                            AdminSessionMeResponse currentAdmin) {
        int page = AdminPaginationValidator.normalizePage(rawPage);
        int pageSize = normalizePageSize(rawPageSize);
        byte[] skuIdBytes = decodeSkuId(skuId);
        String normalizedOrderNo = normalizeText(orderNo, 64);
        String normalizedDeliveryStatus = normalizeEnum(deliveryStatus, DELIVERY_STATUSES, "ADMIN_CARD_SECRET_DELIVERY_STATUS_INVALID");
        String normalizedOrderStatus = normalizeEnum(orderStatus, ORDER_STATUSES, "ADMIN_CARD_SECRET_ORDER_STATUS_INVALID");
        String normalizedCreatedByAdminUsername = normalizeText(createdByAdminUsername, 128);
        AdminActor actor = adminActor(currentAdmin);
        boolean onlyMine = Boolean.TRUE.equals(createdByMe);
        List<String> skuIdHexes = skuIdHexFilter(validPositive(spuId), skuIdBytes);
        if (skuIdHexes != null && skuIdHexes.isEmpty()) {
            return new AdminCardSecretDeliveryPageResponse(page, pageSize, 0, 0, List.of());
        }

        PageInfo<Map<String, Object>> pageInfo = page(page, pageSize, () ->
                cardSecretInventoryMapper.pageDeliveriesForAdmin(
                        validPositive(spuId),
                        skuIdBytes,
                        skuIdHexes,
                        normalizedOrderNo,
                        validPositive(userId),
                        normalizedDeliveryStatus,
                        normalizedOrderStatus,
                        onlyMine,
                        actor.available(),
                        actor.username(),
                        actor.email(),
                        actor.phone(),
                        normalizedCreatedByAdminUsername
                )
        );
        List<AdminCardSecretDeliveryItemResponse> records = mergeSkuSummaries(pageInfo.getList()).stream()
                .map(this::deliveryItem)
                .toList();
        return new AdminCardSecretDeliveryPageResponse(page, pageSize, pageInfo.getTotal(), pageInfo.getPages(), records);
    }

    public AdminCardSecretRevealResponse reveal(String cardSecretId, AdminSessionMeResponse currentAdmin) {
        byte[] decodedId = decodeCardSecretId(cardSecretId);
        Map<String, Object> row = cardSecretInventoryMapper.findRevealForAdmin(decodedId);
        if (row == null || row.isEmpty()) {
            throw new AdminServiceException(
                    "ADMIN_CARD_SECRET_NOT_FOUND",
                    "Card secret does not exist.",
                    HttpStatus.NOT_FOUND
            );
        }
        row = mergeSkuSummary(row, skuSummariesByRows(List.of(row)));
        String secret = decrypt(text(row, "secretCiphertext"), text(row, "secretNonce"), text(row, "secretKeyVersion"));
        log.info("[卡密管理] 管理员查看卡密明文，admin={}, cardSecretId={}, orderNo={}",
                currentAdmin == null ? "" : currentAdmin.username(),
                text(row, "cardSecretId"),
                text(row, "orderNo"));
        return new AdminCardSecretRevealResponse(
                text(row, "cardSecretId"),
                text(row, "skuId"),
                text(row, "skuName"),
                text(row, "inventoryStatus"),
                text(row, "deliveryStatus"),
                text(row, "orderNo"),
                text(row, "orderStatus"),
                longValue(row, "userId"),
                secret
        );
    }

    private PageInfo<Map<String, Object>> page(int page, int pageSize, PageQuery query) {
        try {
            return PageHelper.startPage(page, pageSize, true)
                    .doSelectPageInfo(query::select);
        } finally {
            PageHelper.clearPage();
        }
    }

    private List<String> skuIdHexFilter(Long spuId, byte[] skuIdBytes) {
        String skuIdHex = skuIdBytes == null ? null : ProductSkuIdCodec.toHex(skuIdBytes);
        if (spuId == null) {
            return skuIdHex == null ? null : List.of(skuIdHex);
        }
        List<String> spuSkuIdHexes = productSpuMapper.listSkuIdHexesBySpuId(spuId);
        if (spuSkuIdHexes == null || spuSkuIdHexes.isEmpty()) {
            return List.of();
        }
        if (skuIdHex == null) {
            return spuSkuIdHexes;
        }
        return spuSkuIdHexes.contains(skuIdHex) ? List.of(skuIdHex) : List.of();
    }

    private List<Map<String, Object>> mergeSkuSummaries(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        Map<String, Map<String, Object>> summariesBySkuIdHex = skuSummariesByRows(rows);
        List<Map<String, Object>> mergedRows = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            mergedRows.add(mergeSkuSummary(row, summariesBySkuIdHex));
        }
        return mergedRows;
    }

    private Map<String, Object> mergeSkuSummary(Map<String, Object> row,
                                                Map<String, Map<String, Object>> summariesBySkuIdHex) {
        String skuIdHex = text(row, "skuIdHex");
        Map<String, Object> summary = skuIdHex == null ? null : summariesBySkuIdHex.get(skuIdHex);
        if (summary == null || summary.isEmpty()) {
            return row;
        }
        Map<String, Object> merged = new LinkedHashMap<>(row);
        merged.put("skuName", value(summary, "skuName"));
        merged.put("spuId", value(summary, "spuId"));
        merged.put("spuName", value(summary, "spuName"));
        return merged;
    }

    private Map<String, Map<String, Object>> skuSummariesByRows(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        LinkedHashSet<String> skuIdHexes = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            String skuIdHex = text(row, "skuIdHex");
            if (skuIdHex != null) {
                skuIdHexes.add(skuIdHex);
            }
        }
        if (skuIdHexes.isEmpty()) {
            return Map.of();
        }
        List<byte[]> skuIds = skuIdHexes.stream()
                .map(hex -> HexFormat.of().parseHex(hex))
                .toList();
        List<Map<String, Object>> summaries = productSpuMapper.listSkuSummariesBySkuIds(skuIds);
        Map<String, Map<String, Object>> indexed = new LinkedHashMap<>();
        for (Map<String, Object> summary : summaries) {
            String skuIdHex = text(summary, "skuIdHex");
            if (skuIdHex != null) {
                indexed.put(skuIdHex, summary);
            }
        }
        return indexed;
    }

    private AdminCardSecretInventoryItemResponse inventoryItem(Map<String, Object> row) {
        return new AdminCardSecretInventoryItemResponse(
                text(row, "cardSecretId"),
                text(row, "skuId"),
                text(row, "skuName"),
                longValue(row, "spuId"),
                text(row, "spuName"),
                text(row, "batchNo"),
                text(row, "importSource"),
                text(row, "inventoryStatus"),
                text(row, "deliveryStatus"),
                text(row, "orderNo"),
                text(row, "orderStatus"),
                longValue(row, "userId"),
                offsetDateTime(row, "soldAt"),
                offsetDateTime(row, "deliveredAt"),
                offsetDateTime(row, "createdAt"),
                text(row, "createdByAdminUsername"),
                text(row, "createdByAdminEmail")
        );
    }

    private AdminCardSecretDeliveryItemResponse deliveryItem(Map<String, Object> row) {
        return new AdminCardSecretDeliveryItemResponse(
                text(row, "deliveryId"),
                text(row, "cardSecretId"),
                text(row, "skuId"),
                text(row, "skuName"),
                longValue(row, "spuId"),
                text(row, "spuName"),
                text(row, "orderNo"),
                text(row, "orderStatus"),
                longValue(row, "userId"),
                text(row, "inventoryStatus"),
                text(row, "deliveryStatus"),
                offsetDateTime(row, "deliveredAt"),
                offsetDateTime(row, "revokedAt"),
                offsetDateTime(row, "refundedAt"),
                offsetDateTime(row, "replacedAt"),
                text(row, "createdByAdminUsername")
        );
    }

    private String decrypt(String ciphertextBase64, String nonceBase64, String keyVersion) {
        byte[] aesKey = loadAesKey(keyVersion);
        byte[] ciphertext = decodeBase64(ciphertextBase64, "ADMIN_CARD_SECRET_DECRYPT_FAILED", "Card secret ciphertext is invalid.");
        byte[] nonce = decodeBase64(nonceBase64, "ADMIN_CARD_SECRET_DECRYPT_FAILED", "Card secret nonce is invalid.");
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new GCMParameterSpec(GCM_TAG_BITS, nonce));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException ex) {
            throw new AdminServiceException(
                    "ADMIN_CARD_SECRET_DECRYPT_FAILED",
                    "Card secret decrypt failed.",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    private byte[] loadAesKey(String keyVersion) {
        String normalizedVersion = normalizeKeyVersion(keyVersion);
        String envName = AdminCardSecretCryptoConfigService.AES_KEY_ENV_PREFIX + normalizedVersion;
        String value = managedEnvService.readSystemEnvValue(envName)
                .map(String::trim)
                .filter(StringUtils::hasText)
                .orElseThrow(() -> cryptoNotConfigured("Card secret AES key is not configured: " + envName));
        byte[] decoded = decodeBase64(value, "ADMIN_CARD_SECRET_CRYPTO_NOT_CONFIGURED", "Card secret AES key must be standard Base64.");
        if (decoded.length != AES_KEY_BYTES) {
            throw cryptoNotConfigured("Card secret AES key must decode to exactly 32 bytes.");
        }
        return decoded;
    }

    private String normalizeKeyVersion(String keyVersion) {
        String value = keyVersion == null ? "" : keyVersion.trim().toUpperCase();
        if (!value.matches("^V[1-9][0-9]{0,2}$")) {
            throw cryptoNotConfigured("Card secret key version is invalid.");
        }
        return value;
    }

    private byte[] decodeBase64(String value, String code, String message) {
        try {
            return Base64.getDecoder().decode(value == null ? "" : value.trim());
        } catch (IllegalArgumentException ex) {
            throw new AdminServiceException(code, message, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private AdminServiceException cryptoNotConfigured(String message) {
        return new AdminServiceException(
                "ADMIN_CARD_SECRET_CRYPTO_NOT_CONFIGURED",
                message,
                HttpStatus.BAD_REQUEST
        );
    }

    private int normalizePageSize(Integer rawPageSize) {
        return rawPageSize == null
                ? DEFAULT_PAGE_SIZE
                : AdminPaginationValidator.normalizePageSize(rawPageSize);
    }

    private byte[] decodeSkuId(String skuId) {
        String normalized = normalizeText(skuId, 22);
        if (normalized == null) {
            return null;
        }
        try {
            return ProductSkuIdCodec.fromBase62(normalized);
        } catch (IllegalArgumentException ex) {
            throw new AdminServiceException(
                    "ADMIN_CARD_SECRET_SKU_ID_INVALID",
                    "SKU ID is invalid.",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private byte[] decodeCardSecretId(String cardSecretId) {
        String normalized = normalizeText(cardSecretId, 22);
        if (normalized == null) {
            throw new AdminServiceException(
                    "ADMIN_CARD_SECRET_ID_INVALID",
                    "Card secret ID is invalid.",
                    HttpStatus.BAD_REQUEST
            );
        }
        try {
            return HybridIdCodec.fromBase62(normalized);
        } catch (IllegalArgumentException ex) {
            throw new AdminServiceException(
                    "ADMIN_CARD_SECRET_ID_INVALID",
                    "Card secret ID is invalid.",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private String normalizeEnum(String value, Set<String> allowedValues, String errorCode) {
        String normalized = normalizeText(value, 64);
        if (normalized == null) {
            return null;
        }
        String upper = normalized.toUpperCase();
        if (!allowedValues.contains(upper)) {
            throw new AdminServiceException(errorCode, "Card secret query parameter is invalid.", HttpStatus.BAD_REQUEST);
        }
        return upper;
    }

    private Long validPositive(Long value) {
        return value == null || value <= 0 ? null : value;
    }

    private String normalizeText(String value, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.indexOf('\n') >= 0 || normalized.indexOf('\r') >= 0 || normalized.length() > maxLength) {
            throw new AdminServiceException(
                    "ADMIN_CARD_SECRET_QUERY_PARAM_INVALID",
                    "Card secret query parameter is invalid.",
                    HttpStatus.BAD_REQUEST
            );
        }
        return normalized;
    }

    private AdminActor adminActor(AdminSessionMeResponse currentAdmin) {
        String username = currentAdmin == null ? null : normalizeActorValue(currentAdmin.username());
        String email = currentAdmin == null ? null : normalizeActorValue(currentAdmin.email());
        String phone = currentAdmin == null ? null : normalizeActorValue(currentAdmin.phone());
        return new AdminActor(username, email, phone, username != null || email != null || phone != null);
    }

    private String normalizeActorValue(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String text(Map<String, Object> row, String key) {
        Object value = value(row, key);
        String text = value == null ? "" : String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private Long longValue(Map<String, Object> row, String key) {
        Object value = value(row, key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = value == null ? "" : String.valueOf(value).trim();
        return text.isEmpty() ? null : Long.parseLong(text);
    }

    private OffsetDateTime offsetDateTime(Map<String, Object> row, String key) {
        Object value = value(row, key);
        if (value instanceof OffsetDateTime dateTime) {
            return dateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant().atZone(ZoneId.systemDefault()).toOffsetDateTime();
        }
        if (value instanceof Date date) {
            return date.toInstant().atZone(ZoneId.systemDefault()).toOffsetDateTime();
        }
        String text = value == null ? "" : String.valueOf(value).trim();
        return text.isEmpty() ? null : OffsetDateTime.parse(text);
    }

    private Object value(Map<String, Object> row, String key) {
        if (row == null || key == null) {
            return null;
        }
        if (row.containsKey(key)) {
            return row.get(key);
        }
        return row.get(toSnakeCase(key));
    }

    private String toSnakeCase(String value) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < value.length(); index += 1) {
            char ch = value.charAt(index);
            if (Character.isUpperCase(ch)) {
                builder.append('_').append(Character.toLowerCase(ch));
            } else {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private interface PageQuery {
        List<Map<String, Object>> select();
    }

    private record AdminActor(String username, String email, String phone, boolean available) {
    }
}
