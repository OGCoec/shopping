package com.example.ShoppingSystem.product.service;

import com.example.ShoppingSystem.Utils.ProductSkuIdCodec;
import com.example.ShoppingSystem.product.dto.PublicProductDetailResponse;
import com.example.ShoppingSystem.product.dto.PublicProductSkuResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class PublicProductSpuAssembler {

    private final ObjectMapper objectMapper;

    public PublicProductSpuAssembler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public PublicProductDetailResponse toDetailResponse(Map<String, Object> row) {
        return new PublicProductDetailResponse(
                toLong(value(row, "id"), 0L),
                toLong(value(row, "categoryId"), 0L),
                toText(value(row, "categoryName")),
                toText(value(row, "name")),
                toText(value(row, "subtitle")),
                toText(value(row, "brandName")),
                toText(value(row, "mainImageUrl")),
                parseJsonNode(value(row, "imageUrlsJson"), true),
                parseJsonNode(value(row, "detailImageUrlsJson"), true),
                parseJsonNode(value(row, "attributesJson"), false),
                toText(value(row, "description")),
                toText(value(row, "afterSale")),
                toSkuResponses(value(row, "skusJson")));
    }

    private List<PublicProductSkuResponse> toSkuResponses(Object rawSkusJson) {
        JsonNode skusNode = parseJsonNode(rawSkusJson, true);
        if (!skusNode.isArray() || skusNode.isEmpty()) {
            return List.of();
        }
        List<PublicProductSkuResponse> skus = new ArrayList<>();
        for (JsonNode skuNode : skusNode) {
            int stockQuantity = jsonInt(skuNode, "stockQuantity", 0);
            skus.add(new PublicProductSkuResponse(
                    toSkuIdText(jsonText(skuNode, "id")),
                    jsonText(skuNode, "skuName"),
                    jsonNodeOrDefault(skuNode.get("specJson"), false),
                    jsonNodeOrDefault(skuNode.get("skuImageUrls"), true),
                    jsonBigDecimal(skuNode, "priceYuan", BigDecimal.ZERO),
                    jsonNullableBigDecimal(skuNode, "originalPriceYuan"),
                    stockQuantity,
                    jsonInt(skuNode, "remainingQuantity", stockQuantity),
                    jsonBoolean(skuNode, "hotSku", false)));
        }
        return List.copyOf(skus);
    }

    private Object value(Map<String, Object> row, String key) {
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

    private JsonNode parseJsonNode(Object raw, boolean array) {
        if (raw instanceof JsonNode jsonNode) {
            return jsonNodeOrDefault(jsonNode, array);
        }
        String json = toText(raw);
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

    private Long toLong(Object value, Long defaultValue) {
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

    private int jsonInt(JsonNode node, String field, int defaultValue) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            return defaultValue;
        }
        if (value.isNumber()) {
            return value.intValue();
        }
        try {
            return Integer.parseInt(value.asText());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private boolean jsonBoolean(JsonNode node, String field, boolean defaultValue) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            return defaultValue;
        }
        if (value.isBoolean()) {
            return value.booleanValue();
        }
        String text = toText(value.asText());
        return text.isEmpty() ? defaultValue : Boolean.parseBoolean(text);
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
        String text = toText(value.asText());
        if (text.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String jsonText(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            return "";
        }
        return value.isTextual() ? toText(value.asText()) : toText(value);
    }

    private String toSkuIdText(Object raw) {
        return ProductSkuIdCodec.toBase62FromDatabaseValue(raw);
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

    private String toText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
