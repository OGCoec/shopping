package com.example.ShoppingSystem.admin.service;

import com.example.ShoppingSystem.Utils.HybridSemaphoreIdWorker;
import com.example.ShoppingSystem.admin.dto.AdminProductSpuDetailSkuUpdateRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class AdminProductSkuService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_DISABLED = "DISABLED";
    private static final Set<String> SUPPORTED_STATUS = Set.of(STATUS_ACTIVE, STATUS_DISABLED);
    private static final int MAX_IMAGE_URL_LENGTH = 512;
    private static final int MAX_SKU_CODE_LENGTH = 64;
    private static final int MAX_SKU_NAME_LENGTH = 128;
    private static final String SKU_ID_HEX_PATTERN = "^[0-9a-f]{32}$";

    private final HybridSemaphoreIdWorker hybridSemaphoreIdWorker;
    private final ObjectMapper objectMapper;

    public AdminProductSkuService(HybridSemaphoreIdWorker hybridSemaphoreIdWorker,
                                  ObjectMapper objectMapper) {
        this.hybridSemaphoreIdWorker = hybridSemaphoreIdWorker;
        this.objectMapper = objectMapper;
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
            String finalId = requestedId == null ? nextHybridSkuId() : requestedId;
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
            String skuImageUrl = normalizeNullableText(rawSku.skuImageUrl(), "SKU 图片", MAX_IMAGE_URL_LENGTH);
            BigDecimal priceYuan = rawSku.priceYuan();
            if (priceYuan == null || priceYuan.compareTo(BigDecimal.ZERO) < 0) {
                throw new AdminServiceException("ADMIN_PRODUCT_SKU_PRICE_INVALID", "SKU 价格无效。", HttpStatus.BAD_REQUEST);
            }
            BigDecimal originalPriceYuan = rawSku.originalPriceYuan();
            if (originalPriceYuan != null && originalPriceYuan.compareTo(priceYuan) < 0) {
                throw new AdminServiceException("ADMIN_PRODUCT_SKU_ORIGINAL_PRICE_INVALID", "SKU 原价不能小于销售价。", HttpStatus.BAD_REQUEST);
            }
            Integer stockQuantity = rawSku.stockQuantity();
            if (stockQuantity == null || stockQuantity < 0) {
                throw new AdminServiceException("ADMIN_PRODUCT_SKU_STOCK_INVALID", "SKU 库存无效。", HttpStatus.BAD_REQUEST);
            }
            String status = normalizeStatus(rawSku.status(), STATUS_ACTIVE);
            skus.add(new NormalizedSkuUpdate(
                    requestedId,
                    finalId,
                    spuId,
                    skuCode,
                    skuName,
                    specJson,
                    skuImageUrl,
                    priceYuan,
                    originalPriceYuan,
                    stockQuantity,
                    status));
        }
        return List.copyOf(skus);
    }

    public String toSkuJson(List<NormalizedSkuUpdate> skus) {
        if (skus == null || skus.isEmpty()) {
            return "[]";
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (NormalizedSkuUpdate sku : skus) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", sku.requestedId());
            row.put("generated_id", sku.finalId());
            row.put("sku_code", sku.skuCode());
            row.put("sku_name", sku.skuName());
            row.put("spec_json", sku.specJson());
            row.put("sku_image_url", sku.skuImageUrl());
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
        if (!value.matches(SKU_ID_HEX_PATTERN)) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_SKU_ID_INVALID",
                    "SKU ID 无效。",
                    HttpStatus.BAD_REQUEST);
        }
        return value;
    }

    private String nextHybridSkuId() {
        return HexFormat.of().formatHex(hybridSemaphoreIdWorker.nextId());
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

    private String normalizeNullableText(String raw, String label, int maxLength) {
        String value = normalizeText(raw);
        if (value.length() > maxLength) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_TEXT_TOO_LONG",
                    label + "不能超过 " + maxLength + " 个字符。",
                    HttpStatus.BAD_REQUEST);
        }
        return value.isEmpty() ? null : value;
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

    public record NormalizedSkuUpdate(String requestedId,
                                      String finalId,
                                      Long spuId,
                                      String skuCode,
                                      String skuName,
                                      JsonNode specJson,
                                      String skuImageUrl,
                                      BigDecimal priceYuan,
                                      BigDecimal originalPriceYuan,
                                      Integer stockQuantity,
                                      String status) {
        public NormalizedSkuUpdate withSkuImageUrl(String nextSkuImageUrl) {
            return new NormalizedSkuUpdate(
                    requestedId,
                    finalId,
                    spuId,
                    skuCode,
                    skuName,
                    specJson,
                    nextSkuImageUrl,
                    priceYuan,
                    originalPriceYuan,
                    stockQuantity,
                    status);
        }
    }
}
