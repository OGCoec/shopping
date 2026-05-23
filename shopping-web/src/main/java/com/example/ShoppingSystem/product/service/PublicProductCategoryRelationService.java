package com.example.ShoppingSystem.product.service;

import com.example.ShoppingSystem.mapper.ProductCategoryMapper;
import com.example.ShoppingSystem.product.dto.ProductCategoryRelationResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class PublicProductCategoryRelationService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String RELATION_KEY_PREFIX = "shopping:category:public:relation:";
    private static final String RELATION_KEY_PATTERN = RELATION_KEY_PREFIX + "*";
    private static final String CLEANUP_PENDING_KEY = RELATION_KEY_PREFIX + "cleanup-pending";
    private static final Duration RELATION_TTL = Duration.ofMinutes(30);
    private static final Duration CLEANUP_PENDING_TTL = Duration.ofMinutes(10);
    private static final String DELETE_RELATION_KEYS_LUA = """
            local cursor = '0'
            local deleted = 0
            repeat
                local result = redis.call('SCAN', cursor, 'MATCH', ARGV[1], 'COUNT', ARGV[2])
                cursor = result[1]
                local keys = result[2]
                if #keys > 0 then
                    deleted = deleted + redis.call('DEL', unpack(keys))
                end
            until cursor == '0'
            return deleted
            """;
    private static final DefaultRedisScript<Long> DELETE_RELATION_KEYS_SCRIPT =
            new DefaultRedisScript<>(DELETE_RELATION_KEYS_LUA, Long.class);

    private final ProductCategoryMapper productCategoryMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public PublicProductCategoryRelationService(ProductCategoryMapper productCategoryMapper,
                                                StringRedisTemplate stringRedisTemplate,
                                                ObjectMapper objectMapper) {
        this.productCategoryMapper = productCategoryMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    public ProductCategoryRelationResponse getRelation(Long id) {
        Long categoryId = normalizeRelationId(id);
        String key = relationKey(categoryId);
        ProductCategoryRelationResponse cached = readCachedRelation(key);
        if (cached != null) {
            return cached;
        }
        Map<Long, ProductCategoryRelationResponse> rebuilt = rebuildAndCacheRelations();
        ProductCategoryRelationResponse relation = rebuilt.get(categoryId);
        if (relation == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "分类不存在。");
        }
        return relation;
    }

    public void evictAfterCommit() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            invalidateRelationsSafely();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                invalidateRelationsSafely();
            }
        });
    }

    @Scheduled(fixedDelayString = "${shopping.category.public-relation-cleanup-delay-ms:600000}",
            initialDelayString = "${shopping.category.public-relation-cleanup-initial-delay-ms:120000}")
    public void retryPendingInvalidation() {
        Boolean pending;
        try {
            pending = stringRedisTemplate.hasKey(CLEANUP_PENDING_KEY);
        } catch (Exception e) {
            log.warn("[商品分类缓存] 读取 public relation 缓存清理补偿标记失败", e);
            return;
        }
        if (!Boolean.TRUE.equals(pending)) {
            return;
        }
        invalidateRelationsSafely();
    }

    private ProductCategoryRelationResponse readCachedRelation(String key) {
        String raw;
        try {
            raw = stringRedisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("[商品分类缓存] 读取 public relation 缓存失败，key={}", key, e);
            return null;
        }
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(raw, ProductCategoryRelationResponse.class);
        } catch (JsonProcessingException e) {
            log.warn("[商品分类缓存] public relation 缓存 JSON 无效，key={}", key, e);
            return null;
        }
    }

    private Map<Long, ProductCategoryRelationResponse> rebuildAndCacheRelations() {
        List<Map<String, Object>> rows = productCategoryMapper.listActivePublicCategoryRows();
        Map<Long, PublicCategoryNode> nodes = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            PublicCategoryNode node = toPublicNode(row);
            nodes.put(node.id(), node);
        }

        Map<Long, List<String>> childrenByParent = new LinkedHashMap<>();
        for (PublicCategoryNode node : nodes.values()) {
            Long parentId = nodes.containsKey(node.parentId()) ? node.parentId() : 0L;
            childrenByParent.computeIfAbsent(parentId, ignored -> new ArrayList<>()).add(String.valueOf(node.id()));
        }

        Map<Long, ProductCategoryRelationResponse> relations = new LinkedHashMap<>();
        relations.put(0L, new ProductCategoryRelationResponse(
                null,
                "0",
                List.copyOf(childrenByParent.getOrDefault(0L, List.of()))));
        for (PublicCategoryNode node : nodes.values()) {
            Long parentId = nodes.containsKey(node.parentId()) ? node.parentId() : 0L;
            relations.put(node.id(), new ProductCategoryRelationResponse(
                    new ProductCategoryRelationResponse.CategorySelf(
                            String.valueOf(node.id()),
                            node.name(),
                            node.code(),
                            node.level(),
                            node.iconUrls(),
                            node.status()),
                    String.valueOf(parentId),
                    List.copyOf(childrenByParent.getOrDefault(node.id(), List.of()))));
        }
        writeRelationsToRedis(relations);
        return relations;
    }

    private void writeRelationsToRedis(Map<Long, ProductCategoryRelationResponse> relations) {
        if (relations.isEmpty()) {
            return;
        }
        Map<String, String> cacheValues = new LinkedHashMap<>();
        for (Map.Entry<Long, ProductCategoryRelationResponse> entry : relations.entrySet()) {
            try {
                cacheValues.put(relationKey(entry.getKey()), objectMapper.writeValueAsString(entry.getValue()));
            } catch (JsonProcessingException e) {
                log.warn("[商品分类缓存] public relation 序列化失败，categoryId={}", entry.getKey(), e);
            }
        }
        if (cacheValues.isEmpty()) {
            return;
        }
        try {
            stringRedisTemplate.executePipelined(new SessionCallback<Object>() {
                @Override
                @SuppressWarnings({"rawtypes", "unchecked"})
                public Object execute(RedisOperations operations) {
                    cacheValues.forEach((key, value) -> operations.opsForValue().set(key, value, RELATION_TTL));
                    return null;
                }
            });
        } catch (Exception e) {
            log.warn("[商品分类缓存] public relation 批量写入 Redis 失败，count={}", cacheValues.size(), e);
        }
    }

    private void invalidateRelationsSafely() {
        try {
            Long deleted = stringRedisTemplate.execute(
                    DELETE_RELATION_KEYS_SCRIPT,
                    Collections.emptyList(),
                    RELATION_KEY_PATTERN,
                    "500");
            log.info("[商品分类缓存] public relation 缓存已清理，deleted={}", deleted == null ? 0 : deleted);
        } catch (Exception e) {
            log.warn("[商品分类缓存] public relation 缓存清理失败，写入补偿标记", e);
            markCleanupPending();
        }
    }

    private void markCleanupPending() {
        try {
            stringRedisTemplate.opsForValue().set(CLEANUP_PENDING_KEY, "1", CLEANUP_PENDING_TTL);
        } catch (Exception e) {
            log.warn("[商品分类缓存] 写入 public relation 缓存清理补偿标记失败", e);
        }
    }

    private PublicCategoryNode toPublicNode(Map<String, Object> row) {
        return new PublicCategoryNode(
                toLong(value(row, "id"), 0L),
                toLong(value(row, "parentId"), 0L),
                toText(value(row, "name")),
                toText(value(row, "code")),
                toInt(value(row, "level"), 1),
                parseIconUrls(toText(value(row, "iconUrlsJson"))),
                toText(value(row, "status")));
    }

    private JsonNode parseIconUrls(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            return objectMapper.createArrayNode();
        }
        try {
            JsonNode node = objectMapper.readTree(value);
            return node != null && node.isArray() ? node : objectMapper.createArrayNode();
        } catch (JsonProcessingException e) {
            return objectMapper.createArrayNode();
        }
    }

    private Long normalizeRelationId(Long id) {
        if (id == null || id < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分类 ID 无效。");
        }
        return id;
    }

    private String relationKey(Long categoryId) {
        return RELATION_KEY_PREFIX + categoryId;
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

    private int toInt(Object value, int defaultValue) {
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

    private record PublicCategoryNode(Long id,
                                      Long parentId,
                                      String name,
                                      String code,
                                      Integer level,
                                      JsonNode iconUrls,
                                      String status) {
    }
}
