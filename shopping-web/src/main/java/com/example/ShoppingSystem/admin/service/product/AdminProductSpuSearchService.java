package com.example.ShoppingSystem.admin.service.product;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.example.ShoppingSystem.admin.dto.AdminProductSpuPageResponse;
import com.example.ShoppingSystem.admin.dto.AdminProductSpuResponse;
import com.example.ShoppingSystem.admin.service.common.AdminServiceException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class AdminProductSpuSearchService {

    private static final String HIGHLIGHT_START = "[[HL]]";
    private static final String HIGHLIGHT_END = "[[/HL]]";

    private final ElasticsearchClient elasticsearchClient;

    public AdminProductSpuSearchService(ElasticsearchClient elasticsearchClient) {
        this.elasticsearchClient = elasticsearchClient;
    }

    public AdminProductSpuPageResponse searchPage(String name,
                                                  Long categoryId,
                                                  String status,
                                                  int page,
                                                  int pageSize) {
        int from = Math.max(0, (page - 1) * pageSize);
        boolean hasName = name != null && !name.isBlank();
        BoolQuery.Builder bool = new BoolQuery.Builder();
        if (hasName) {
            bool.must(must -> must.match(match -> match.field("name").query(name)));
        }
        if (categoryId != null) {
            bool.filter(filter -> filter.term(term -> term.field("category_id").value(categoryId)));
        }
        if (status != null && !status.isBlank()) {
            bool.filter(filter -> filter.term(term -> term.field("status").value(status)));
        }

        try {
            SearchRequest.Builder search = new SearchRequest.Builder()
                    .index(AdminProductSpuIndexService.PRODUCT_SPU_INDEX_ALIAS)
                    .from(from)
                    .size(pageSize)
                    .trackTotalHits(track -> track.enabled(true))
                    .query(buildQuery(bool, hasName, categoryId, status));
            if (hasName) {
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
            List<AdminProductSpuResponse> records = new ArrayList<>();
            for (Hit<JsonNode> hit : response.hits().hits()) {
                AdminProductSpuResponse product = toResponse(hit);
                if (product.id() == null || product.id() <= 0) {
                    continue;
                }
                String nameHighlight = firstNameHighlight(hit);
                records.add(new AdminProductSpuResponse(
                        product.id(),
                        product.categoryId(),
                        product.categoryName(),
                        product.name(),
                        product.subtitle(),
                        product.brandName(),
                        product.mainImageUrl(),
                        product.status(),
                        null,
                        null,
                        nameHighlight == null || nameHighlight.isBlank() ? null : nameHighlight));
            }
            return new AdminProductSpuPageResponse(total, page, pageSize, records);
        } catch (IOException | ElasticsearchException e) {
            log.warn("Admin product SPU Elasticsearch page query failed, name={}", name, e);
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_ES_SEARCH_FAILED",
                    "Product search service is temporarily unavailable. Please try again later.",
                    HttpStatus.BAD_GATEWAY);
        }
    }

    private Query buildQuery(BoolQuery.Builder bool, boolean hasName, Long categoryId, String status) {
        if (hasName || categoryId != null || (status != null && !status.isBlank())) {
            return new Query.Builder().bool(bool.build()).build();
        }
        return new Query.Builder().matchAll(matchAll -> matchAll).build();
    }

    private AdminProductSpuResponse toResponse(Hit<JsonNode> hit) {
        JsonNode source = hit.source();
        return new AdminProductSpuResponse(
                parseSpuId(hit.id()),
                jsonLong(source, "category_id", 0L),
                jsonText(source, "category_name"),
                jsonText(source, "name"),
                jsonText(source, "subtitle"),
                jsonText(source, "brand_name"),
                jsonText(source, "main_image_url"),
                jsonText(source, "status"),
                null,
                null,
                null);
    }

    private Long parseSpuId(String rawId) {
        try {
            return Long.parseLong(rawId);
        } catch (NumberFormatException e) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_ES_RESULT_INVALID",
                    "Product search result is invalid. Please rebuild product index.",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String firstNameHighlight(Hit<JsonNode> hit) {
        List<String> values = hit.highlight() == null ? null : hit.highlight().get("name");
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.getFirst();
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
