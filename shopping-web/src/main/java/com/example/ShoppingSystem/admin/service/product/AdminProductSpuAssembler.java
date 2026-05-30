package com.example.ShoppingSystem.admin.service.product;

import com.example.ShoppingSystem.Utils.ProductSkuIdCodec;
import com.example.ShoppingSystem.admin.dto.AdminProductSpuDetailResponse;
import com.example.ShoppingSystem.admin.dto.AdminProductSpuDetailSkuResponse;
import com.example.ShoppingSystem.admin.dto.AdminProductSpuResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Component
public class AdminProductSpuAssembler {

    private final ObjectMapper objectMapper;

    public AdminProductSpuAssembler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AdminProductSpuResponse toSpuResponse(Map<String, Object> row) {
        return toSpuResponse(row, null);
    }

    public AdminProductSpuResponse toSpuResponse(Map<String, Object> row, String nameHighlight) {
        return new AdminProductSpuResponse(
                toLong(value(row, "id"), 0L),
                toLong(value(row, "categoryId"), 0L),
                toText(value(row, "categoryName")),
                toText(value(row, "name")),
                toText(value(row, "subtitle")),
                toText(value(row, "brandName")),
                toText(value(row, "mainImageUrl")),
                toText(value(row, "status")),
                toOffsetDateTime(value(row, "createdAt")),
                toOffsetDateTime(value(row, "updatedAt")),
                normalizeText(nameHighlight).isEmpty() ? null : nameHighlight);
    }

    public AdminProductSpuDetailResponse toSpuDetailResponse(Map<String, Object> row) {
        return new AdminProductSpuDetailResponse(
                toLong(value(row, "id"), 0L),
                toLong(value(row, "categoryId"), 0L),
                toText(value(row, "categoryName")),
                toText(value(row, "name")),
                toText(value(row, "subtitle")),
                toText(value(row, "brandName")),
                toText(value(row, "mainImageUrl")),
                toText(value(row, "status")),
                toOffsetDateTime(value(row, "createdAt")),
                toOffsetDateTime(value(row, "updatedAt")),
                parseJsonNode(value(row, "imageUrlsJson"), true),
                parseJsonNode(value(row, "detailImageUrlsJson"), true),
                parseJsonNode(value(row, "attributesJson"), false),
                toText(value(row, "description")),
                toText(value(row, "afterSale")),
                toSkuResponses(value(row, "skusJson")));
    }

    public List<Long> parseLongList(Object raw) {
        if (raw instanceof Collection<?> values) {
            return normalizeLongCollection(values.stream()
                    .map(value -> toLong(value, 0L))
                    .toList());
        }
        String json = normalizeText(raw);
        if (json.isEmpty() || "[]".equals(json)) {
            return List.of();
        }
        try {
            List<Long> values = objectMapper.readValue(json, new TypeReference<>() {
            });
            return normalizeLongCollection(values);
        } catch (Exception e) {
            try {
                List<String> values = objectMapper.readValue(json, new TypeReference<>() {
                });
                return normalizeLongCollection(values.stream().map(value -> toLong(value, 0L)).toList());
            } catch (Exception ignored) {
                return List.of();
            }
        }
    }

    public List<String> parseStringList(Object raw) {
        if (raw instanceof Collection<?> values) {
            return normalizeStringCollection(values);
        }
        String json = normalizeText(raw);
        if (json.isEmpty() || "[]".equals(json)) {
            return List.of();
        }
        try {
            List<String> values = objectMapper.readValue(json, new TypeReference<>() {
            });
            return normalizeStringCollection(values);
        } catch (Exception e) {
            return List.of();
        }
    }

    public Object value(Map<String, Object> row, String key) {
        if (row == null || key == null) {
            return null;
        }
        if (row.containsKey(key)) {
            return row.get(key);
        }
        String snakeKey = toSnakeCase(key);
        if (row.containsKey(snakeKey)) {
            return row.get(snakeKey);
        }
        return null;
    }

