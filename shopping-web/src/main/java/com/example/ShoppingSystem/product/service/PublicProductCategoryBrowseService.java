package com.example.ShoppingSystem.product.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.example.ShoppingSystem.mapper.product.ProductCategoryMapper;
import com.example.ShoppingSystem.product.dto.PublicProductCategoryTreeNodeResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class PublicProductCategoryBrowseService {

    private static final String PRODUCT_CATEGORY_INDEX_ALIAS = "shopping_product_category";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String HIGHLIGHT_START = "[[HL]]";
    private static final String HIGHLIGHT_END = "[[/HL]]";
    private static final int DEFAULT_MAX_MATCHES = 5000;
    private static final int MIN_MAX_MATCHES = 100;
    private static final int MAX_MAX_MATCHES = 10000;
    private static final int MAX_KEYWORD_LENGTH = 128;

    private final ProductCategoryMapper productCategoryMapper;
    private final ObjectMapper objectMapper;
    private final ElasticsearchClient elasticsearchClient;

    @Value("${shopping.public.product-category-search.max-matches:5000}")
    private int maxMatches;

    public PublicProductCategoryBrowseService(ProductCategoryMapper productCategoryMapper,
                                              ObjectMapper objectMapper,
                                              ElasticsearchClient elasticsearchClient) {
        this.productCategoryMapper = productCategoryMapper;
        this.objectMapper = objectMapper;
        this.elasticsearchClient = elasticsearchClient;
    }

    public List<PublicProductCategoryTreeNodeResponse> tree() {
        return buildTree(productCategoryMapper.listActivePublicCategoryRows(), Map.of());
    }

    public List<PublicProductCategoryTreeNodeResponse> search(String keyword) {
        String normalizedKeyword = normalizeKeyword(keyword);
        if (normalizedKeyword.isBlank()) {
            return tree();
        }
        List<Long> matchedIds = searchMatchedIds(normalizedKeyword);
        if (matchedIds.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> rows = productCategoryMapper.listActiveCategorySearchDisplayRows(matchedIds);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<Long> displayIds = collectIds(rows);
        Map<Long, String> highlights = searchNameHighlights(normalizedKeyword, displayIds);
        return buildTree(rows, highlights);
    }

    private List<Long> searchMatchedIds(String keyword) {
        try {
            SearchRequest request = new SearchRequest.Builder()
                    .index(PRODUCT_CATEGORY_INDEX_ALIAS)
                    .size(safeMaxMatches())
                    .query(query -> query.bool(bool -> bool
                            .must(must -> must.match(match -> match.field("name").query(keyword)))
                            .filter(filter -> filter.term(term -> term.field("status").value(STATUS_ACTIVE)))))
                    .sort(sort -> sort.score(score -> score.order(SortOrder.Desc)))
                    .sort(sort -> sort.field(field -> field.field("id").order(SortOrder.Asc)))
                    .build();
            SearchResponse<JsonNode> response = elasticsearchClient.search(request, JsonNode.class);
            LinkedHashSet<Long> ids = new LinkedHashSet<>();
            for (Hit<JsonNode> hit : response.hits().hits()) {
                Long id = parseCategoryId(hit);
                if (id > 0) {
                    ids.add(id);
                }
            }
            return List.copyOf(ids);
        } catch (IOException | ElasticsearchException e) {
            log.warn("Public product category Elasticsearch search failed, keyword={}", keyword, e);
            throw searchUnavailableException();
        }
    }

    private Map<Long, String> searchNameHighlights(String keyword, Collection<Long> displayIds) {
        List<String> ids = normalizeDocumentIds(displayIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        try {
            SearchRequest request = new SearchRequest.Builder()
                    .index(PRODUCT_CATEGORY_INDEX_ALIAS)
                    .size(Math.min(ids.size(), safeMaxMatches()))
                    .query(query -> query.bool(bool -> bool
                            .must(must -> must.match(match -> match.field("name").query(keyword)))
                            .filter(filter -> filter.term(term -> term.field("status").value(STATUS_ACTIVE)))
                            .filter(filter -> filter.ids(idsQuery -> idsQuery.values(ids)))))
                    .highlight(highlight -> highlight
                            .preTags(HIGHLIGHT_START)
                            .postTags(HIGHLIGHT_END)
                            .fields("name", field -> field))
                    .sort(sort -> sort.score(score -> score.order(SortOrder.Desc)))
                    .sort(sort -> sort.field(field -> field.field("id").order(SortOrder.Asc)))
                    .build();
            SearchResponse<JsonNode> response = elasticsearchClient.search(request, JsonNode.class);
            Map<Long, String> highlights = new LinkedHashMap<>();
            for (Hit<JsonNode> hit : response.hits().hits()) {
                Long id = parseCategoryId(hit);
                String highlight = firstNameHighlight(hit);
                if (id > 0 && highlight != null && !highlight.isBlank()) {
                    highlights.put(id, highlight);
                }
            }
            return highlights;
        } catch (IOException | ElasticsearchException e) {
            log.warn("Public product category Elasticsearch highlight query failed, keyword={}", keyword, e);
            throw searchUnavailableException();
        }
    }

    private List<PublicProductCategoryTreeNodeResponse> buildTree(List<Map<String, Object>> rows,
                                                                  Map<Long, String> highlights) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        Map<Long, MutableCategoryNode> nodes = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            MutableCategoryNode node = toNode(row, highlights);
            if (node.id <= 0) {
                continue;
            }
            nodes.put(node.id, node);
        }

        List<MutableCategoryNode> roots = new ArrayList<>();
        for (MutableCategoryNode node : nodes.values()) {
            MutableCategoryNode parent = nodes.get(node.parentId);
            if (parent == null) {
                roots.add(node);
            } else {
                parent.children.add(node);
            }
        }
        return roots.stream()
                .map(MutableCategoryNode::toResponse)
                .toList();
    }

    private MutableCategoryNode toNode(Map<String, Object> row, Map<Long, String> highlights) {
        Long id = toLong(value(row, "id"), 0L);
        return new MutableCategoryNode(
                id,
                toLong(value(row, "parentId"), 0L),
                toText(value(row, "name")),
                highlights.get(id),
                toText(value(row, "code")),
                toInt(value(row, "level"), 1),
                parseIconUrls(toText(value(row, "iconUrlsJson"))));
    }

    private List<Long> collectIds(List<Map<String, Object>> rows) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            Long id = toLong(value(row, "id"), 0L);
            if (id > 0) {
                ids.add(id);
            }
        }
        return List.copyOf(ids);
    }

    private List<String> normalizeDocumentIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .map(String::valueOf)
                .toList();
    }

    private Long parseCategoryId(Hit<JsonNode> hit) {
        Long id = parseLong(hit == null ? null : hit.id(), 0L);
        if (id > 0) {
            return id;
        }
        JsonNode source = hit == null ? null : hit.source();
        JsonNode idNode = source == null ? null : source.get("id");
        if (idNode == null || idNode.isNull()) {
            return 0L;
        }
        return idNode.isNumber() ? idNode.longValue() : parseLong(idNode.asText(), 0L);
    }

    private String firstNameHighlight(Hit<JsonNode> hit) {
        List<String> values = hit == null || hit.highlight() == null ? null : hit.highlight().get("name");
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.getFirst();
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

    private String normalizeKeyword(String keyword) {
        String value = keyword == null ? "" : keyword.trim();
        if (value.length() <= MAX_KEYWORD_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_KEYWORD_LENGTH);
    }

    private int safeMaxMatches() {
        int configured = maxMatches <= 0 ? DEFAULT_MAX_MATCHES : maxMatches;
        return Math.max(MIN_MAX_MATCHES, Math.min(MAX_MAX_MATCHES, configured));
    }

    private ResponseStatusException searchUnavailableException() {
        return new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Category search service is temporarily unavailable. Please try again later.");
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

    private Long toLong(Object value, Long defaultValue) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return parseLong(toText(value), defaultValue);
    }

    private Long parseLong(String raw, Long defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private Integer toInt(Object value, Integer defaultValue) {
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

    private String toText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static final class MutableCategoryNode {
        private final Long id;
        private final Long parentId;
        private final String name;
        private final String nameHighlight;
        private final String code;
        private final Integer level;
        private final JsonNode iconUrls;
        private final List<MutableCategoryNode> children = new ArrayList<>();

        private MutableCategoryNode(Long id,
                                    Long parentId,
                                    String name,
                                    String nameHighlight,
                                    String code,
                                    Integer level,
                                    JsonNode iconUrls) {
            this.id = id;
            this.parentId = parentId;
            this.name = name;
            this.nameHighlight = nameHighlight;
            this.code = code;
            this.level = level;
            this.iconUrls = iconUrls;
        }

        private PublicProductCategoryTreeNodeResponse toResponse() {
            return new PublicProductCategoryTreeNodeResponse(
                    String.valueOf(id),
                    name,
                    nameHighlight,
                    code,
                    level,
                    iconUrls,
                    children.stream()
                            .map(MutableCategoryNode::toResponse)
                            .toList());
        }
    }
}
