package com.example.ShoppingSystem.product.service.impl.PublicProductSearchService;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.example.ShoppingSystem.product.dto.PublicProductSearchResponse;
import com.example.ShoppingSystem.product.dto.PublicProductSummaryResponse;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.example.ShoppingSystem.product.service.PublicProductSearchService;
@Slf4j
@Service
public class PublicProductSearchServiceImpl implements PublicProductSearchService {

    private static final String PRODUCT_SPU_INDEX_ALIAS = "shopping_product_spu";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String HIGHLIGHT_START = "[[HL]]";
    private static final String HIGHLIGHT_END = "[[/HL]]";
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_KEYWORD_LENGTH = 128;

    private final ElasticsearchClient elasticsearchClient;

    public PublicProductSearchServiceImpl(ElasticsearchClient elasticsearchClient) {
        this.elasticsearchClient = elasticsearchClient;
    }

    public PublicProductSearchResponse search(String keyword, Long categoryId, int page, int pageSize) {
        String normalizedKeyword = normalizeKeyword(keyword);
        Long normalizedCategoryId = normalizeCategoryId(categoryId);
        int safePage = page <= 0 ? DEFAULT_PAGE : page;
        int safePageSize = pageSize <= 0 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        int from = Math.max(0, (safePage - 1) * safePageSize);
        boolean hasKeyword = !normalizedKeyword.isBlank();

        BoolQuery.Builder bool = new BoolQuery.Builder()
                .filter(filter -> filter.term(term -> term.field("status").value(STATUS_ACTIVE)));
        if (hasKeyword) {
            bool.must(must -> must.match(match -> match.field("name").query(normalizedKeyword)));
        }
        if (normalizedCategoryId != null) {
            bool.filter(filter -> filter.term(term -> term.field("category_id").value(normalizedCategoryId)));
        }

        try {
            SearchRequest.Builder search = new SearchRequest.Builder()
                    .index(PRODUCT_SPU_INDEX_ALIAS)
                    .from(from)
                    .size(safePageSize)
                    .trackTotalHits(track -> track.enabled(true))
                    .query(new Query.Builder().bool(bool.build()).build());
            if (hasKeyword) {
                search.sort(sort -> sort.score(score -> score.order(SortOrder.Desc)));
                search.highlight(highlight -> highlight
                        .preTags(HIGHLIGHT_START)
                        .postTags(HIGHLIGHT_END)
                        .fields("name", field -> field));
            }
            search.sort(sort -> sort.field(field -> field.field("id").order(SortOrder.Desc)));

            SearchResponse<JsonNode> response = elasticsearchClient.search(search.build(), JsonNode.class);
            long total = response.hits().total() == null
                    ? response.hits().hits().size()
                    : response.hits().total().value();
            List<PublicProductSummaryResponse> records = new ArrayList<>();
            for (Hit<JsonNode> hit : response.hits().hits()) {
                PublicProductSummaryResponse product = toResponse(hit);
                if (product.id() == null || product.id() <= 0) {
                    continue;
                }
                records.add(product);
            }
            return new PublicProductSearchResponse(total, safePage, safePageSize, records);
        } catch (IOException | ElasticsearchException e) {
            log.warn("Public product Elasticsearch search failed, keyword={}, categoryId={}",
                    normalizedKeyword, normalizedCategoryId, e);
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Product search service is temporarily unavailable. Please try again later.");
        }
    }

    private PublicProductSummaryResponse toResponse(Hit<JsonNode> hit) {
        JsonNode source = hit == null ? null : hit.source();
        return new PublicProductSummaryResponse(
                parseId(hit == null ? null : hit.id()),
                jsonLong(source, "category_id", 0L),
                jsonText(source, "category_name"),
                jsonText(source, "name"),
                firstNameHighlight(hit),
                jsonText(source, "subtitle"),
                jsonText(source, "brand_name"),
                jsonText(source, "main_image_url"));
    }

    private String normalizeKeyword(String keyword) {
        String value = keyword == null ? "" : keyword.trim();
        if (value.length() <= MAX_KEYWORD_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_KEYWORD_LENGTH);
    }

    private Long normalizeCategoryId(Long categoryId) {
        return categoryId == null || categoryId <= 0 ? null : categoryId;
    }

    private Long parseId(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private String firstNameHighlight(Hit<JsonNode> hit) {
        List<String> values = hit == null || hit.highlight() == null ? null : hit.highlight().get("name");
        if (values == null || values.isEmpty()) {
            return null;
        }
        String value = values.getFirst();
        return value == null || value.isBlank() ? null : value;
    }

    private Long jsonLong(JsonNode node, String field, Long defaultValue) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            return defaultValue;
        }
        if (value.isNumber()) {
            return value.longValue();
        }
        try {
            return Long.parseLong(value.asText());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private String jsonText(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            return "";
        }
        return value.isTextual() ? value.asText().trim() : String.valueOf(value).trim();
    }
}
