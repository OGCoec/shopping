package com.example.ShoppingSystem.admin.service.product;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import com.example.ShoppingSystem.admin.dto.AdminProductSpuResponse;
import com.example.ShoppingSystem.mapper.product.ProductSpuMapper;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class AdminProductSpuIndexService {

    static final String PRODUCT_SPU_INDEX_ALIAS = "shopping_product_spu";
    private static final String PRODUCT_SPU_INDEX_NAME = "shopping_product_spu_v1";
    private static final int DEFAULT_PAGE_SIZE = 1000;
    private static final int MIN_PAGE_SIZE = 100;
    private static final int MAX_PAGE_SIZE = 5000;

    private static final String CREATE_INDEX_JSON = """
            {
              "mappings": {
                "dynamic": true,
                "properties": {
                  "id": { "type": "long" },
                  "category_id": { "type": "long" },
                  "category_name": { "type": "keyword", "ignore_above": 128 },
                  "name": {
                    "type": "text",
                    "analyzer": "ik_max_word",
                    "search_analyzer": "ik_smart",
                    "fields": {
                      "keyword": { "type": "keyword", "ignore_above": 256 }
                    }
                  },
                  "subtitle": {
                    "type": "text",
                    "analyzer": "ik_max_word",
                    "search_analyzer": "ik_smart",
                    "fields": {
                      "keyword": { "type": "keyword", "ignore_above": 256 }
                    }
                  },
                  "brand_name": {
                    "type": "text",
                    "analyzer": "ik_max_word",
                    "search_analyzer": "ik_smart",
                    "fields": {
                      "keyword": { "type": "keyword", "ignore_above": 128 }
                    }
                  },
                  "main_image_url": { "type": "keyword", "index": false },
                  "status": { "type": "keyword" }
                }
              }
            }
            """;

    private static final String CATEGORY_NAME_MAPPING_JSON = """
            {
              "properties": {
                "category_name": { "type": "keyword", "ignore_above": 128 }
              }
            }
            """;

    private final ElasticsearchClient elasticsearchClient;
    private final RestClient restClient;
    private final ProductSpuMapper productSpuMapper;
    private final AdminProductSpuAssembler assembler;

    @Value("${shopping.admin.product-spu-index.startup-rebuild-enabled:true}")
    private boolean startupRebuildEnabled;

    @Value("${shopping.admin.product-spu-index.page-size:1000}")
    private int pageSize;

    public AdminProductSpuIndexService(ElasticsearchClient elasticsearchClient,
                                       RestClient restClient,
                                       ProductSpuMapper productSpuMapper,
                                       AdminProductSpuAssembler assembler) {
        this.elasticsearchClient = elasticsearchClient;
        this.restClient = restClient;
        this.productSpuMapper = productSpuMapper;
        this.assembler = assembler;
    }

    public void initializeOnStartup() {
        long start = System.currentTimeMillis();
        ensureIndexAndMapping();
        if (!startupRebuildEnabled) {
            log.info("Admin product SPU Elasticsearch startup rebuild disabled.");
            return;
        }
        int safePageSize = safePageSize();
        long offset = 0L;
        long indexed = 0L;
        while (true) {
            List<Map<String, Object>> rows = productSpuMapper.listSpuIndexDocuments(safePageSize, offset);
            if (rows == null || rows.isEmpty()) {
                break;
            }
            indexed += bulkIndex(toResponses(rows));
            offset += rows.size();
        }
        log.info("Admin product SPU Elasticsearch index initialized: alias={}, indexed={}, elapsedMs={}",
                PRODUCT_SPU_INDEX_ALIAS, indexed, System.currentTimeMillis() - start);
    }

    public void syncProductsAfterCommit(Collection<Long> spuIds) {
        List<Long> ids = normalizeLongCollection(spuIds);
        if (ids.isEmpty()) {
            return;
        }
        runAfterCommit(() -> syncProducts(ids));
    }

    public void syncProductsByCategoryIdsAfterCommit(Collection<Long> categoryIds) {
        List<Long> ids = normalizeLongCollection(categoryIds);
        if (ids.isEmpty()) {
            return;
        }
        runAfterCommit(() -> syncProductsByCategoryIds(ids));
    }

    public void deleteProductsAfterCommit(Collection<Long> spuIds) {
        List<Long> ids = normalizeLongCollection(spuIds);
        if (ids.isEmpty()) {
            return;
        }
        runAfterCommit(() -> bulkDelete(ids));
    }

    private void syncProducts(List<Long> ids) {
        int safePageSize = safePageSize();
        for (int start = 0; start < ids.size(); start += safePageSize) {
            List<Long> batch = ids.subList(start, Math.min(start + safePageSize, ids.size()));
            List<Map<String, Object>> rows = productSpuMapper.listSpuIndexDocumentsByIds(batch);
            List<AdminProductSpuResponse> products = toResponses(rows);
            Set<Long> foundIds = new LinkedHashSet<>();
            for (AdminProductSpuResponse product : products) {
                if (product.id() != null && product.id() > 0) {
                    foundIds.add(product.id());
                }
            }
            List<Long> missingIds = batch.stream()
                    .filter(id -> !foundIds.contains(id))
                    .toList();
            bulkWrite(products, missingIds);
        }
    }

    private void syncProductsByCategoryIds(List<Long> categoryIds) {
        int safePageSize = safePageSize();
        long offset = 0L;
        while (true) {
            List<Map<String, Object>> rows = productSpuMapper.listSpuIndexDocumentsByCategoryIds(
                    categoryIds,
                    safePageSize,
                    offset);
            if (rows == null || rows.isEmpty()) {
                break;
            }
            bulkIndex(toResponses(rows));
            offset += rows.size();
        }
    }

    private long bulkIndex(List<AdminProductSpuResponse> products) {
        return bulkWrite(products, List.of());
    }

    private long bulkDelete(List<Long> ids) {
        return bulkWrite(List.of(), ids);
    }

    private long bulkWrite(List<AdminProductSpuResponse> products, List<Long> deleteIds) {
        if ((products == null || products.isEmpty()) && (deleteIds == null || deleteIds.isEmpty())) {
            return 0L;
        }
        BulkRequest.Builder builder = new BulkRequest.Builder();
        if (products != null) {
            for (AdminProductSpuResponse product : products) {
                if (product == null || product.id() == null || product.id() <= 0) {
                    continue;
                }
                Map<String, Object> source = toSource(product);
                builder.operations(operation -> operation.index(index -> index
                        .index(PRODUCT_SPU_INDEX_ALIAS)
                        .id(String.valueOf(product.id()))
                        .document(source)));
            }
        }
        if (deleteIds != null) {
            for (Long id : deleteIds) {
                if (id == null || id <= 0) {
                    continue;
                }
                builder.operations(operation -> operation.delete(delete -> delete
                        .index(PRODUCT_SPU_INDEX_ALIAS)
                        .id(String.valueOf(id))));
            }
        }
        try {
            BulkResponse response = elasticsearchClient.bulk(builder.build());
            if (response.errors()) {
                log.warn("Admin product SPU Elasticsearch bulk write completed with errors: {}",
                        firstBulkError(response));
            }
            return response.items().size();
        } catch (IOException e) {
            log.warn("Admin product SPU Elasticsearch bulk write failed.", e);
            return 0L;
        }
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

    private List<AdminProductSpuResponse> toResponses(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<AdminProductSpuResponse> products = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            AdminProductSpuResponse product = assembler.toSpuResponse(row);
            if (product.id() != null && product.id() > 0) {
                products.add(product);
            }
        }
        return products;
    }

    private Map<String, Object> toSource(AdminProductSpuResponse product) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("id", product.id());
        source.put("category_id", product.categoryId());
        source.put("category_name", blankToNull(product.categoryName()));
        source.put("name", blankToNull(product.name()));
        source.put("subtitle", blankToNull(product.subtitle()));
        source.put("brand_name", blankToNull(product.brandName()));
        source.put("main_image_url", blankToNull(product.mainImageUrl()));
        source.put("status", blankToNull(product.status()));
        return source;
    }

    private void ensureIndexAndMapping() {
        try {
            ensureConcreteIndex();
            ensureAlias();
            if (!mappingContainsCategoryName()) {
                Request mapping = new Request("PUT", "/" + PRODUCT_SPU_INDEX_NAME + "/_mapping");
                mapping.setJsonEntity(CATEGORY_NAME_MAPPING_JSON);
                restClient.performRequest(mapping);
                log.info("Admin product SPU Elasticsearch mapping updated: index={}", PRODUCT_SPU_INDEX_NAME);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Admin product SPU Elasticsearch index initialization failed.", e);
        }
    }

    private void ensureConcreteIndex() throws IOException {
        if (endpointExists("/" + PRODUCT_SPU_INDEX_NAME)) {
            return;
        }
        Request create = new Request("PUT", "/" + PRODUCT_SPU_INDEX_NAME);
        create.setJsonEntity(CREATE_INDEX_JSON);
        restClient.performRequest(create);
        log.info("Admin product SPU Elasticsearch concrete index created: index={}", PRODUCT_SPU_INDEX_NAME);
    }

    private void ensureAlias() throws IOException {
        if (endpointExists("/" + PRODUCT_SPU_INDEX_NAME + "/_alias/" + PRODUCT_SPU_INDEX_ALIAS)) {
            return;
        }
        if (endpointExists("/_alias/" + PRODUCT_SPU_INDEX_ALIAS)) {
            throw new IllegalStateException(
                    "Admin product SPU Elasticsearch alias already points to another index: alias="
                            + PRODUCT_SPU_INDEX_ALIAS + ", expectedIndex=" + PRODUCT_SPU_INDEX_NAME);
        }
        if (endpointExists("/" + PRODUCT_SPU_INDEX_ALIAS)) {
            throw new IllegalStateException(
                    "Admin product SPU Elasticsearch alias name is occupied by a concrete index: alias="
                            + PRODUCT_SPU_INDEX_ALIAS);
        }
        Request alias = new Request("POST", "/_aliases");
        alias.setJsonEntity("""
                {
                  "actions": [
                    { "add": { "index": "%s", "alias": "%s" } }
                  ]
                }
                """.formatted(PRODUCT_SPU_INDEX_NAME, PRODUCT_SPU_INDEX_ALIAS));
        restClient.performRequest(alias);
        log.info("Admin product SPU Elasticsearch alias added: alias={}, index={}",
                PRODUCT_SPU_INDEX_ALIAS, PRODUCT_SPU_INDEX_NAME);
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

    private boolean mappingContainsCategoryName() throws IOException {
        Request request = new Request("GET", "/" + PRODUCT_SPU_INDEX_NAME + "/_mapping");
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
        return body != null && body.contains("\"category_name\"");
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

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
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
