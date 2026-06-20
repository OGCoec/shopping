package com.example.ShoppingSystem.admin.service.product.impl.AdminProductSkuService;

import com.example.ShoppingSystem.Utils.HybridSemaphoreIdWorker;
import com.example.ShoppingSystem.Utils.ProductSkuIdCodec;
import com.example.ShoppingSystem.admin.dto.AdminProductSkuCreateRequest;
import com.example.ShoppingSystem.admin.dto.AdminProductSkuUpdateRequest;
import com.example.ShoppingSystem.admin.dto.AdminProductSpuDetailSkuUpdateRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import com.example.ShoppingSystem.admin.service.common.AdminServiceException;

import com.example.ShoppingSystem.admin.service.product.AdminProductSkuService;
import com.example.ShoppingSystem.admin.service.product.ProductImageUrlValidator;
@Service
public class AdminProductSkuServiceImpl implements AdminProductSkuService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_DISABLED = "DISABLED";
    private static final Set<String> SUPPORTED_STATUS = Set.of(STATUS_ACTIVE, STATUS_DISABLED);
    private static final int MAX_IMAGE_URL_LENGTH = 512;
    private static final int MAX_SKU_CODE_LENGTH = 64;
    private static final int MAX_SKU_NAME_LENGTH = 128;

    private final HybridSemaphoreIdWorker hybridSemaphoreIdWorker;
    private final ObjectMapper objectMapper;
    private final ProductImageUrlValidator productImageUrlValidator;

    public AdminProductSkuServiceImpl(HybridSemaphoreIdWorker hybridSemaphoreIdWorker,
                                  ObjectMapper objectMapper,
                                  ProductImageUrlValidator productImageUrlValidator) {
        this.hybridSemaphoreIdWorker = hybridSemaphoreIdWorker;
        this.objectMapper = objectMapper;
        this.productImageUrlValidator = productImageUrlValidator;
    }

    public List<NormalizedSkuUpdate> normalizeSkuUpdates(Long spuId, List<AdminProductSpuDetailSkuUpdateRequest> rawSkus) {
        if (rawSkus == null || rawSkus.isEmpty()) {
            return List.of();
        }
        List<NormalizedSkuUpdate> skus = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        Set<String> skuCodes = new HashSet<>();
        Set<String> skuNames = new HashSet<>();
        for (AdminProductSpuDetailSkuUpdateRequest rawSku : rawSkus) {
            if (rawSku == null) {
                throw new AdminServiceException("ADMIN_PRODUCT_SKU_REQUIRED", "SKU 不能为空。", HttpStatus.BAD_REQUEST);
            }
            String requestedId = normalizeOptionalSkuId(rawSku.id());
            byte[] requestedIdBytes = requestedId == null ? null : decodeSkuId(requestedId);
            byte[] finalIdBytes = requestedIdBytes == null ? nextHybridSkuIdBytes() : requestedIdBytes;
            String finalId = ProductSkuIdCodec.toBase62(finalIdBytes);
            if (!ids.add(finalId)) {
                throw new AdminServiceException("ADMIN_PRODUCT_SKU_DUPLICATE", "SKU ID 不能重复。", HttpStatus.BAD_REQUEST);
            }
            String skuCode = normalizeRequiredText(rawSku.skuCode(), "SKU 编码", MAX_SKU_CODE_LENGTH);
            String skuName = normalizeRequiredText(rawSku.skuName(), "SKU 名称", MAX_SKU_NAME_LENGTH);
            if (!skuCodes.add(skuCode)) {
                throw new AdminServiceException("ADMIN_PRODUCT_SKU_DUPLICATE", "SKU 编码不能重复。", HttpStatus.BAD_REQUEST);
            }
            if (!skuNames.add(skuName)) {
                throw new AdminServiceException("ADMIN_PRODUCT_SKU_DUPLICATE", "SKU 名称不能重复。", HttpStatus.BAD_REQUEST);
            }
            JsonNode specJson = normalizeJsonNode(rawSku.specJson(), false, "SKU 规格");
            JsonNode skuImageUrls = normalizeImageUrlArray(rawSku.skuImageUrls(), "SKU 图片");
            BigDecimal priceYuan = normalizePriceYuan(rawSku.priceYuan());
            BigDecimal originalPriceYuan = normalizeOriginalPriceYuan(rawSku.originalPriceYuan(), priceYuan);
            Integer stockQuantity = normalizeStockQuantity(rawSku.stockQuantity());
            String status = normalizeStatus(rawSku.status(), STATUS_ACTIVE);
            skus.add(new NormalizedSkuUpdate(
                    requestedId,
                    finalId,
                    requestedIdBytes,
                    finalIdBytes,
                    spuId,
                    skuCode,
                    skuName,
                    specJson,
                    skuImageUrls,
                    priceYuan,
                    originalPriceYuan,
                    stockQuantity,
                    status));
        }
        return List.copyOf(skus);
    }

    public NormalizedSkuUpdate normalizeSkuCreate(Long spuId, AdminProductSkuCreateRequest rawSku) {
        if (rawSku == null) {
            throw new AdminServiceException("ADMIN_PRODUCT_SKU_REQUIRED", "SKU 不能为空。", HttpStatus.BAD_REQUEST);
        }
        byte[] finalIdBytes = nextHybridSkuIdBytes();
        String finalId = ProductSkuIdCodec.toBase62(finalIdBytes);
        return normalizeSingleSku(
                null,
                finalId,
                null,
                finalIdBytes,
                spuId,
                rawSku.skuCode(),
                rawSku.skuName(),
                rawSku.specJson(),
                rawSku.skuImageUrls(),
                rawSku.priceYuan(),
                rawSku.originalPriceYuan(),
                rawSku.stockQuantity(),
                rawSku.status());
    }

    public NormalizedSkuUpdate normalizeSkuUpdate(Long spuId, String skuId, AdminProductSkuUpdateRequest rawSku) {
        if (rawSku == null) {
            throw new AdminServiceException("ADMIN_PRODUCT_SKU_REQUIRED", "SKU 不能为空。", HttpStatus.BAD_REQUEST);
        }
        String normalizedSkuId = normalizeRequiredSkuId(skuId);
        byte[] normalizedSkuIdBytes = decodeSkuId(normalizedSkuId);
        return normalizeSingleSku(
                normalizedSkuId,
                normalizedSkuId,
                normalizedSkuIdBytes,
                normalizedSkuIdBytes,
                spuId,
                rawSku.skuCode(),
                rawSku.skuName(),
                rawSku.specJson(),
                rawSku.skuImageUrls(),
                rawSku.priceYuan(),
                rawSku.originalPriceYuan(),
                rawSku.stockQuantity(),
                rawSku.status());
    }

    private NormalizedSkuUpdate normalizeSingleSku(String requestedId,
                                                   String finalId,
                                                   byte[] requestedIdBytes,
                                                   byte[] finalIdBytes,
                                                   Long spuId,
                                                   String rawSkuCode,
                                                   String rawSkuName,
                                                   JsonNode rawSpecJson,
                                                   JsonNode rawSkuImageUrls,
                                                   BigDecimal rawPriceYuan,
                                                   BigDecimal rawOriginalPriceYuan,
                                                   Integer rawStockQuantity,
                                                   String rawStatus) {
        String skuCode = normalizeRequiredText(rawSkuCode, "SKU 编码", MAX_SKU_CODE_LENGTH);
        String skuName = normalizeRequiredText(rawSkuName, "SKU 名称", MAX_SKU_NAME_LENGTH);
        JsonNode specJson = normalizeJsonNode(rawSpecJson, false, "SKU 规格");
        JsonNode skuImageUrls = normalizeImageUrlArray(rawSkuImageUrls, "SKU 图片");
        BigDecimal priceYuan = normalizePriceYuan(rawPriceYuan);
        BigDecimal originalPriceYuan = normalizeOriginalPriceYuan(rawOriginalPriceYuan, priceYuan);
        Integer stockQuantity = normalizeStockQuantity(rawStockQuantity);
        String status = normalizeStatus(rawStatus, STATUS_ACTIVE);
        return new NormalizedSkuUpdate(
                requestedId,
                finalId,
                requestedIdBytes,
                finalIdBytes,
                spuId,
                skuCode,
                skuName,
                specJson,
                skuImageUrls,
                priceYuan,
                originalPriceYuan,
                stockQuantity,
                status);
    }

    private BigDecimal normalizePriceYuan(BigDecimal rawPriceYuan) {
        if (rawPriceYuan == null || rawPriceYuan.compareTo(BigDecimal.ZERO) < 0 || rawPriceYuan.scale() > 2) {
            throw new AdminServiceException("ADMIN_PRODUCT_SKU_PRICE_INVALID", "SKU 价格无效。", HttpStatus.BAD_REQUEST);
        }
        return rawPriceYuan;
    }

    private BigDecimal normalizeOriginalPriceYuan(BigDecimal rawOriginalPriceYuan, BigDecimal priceYuan) {
        if (rawOriginalPriceYuan == null) {
            return null;
        }
        if (rawOriginalPriceYuan.compareTo(BigDecimal.ZERO) < 0 || rawOriginalPriceYuan.scale() > 2) {
            throw new AdminServiceException("ADMIN_PRODUCT_SKU_ORIGINAL_PRICE_INVALID", "SKU 原价无效。", HttpStatus.BAD_REQUEST);
        }
        if (rawOriginalPriceYuan.compareTo(priceYuan) < 0) {
            throw new AdminServiceException("ADMIN_PRODUCT_SKU_ORIGINAL_PRICE_INVALID", "SKU 原价不能小于销售价。", HttpStatus.BAD_REQUEST);
        }
        return rawOriginalPriceYuan;
    }

    private Integer normalizeStockQuantity(Integer rawStockQuantity) {
        if (rawStockQuantity == null || rawStockQuantity <= 0) {
            throw new AdminServiceException("ADMIN_PRODUCT_SKU_STOCK_INVALID", "SKU 库存必须大于 0。", HttpStatus.BAD_REQUEST);
        }
        return rawStockQuantity;
    }

    public String toSkuJson(List<NormalizedSkuUpdate> skus) {
        if (skus == null || skus.isEmpty()) {
            return "[]";
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (NormalizedSkuUpdate sku : skus) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", sku.requestedId());
            row.put("id_bytes_hex", sku.requestedIdBytes() == null ? null : ProductSkuIdCodec.toHex(sku.requestedIdBytes()));
            row.put("generated_id", sku.finalId());
            row.put("generated_id_bytes_hex", ProductSkuIdCodec.toHex(sku.finalIdBytes()));
            row.put("sku_code", sku.skuCode());
            row.put("sku_name", sku.skuName());
            row.put("spec_json", sku.specJson());
            row.put("sku_image_urls", sku.skuImageUrls());
            row.put("price_yuan", sku.priceYuan());
            row.put("original_price_yuan", sku.originalPriceYuan());
            row.put("stock_quantity", sku.stockQuantity());
            row.put("status", sku.status());
            rows.add(row);
        }
        return toJsonString(rows);
    }

    private String normalizeOptionalSkuId(String id) {
        String value = normalizeText(id);
        if (value.isEmpty()) {
            return null;
        }
        if (!value.matches(ProductSkuIdCodec.BASE62_PATTERN)) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_SKU_ID_INVALID",
                    "SKU ID 无效。",
                    HttpStatus.BAD_REQUEST);
        }
        decodeSkuId(value);
        return value;
    }

    private String normalizeRequiredSkuId(String id) {
        String value = normalizeOptionalSkuId(id);
        if (value == null) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_SKU_ID_INVALID",
                    "SKU ID 无效。",
                    HttpStatus.BAD_REQUEST);
        }
        return value;
    }

    private byte[] nextHybridSkuIdBytes() {
        return hybridSemaphoreIdWorker.nextId();
    }

    private byte[] decodeSkuId(String value) {
        try {
            return ProductSkuIdCodec.fromBase62(value);
        } catch (IllegalArgumentException e) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_SKU_ID_INVALID",
                    "SKU ID 无效。",
                    HttpStatus.BAD_REQUEST);
        }
    }

    private JsonNode normalizeJsonNode(JsonNode node, boolean array, String label) {
        JsonNode value = jsonNodeOrDefault(node, array);
        if (array && !value.isArray()) {
            throw new AdminServiceException("ADMIN_PRODUCT_JSON_INVALID", label + "必须是 JSON 数组。", HttpStatus.BAD_REQUEST);
        }
        if (!array && !value.isObject()) {
            throw new AdminServiceException("ADMIN_PRODUCT_JSON_INVALID", label + "必须是 JSON 对象。", HttpStatus.BAD_REQUEST);
        }
        return value;
    }

    private JsonNode normalizeImageUrlArray(JsonNode node, String label) {
        JsonNode value = jsonNodeOrDefault(node, true);
        if (!value.isArray()) {
            throw new AdminServiceException("ADMIN_PRODUCT_JSON_INVALID", label + "必须是 JSON 数组。", HttpStatus.BAD_REQUEST);
        }
        ArrayNode normalized = objectMapper.createArrayNode();
        Set<String> seen = new HashSet<>();
        value.elements().forEachRemaining(item -> {
            String url = extractImageUrl(item);
            if (url.isEmpty() || !seen.add(url)) {
                return;
            }
            if (url.length() > MAX_IMAGE_URL_LENGTH) {
                throw new AdminServiceException(
                        "ADMIN_PRODUCT_TEXT_TOO_LONG",
                        label + "不能超过 " + MAX_IMAGE_URL_LENGTH + " 个字符。",
                        HttpStatus.BAD_REQUEST);
            }
            url = productImageUrlValidator.validateImageUrl(url, label);
            normalized.add(url);
        });
        return normalized;
    }

    private String extractImageUrl(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        if (node.isTextual()) {
            return normalizeText(node.asText());
        }
        if (node.isObject()) {
            JsonNode urlNode = node.get("url");
            return urlNode != null && urlNode.isTextual() ? normalizeText(urlNode.asText()) : "";
        }
        return "";
    }

    private JsonNode jsonNodeOrDefault(JsonNode node, boolean array) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return array ? objectMapper.createArrayNode() : objectMapper.createObjectNode();
        }
        return node.deepCopy();
    }

    private String toJsonString(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_JSON_SERIALIZE_FAILED",
                    "商品详情 JSON 序列化失败。",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String normalizeRequiredText(String raw, String label, int maxLength) {
        String value = normalizeText(raw);
        if (value.isEmpty()) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_REQUIRED",
                    label + "不能为空。",
                    HttpStatus.BAD_REQUEST);
        }
        if (value.length() > maxLength) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_TEXT_TOO_LONG",
                    label + "不能超过 " + maxLength + " 个字符。",
                    HttpStatus.BAD_REQUEST);
        }
        return value;
    }

    private String normalizeStatus(String raw, String defaultStatus) {
        String value = normalizeText(raw);
        if (value.isEmpty()) {
            value = defaultStatus;
        }
        String status = value.toUpperCase(Locale.ROOT);
        if (!SUPPORTED_STATUS.contains(status)) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_STATUS_INVALID",
                    "商品状态只能是 ACTIVE 或 DISABLED。",
                    HttpStatus.BAD_REQUEST);
        }
        return status;
    }

    private String normalizeText(Object raw) {
        return raw == null ? "" : String.valueOf(raw).trim();
    }
}
