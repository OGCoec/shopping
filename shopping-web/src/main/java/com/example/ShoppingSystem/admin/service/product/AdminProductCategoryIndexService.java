package com.example.ShoppingSystem.admin.service.product;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import com.example.ShoppingSystem.mapper.product.ProductCategoryMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class AdminProductCategoryIndexService {

    static final String PRODUCT_CATEGORY_INDEX_ALIAS = "shopping_product_category";
    private static final String PRODUCT_CATEGORY_INDEX_NAME = "shopping_product_category_v1";
    private static final int DEFAULT_PAGE_SIZE = 1000;
    private static final int MIN_PAGE_SIZE = 100;
    private static final int MAX_PAGE_SIZE = 5000;

    private static final String CREATE_INDEX_JSON = """
            {
              "mappings": {
                "dynamic": true,
                "properties": {
                  "id": { "type": "long" },
                  "parent_id": { "type": "long" },
                  "name": {
                    "type": "text",
                    "analyzer": "ik_max_word",
                    "search_analyzer": "ik_smart",
                    "fields": {
                      "keyword": { "type": "keyword", "ignore_above": 128 }
                    }
                  },
                  "code": { "type": "keyword", "ignore_above": 128 },
                  "level": { "type": "integer" },
                  "path": { "type": "keyword", "ignore_above": 512 },
                  "icon_urls_json": { "type": "keyword", "index": false },
                  "description": { "type": "keyword", "index": false },
                  "status": { "type": "keyword" },
                  "is_leaf": { "type": "boolean" },
                  "sort_order": { "type": "integer" }
                }
              }
            }
            """;

    private static final String DETAIL_MAPPING_JSON = """
            {
              "properties": {
                "icon_urls_json": { "type": "keyword", "index": false },
                "description": { "type": "keyword", "index": false },
                "is_leaf": { "type": "boolean" }
              }
            }
            """;

    private final ElasticsearchClient elasticsearchClient;
    private final RestClient restClient;
    private final ProductCategoryMapper productCategoryMapper;

    @Value("${shopping.admin.product-category-index.startup-rebuild-enabled:true}")
    private boolean startupRebuildEnabled;

    @Value("${shopping.admin.product-category-index.page-size:1000}")
    private int pageSize;

    public AdminProductCategoryIndexService(ElasticsearchClient elasticsearchClient,
                                            RestClient restClient,
                                            ProductCategoryMapper productCategoryMapper) {
        this.elasticsearchClient = elasticsearchClient;
        this.restClient = restClient;
        this.productCategoryMapper = productCategoryMapper;
    }

    public void initializeOnStartup() {
        long start = System.currentTimeMillis();
        ensureIndex();
        if (!startupRebuildEnabled) {
            log.info("Admin product category Elasticsearch startup rebuild disabled.");
            return;
        }
        int safePageSize = safePageSize();
        long offset = 0L;
        long indexed = 0L;
        while (true) {
            List<Map<String, Object>> rows = productCategoryMapper.listCategoryIndexDocuments(safePageSize, offset);
            if (rows == null || rows.isEmpty()) {
                break;
            }
            indexed += bulkIndex(rows);
            offset += rows.size();
        }
        log.info("Admin product category Elasticsearch index initialized: alias={}, indexed={}, elapsedMs={}",
                PRODUCT_CATEGORY_INDEX_ALIAS, indexed, System.currentTimeMillis() - start);
    }

    public void syncCategoriesAfterCommit(Collection<Long> categoryIds) {
        List<Long> ids = normalizeLongCollection(categoryIds);
        if (ids.isEmpty()) {
            return;
        }
        runAfterCommit(() -> syncCategories(ids));
    }

    public void deleteCategoriesAfterCommit(Collection<Long> categoryIds) {
        List<Long> ids = normalizeLongCollection(categoryIds);
        if (ids.isEmpty()) {
            return;
        }
        runAfterCommit(() -> bulkDelete(ids));
    }

    private void syncCategories(List<Long> ids) {
        int safePageSize = safePageSize();
        // Bound DB IN-list and ES bulk size; each batch still uses one mapper call and one bulk request.
        for (int start = 0; start < ids.size(); start += safePageSize) {
            List<Long> batch = ids.subList(start, Math.min(start + safePageSize, ids.size()));
            List<Map<String, Object>> rows = productCategoryMapper.listCategoryIndexDocumentsByIds(batch);
            Set<Long> foundIds = new LinkedHashSet<>();
            if (rows != null) {
                for (Map<String, Object> row : rows) {
                    Long id = toLong(value(row, "id"), 0L);
                    if (id > 0) {
                        foundIds.add(id);
                    }
                }
            }
            List<Long> missingIds = batch.stream()
                    .filter(id -> !foundIds.contains(id))
                    .toList();
            bulkWrite(rows == null ? List.of() : rows, missingIds);
        }
    }

    private long bulkIndex(List<Map<String, Object>> rows) {
        return bulkWrite(rows, List.of());
    }

    private long bulkDelete(List<Long> ids) {
        return bulkWrite(List.of(), ids);
    }

    private long bulkWrite(List<Map<String, Object>> rows, List<Long> deleteIds) {
        if ((rows == null || rows.isEmpty()) && (deleteIds == null || deleteIds.isEmpty())) {
            return 0L;
        }
        BulkRequest.Builder builder = new BulkRequest.Builder();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                Long id = toLong(value(row, "id"), 0L);
                if (id <= 0) {
                    continue;
                }
                builder.operations(operation -> operation.index(index -> index
                        .index(PRODUCT_CATEGORY_INDEX_ALIAS)
                        .id(String.valueOf(id))
                        .document(toSource(row))));
            }
        }
        if (deleteIds != null) {
            for (Long id : deleteIds) {
                if (id == null || id <= 0) {
                    continue;
                }
                builder.operations(operation -> operation.delete(delete -> delete
                        .index(PRODUCT_CATEGORY_INDEX_ALIAS)
                        .id(String.valueOf(id))));
            }
        }
        try {
            BulkResponse response = elasticsearchClient.bulk(builder.build());
            if (response.errors()) {
                log.warn("Admin product category Elasticsearch bulk write completed with errors: {}",
                        firstBulkError(response));
            }
            return response.items().size();
        } catch (IOException e) {
            log.warn("Admin product category Elasticsearch bulk write failed.", e);
            return 0L;
        }
    }

    private Map<String, Object> toSource(Map<String, Object> row) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("id", toLong(value(row, "id"), 0L));
        source.put("parent_id", toLong(value(row, "parentId"), 0L));
        source.put("name", blankToNull(toText(value(row, "name"))));
        source.put("code", blankToNull(toText(value(row, "code"))));
        source.put("level", toInt(value(row, "level"), 1));
        source.put("path", blankToNull(toText(value(row, "path"))));
        source.put("icon_urls_json", blankToNull(toText(value(row, "iconUrlsJson"))));
        source.put("description", blankToNull(toText(value(row, "description"))));
        source.put("status", blankToNull(toText(value(row, "status"))));
        source.put("is_leaf", toBoolean(value(row, "isLeaf")));
        source.put("sort_order", toInt(value(row, "sortOrder"), 0));
        return source;
    }

    private void ensureIndex() {
        try {
            ensureConcreteIndex();
            ensureAlias();
            if (!mappingContainsCategoryDetails()) {
                Request mapping = new Request("PUT", "/" + PRODUCT_CATEGORY_INDEX_NAME + "/_mapping");
                mapping.setJsonEntity(DETAIL_MAPPING_JSON);
                restClient.performRequest(mapping);
                log.info("Admin product category Elasticsearch mapping updated: index={}", PRODUCT_CATEGORY_INDEX_NAME);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Admin product category Elasticsearch index initialization failed.", e);
        }
    }

    private void ensureConcreteIndex() throws IOException {
        if (endpointExists("/" + PRODUCT_CATEGORY_INDEX_NAME)) {
            return;
        }
        Request create = new Request("PUT", "/" + PRODUCT_CATEGORY_INDEX_NAME);
        create.setJsonEntity(CREATE_INDEX_JSON);
        restClient.performRequest(create);
        log.info("Admin product category Elasticsearch concrete index created: index={}", PRODUCT_CATEGORY_INDEX_NAME);
    }

    private void ensureAlias() throws IOException {
        if (endpointExists("/" + PRODUCT_CATEGORY_INDEX_NAME + "/_alias/" + PRODUCT_CATEGORY_INDEX_ALIAS)) {
            return;
        }
        if (endpointExists("/_alias/" + PRODUCT_CATEGORY_INDEX_ALIAS)) {
            throw new IllegalStateException(
                    "Admin product category Elasticsearch alias already points to another index: alias="
                            + PRODUCT_CATEGORY_INDEX_ALIAS + ", expectedIndex=" + PRODUCT_CATEGORY_INDEX_NAME);
        }
        if (endpointExists("/" + PRODUCT_CATEGORY_INDEX_ALIAS)) {
            throw new IllegalStateException(
                    "Admin product category Elasticsearch alias name is occupied by a concrete index: alias="
                            + PRODUCT_CATEGORY_INDEX_ALIAS);
        }
        Request alias = new Request("POST", "/_aliases");
        alias.setJsonEntity("""
                {
                  "actions": [
                    { "add": { "index": "%s", "alias": "%s" } }
                  ]
                }
                """.formatted(PRODUCT_CATEGORY_INDEX_NAME, PRODUCT_CATEGORY_INDEX_ALIAS));
        restClient.performRequest(alias);
        log.info("Admin product category Elasticsearch alias added: alias={}, index={}",
                PRODUCT_CATEGORY_INDEX_ALIAS, PRODUCT_CATEGORY_INDEX_NAME);
    }

    private boolean endpointExists(String endpoint) throws IOException {
        Request request = new Request("HEAD", endpoint);
        try {
            Response response = restClient.performRequest(request);
            int statusCode = response.getStatusLine().getStatusCode();
            return statusCode >= 200 && statusCode < 300;
        } catch (ResponseException e) {
            if (e.getResponse() != null && e.getResponse().getStatusLine().getStatusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    private boolean mappingContainsCategoryDetails() throws IOException {
        Request request = new Request("GET", "/" + PRODUCT_CATEGORY_INDEX_NAME + "/_mapping");
        Response response;
        try {
            response = restClient.performRequest(request);
        } catch (ResponseException e) {
            if (e.getResponse() != null && e.getResponse().getStatusLine().getStatusCode() == 404) {
                return false;
            }
            throw e;
        }
        String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
        return body != null
                && body.contains("\"icon_urls_json\"")
                && body.contains("\"description\"")
                && body.contains("\"is_leaf\"");
    }

    private String firstBulkError(BulkResponse response) {
        if (response == null || response.items() == null) {
            return "";
        }
        for (var item : response.items()) {
            if (item.error() != null) {
                return item.error().reason();
            }
        }
        return "";
    }

    private int safePageSize() {
        return Math.max(MIN_PAGE_SIZE, Math.min(MAX_PAGE_SIZE, pageSize <= 0 ? DEFAULT_PAGE_SIZE : pageSize));
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

    private boolean toBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = toText(value);
        return "true".equalsIgnoreCase(text) || "1".equals(text);
    }

    private String toText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private void runAfterCommit(Runnable runnable) {
        if (runnable == null) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            runnable.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                runnable.run();
            }
        });
    }
}