    public Long toLong(Object value, Long defaultValue) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = toText(value);
        if (text.isEmpty()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public int toInt(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = toText(value);
        if (text.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public boolean toBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = toText(value);
        return "true".equalsIgnoreCase(text) || "1".equals(text);
    }

    public BigDecimal toBigDecimal(Object value, BigDecimal defaultValue) {
        BigDecimal parsed = toNullableBigDecimal(value);
        return parsed == null ? defaultValue : parsed;
    }

    public BigDecimal toNullableBigDecimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        String text = toText(value);
        if (text.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public String toText(Object value) {
        return normalizeText(value);
    }

    public AdminProductSpuDetailSkuResponse toSkuResponse(Map<String, Object> row) {
        if (row == null || row.isEmpty()) {
            return null;
        }
        return new AdminProductSpuDetailSkuResponse(
                toSkuIdText(value(row, "id")),
                toLong(value(row, "spuId"), 0L),
                toText(value(row, "skuCode")),
                toText(value(row, "skuName")),
                parseJsonNode(value(row, "specJson"), false),
                parseJsonNode(value(row, "skuImageUrls"), true),
                toBigDecimal(value(row, "priceYuan"), BigDecimal.ZERO),
                toNullableBigDecimal(value(row, "originalPriceYuan")),
                toInt(value(row, "stockQuantity"), 0),
                toText(value(row, "status")));
    }

    private List<AdminProductSpuDetailSkuResponse> toSkuResponses(Object rawSkusJson) {
        JsonNode skusNode = parseJsonNode(rawSkusJson, true);
        if (!skusNode.isArray() || skusNode.isEmpty()) {
            return List.of();
        }
        List<AdminProductSpuDetailSkuResponse> skus = new ArrayList<>();
        for (JsonNode skuNode : skusNode) {
            skus.add(new AdminProductSpuDetailSkuResponse(
                    toSkuIdText(jsonText(skuNode, "id")),
                    jsonLong(skuNode, "spuId", 0L),
                    jsonText(skuNode, "skuCode"),
                    jsonText(skuNode, "skuName"),
                    jsonNodeOrDefault(skuNode.get("specJson"), false),
                    jsonNodeOrDefault(skuNode.get("skuImageUrls"), true),
                    jsonBigDecimal(skuNode, "priceYuan", BigDecimal.ZERO),
                    jsonNullableBigDecimal(skuNode, "originalPriceYuan"),
                    jsonInt(skuNode, "stockQuantity", 0),
                    jsonText(skuNode, "status")));
        }
        return List.copyOf(skus);
    }

    private JsonNode parseJsonNode(Object raw, boolean array) {
        if (raw instanceof JsonNode jsonNode) {
            return jsonNodeOrDefault(jsonNode, array);
        }
        String json = normalizeText(raw);
        if (json.isEmpty()) {
            return array ? objectMapper.createArrayNode() : objectMapper.createObjectNode();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node == null || (array && !node.isArray()) || (!array && !node.isObject())) {
                return array ? objectMapper.createArrayNode() : objectMapper.createObjectNode();
            }
            return node;
        } catch (JsonProcessingException e) {
            return array ? objectMapper.createArrayNode() : objectMapper.createObjectNode();
        }
    }

    private JsonNode jsonNodeOrDefault(JsonNode node, boolean array) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return array ? objectMapper.createArrayNode() : objectMapper.createObjectNode();
        }
        return node.deepCopy();
    }

    private Long jsonLong(JsonNode node, String field, Long defaultValue) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            return defaultValue;
        }
        if (value.isNumber()) {
            return value.longValue();
        }
        return toLong(value.asText(), defaultValue);
    }

    private BigDecimal jsonBigDecimal(JsonNode node, String field, BigDecimal defaultValue) {
        BigDecimal value = jsonNullableBigDecimal(node, field);
        return value == null ? defaultValue : value;
    }

    private BigDecimal jsonNullableBigDecimal(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            return value.decimalValue();
        }
        String text = normalizeText(value.asText());
        if (text.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int jsonInt(JsonNode node, String field, int defaultValue) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            return defaultValue;
        }
        if (value.isNumber()) {
            return value.intValue();
        }
        return toInt(value.asText(), defaultValue);
    }

    private String jsonText(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            return "";
        }
        return value.isTextual() ? normalizeText(value.asText()) : normalizeText(value);
    }

    private String toSkuIdText(Object raw) {
        return ProductSkuIdCodec.toBase62FromDatabaseValue(raw);
    }

    private List<Long> normalizeLongCollection(Collection<Long> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && value > 0)
                .distinct()
                .toList();
    }

    private List<String> normalizeStringCollection(Collection<?> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .map(this::normalizeText)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
    }

    private String toSnakeCase(String key) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < key.length(); index += 1) {
            char ch = key.charAt(index);
            if (Character.isUpperCase(ch)) {
                builder.append('_').append(Character.toLowerCase(ch));
            } else {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private String normalizeText(Object raw) {
        return raw == null ? "" : String.valueOf(raw).trim();
    }

    private OffsetDateTime toOffsetDateTime(Object value) {
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime;
        }
        String text = toText(value);
        if (text.isEmpty()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(text);
        } catch (Exception e) {
            return null;
        }
    }
}
