package com.example.ShoppingSystem.admin.service.product;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.example.ShoppingSystem.admin.service.common.AdminServiceException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AdminProductCategorySearchService {

    private static final String HIGHLIGHT_START = "[[HL]]";
    private static final String HIGHLIGHT_END = "[[/HL]]";
    private static final int DEFAULT_MAX_MATCHES = 5000;
    private static final int MIN_MAX_MATCHES = 100;
    private static final int MAX_MAX_MATCHES = 10000;

    private final ElasticsearchClient elasticsearchClient;

    @Value("${shopping.admin.product-category-search.max-matches:5000}")
    private int maxMatches;

    public AdminProductCategorySearchService(ElasticsearchClient elasticsearchClient) {
        this.elasticsearchClient = elasticsearchClient;
    }

    public List<Long> searchMatchedIds(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        try {
            SearchRequest request = new SearchRequest.Builder()
                    .index(AdminProductCategoryIndexService.PRODUCT_CATEGORY_INDEX_ALIAS)
                    .size(safeMaxMatches())
                    .query(query -> query.match(match -> match.field("name").query(keyword)))
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
            log.warn("Admin product category Elasticsearch search failed, keyword={}", keyword, e);
            throw searchUnavailableException();
        }
    }

    public Map<Long, String> searchNameHighlights(String keyword, Collection<Long> displayIds) {
        List<String> ids = normalizeDocumentIds(displayIds);
        if (keyword == null || keyword.isBlank() || ids.isEmpty()) {
            return Map.of();
        }
        try {
            SearchRequest request = new SearchRequest.Builder()
                    .index(AdminProductCategoryIndexService.PRODUCT_CATEGORY_INDEX_ALIAS)
                    .size(Math.min(ids.size(), safeMaxMatches()))
                    .query(query -> query.bool(bool -> bool
                            .must(must -> must.match(match -> match.field("name").query(keyword)))
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
            log.warn("Admin product category Elasticsearch highlight query failed, keyword={}", keyword, e);
            throw searchUnavailableException();
        }
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
        Long parsedId = parseLong(hit == null ? null : hit.id(), 0L);
        if (parsedId > 0) {
            return parsedId;
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

    private int safeMaxMatches() {
        int configured = maxMatches <= 0 ? DEFAULT_MAX_MATCHES : maxMatches;
        return Math.max(MIN_MAX_MATCHES, Math.min(MAX_MAX_MATCHES, configured));
    }

    private AdminServiceException searchUnavailableException() {
        return new AdminServiceException(
                "ADMIN_PRODUCT_CATEGORY_ES_SEARCH_FAILED",
                "Category search service is temporarily unavailable. Please try again later.",
                HttpStatus.BAD_GATEWAY);
    }
}
