package com.example.ShoppingSystem.coupon.service.impl.AdminCouponTemplateIndexService;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import com.example.ShoppingSystem.Utils.HybridIdCodec;
import com.example.ShoppingSystem.mapper.coupon.CouponTemplateMapper;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.example.ShoppingSystem.coupon.service.AdminCouponTemplateIndexService;
@Slf4j
@Service
public class AdminCouponTemplateIndexServiceImpl implements AdminCouponTemplateIndexService {

    static final String COUPON_TEMPLATE_INDEX_ALIAS = "shopping_coupon_template";
    private static final String COUPON_TEMPLATE_INDEX_NAME = "shopping_coupon_template_v1";
    private static final int DEFAULT_PAGE_SIZE = 1000;
    private static final int MIN_PAGE_SIZE = 100;
    private static final int MAX_PAGE_SIZE = 5000;

    private static final String CREATE_INDEX_JSON = """
            {
              "mappings": {
                "dynamic": true,
                "properties": {
                  "id": { "type": "keyword" },
                  "couponCode": { "type": "keyword", "ignore_above": 128 },
                  "name": {
                    "type": "text",
                    "analyzer": "ik_max_word",
                    "search_analyzer": "ik_smart",
                    "fields": {
                      "keyword": { "type": "keyword", "ignore_above": 256 }
                    }
                  },
                  "status": { "type": "keyword" },
                  "receiveStartAt": { "type": "date" },
                  "receiveEndAt": { "type": "date" },
                  "validStartAt": { "type": "date" },
                  "validEndAt": { "type": "date" },
                  "updatedAt": { "type": "date" },
                  "createdAt": { "type": "date" }
                }
              }
            }
            """;

    private final ElasticsearchClient elasticsearchClient;
    private final RestClient restClient;
    private final CouponTemplateMapper couponTemplateMapper;

    @Value("${shopping.admin.coupon-template-index.startup-rebuild-enabled:true}")
    private boolean startupRebuildEnabled;

    @Value("${shopping.admin.coupon-template-index.page-size:1000}")
    private int pageSize;

    public AdminCouponTemplateIndexServiceImpl(ElasticsearchClient elasticsearchClient,
                                           RestClient restClient,
                                           CouponTemplateMapper couponTemplateMapper) {
        this.elasticsearchClient = elasticsearchClient;
        this.restClient = restClient;
        this.couponTemplateMapper = couponTemplateMapper;
    }

    public void initializeOnStartup() {
        long start = System.currentTimeMillis();
        ensureIndexAndAlias();
        if (!startupRebuildEnabled) {
            log.info("Admin coupon template Elasticsearch startup rebuild disabled.");
            return;
        }
        int safePageSize = safePageSize();
        long offset = 0L;
        long indexed = 0L;
        while (true) {
            List<Map<String, Object>> rows = couponTemplateMapper.listCouponTemplateIndexDocuments(safePageSize, offset);
            if (rows == null || rows.isEmpty()) {
                break;
            }
            indexed += bulkIndex(rows);
            offset += rows.size();
        }
        log.info("Admin coupon template Elasticsearch index initialized: alias={}, indexed={}, elapsedMs={}",
                COUPON_TEMPLATE_INDEX_ALIAS, indexed, System.currentTimeMillis() - start);
    }

    public void syncCouponTemplatesAfterCommit(Collection<String> couponTemplateIds) {
        List<String> ids = normalizeCouponIds(couponTemplateIds);
        if (ids.isEmpty()) {
            return;
        }
        runAfterCommit(() -> syncCouponTemplates(ids));
    }

    public void deleteCouponTemplatesAfterCommit(Collection<String> couponTemplateIds) {
        List<String> ids = normalizeCouponIds(couponTemplateIds);
        if (ids.isEmpty()) {
            return;
        }
        runAfterCommit(() -> bulkDelete(ids));
    }

    public void syncCouponTemplates(Collection<String> couponTemplateIds) {
        List<String> ids = normalizeCouponIds(couponTemplateIds);
        if (ids.isEmpty()) {
            return;
        }
        List<byte[]> idBytes = ids.stream()
                .map(HybridIdCodec::fromBase62)
                .toList();
        List<Map<String, Object>> rows = couponTemplateMapper.findCouponTemplateIndexDocumentsByIds(idBytes);
        Set<String> foundIds = new LinkedHashSet<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                String id = rowId(row);
                if (!id.isBlank()) {
                    foundIds.add(id);
                }
            }
        }
        List<String> missingIds = ids.stream()
                .filter(id -> !foundIds.contains(id))
                .toList();
        bulkWrite(rows == null ? List.of() : rows, missingIds);
    }

    public void deleteCouponTemplates(Collection<String> couponTemplateIds) {
        List<String> ids = normalizeCouponIds(couponTemplateIds);
        if (!ids.isEmpty()) {
            bulkDelete(ids);
        }
    }

    private long bulkIndex(List<Map<String, Object>> rows) {
        return bulkWrite(rows, List.of());
    }

    private long bulkDelete(List<String> ids) {
        return bulkWrite(List.of(), ids);
    }

    private long bulkWrite(List<Map<String, Object>> rows, List<String> deleteIds) {
        if ((rows == null || rows.isEmpty()) && (deleteIds == null || deleteIds.isEmpty())) {
            return 0L;
        }
        BulkRequest.Builder builder = new BulkRequest.Builder();
        int operationCount = 0;
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                String id = rowId(row);
                if (id.isBlank()) {
                    continue;
                }
                builder.operations(operation -> operation.index(index -> index
                        .index(COUPON_TEMPLATE_INDEX_ALIAS)
                        .id(id)
                        .document(toSource(row))));
                operationCount += 1;
            }
        }
        if (deleteIds != null) {
            for (String id : deleteIds) {
                if (id == null || id.isBlank()) {
                    continue;
                }
                builder.operations(operation -> operation.delete(delete -> delete
                        .index(COUPON_TEMPLATE_INDEX_ALIAS)
                        .id(id)));
                operationCount += 1;
            }
        }
        if (operationCount == 0) {
            return 0L;
        }
        try {
            BulkResponse response = elasticsearchClient.bulk(builder.build());
            if (response.errors()) {
                log.warn("Admin coupon template Elasticsearch bulk write completed with errors: {}",
                        firstBulkError(response));
            }
            return response.items().size();
        } catch (IOException e) {
            log.warn("Admin coupon template Elasticsearch bulk write failed.", e);
            return 0L;
        }
    }

    private Map<String, Object> toSource(Map<String, Object> row) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("id", rowId(row));
        source.put("couponCode", blankToNull(text(row.get("couponCode"))));
        source.put("name", blankToNull(text(row.get("name"))));
        source.put("status", blankToNull(text(row.get("status"))));
        source.put("receiveStartAt", dateText(row.get("receiveStartAt")));
        source.put("receiveEndAt", dateText(row.get("receiveEndAt")));
        source.put("validStartAt", dateText(row.get("validStartAt")));
        source.put("validEndAt", dateText(row.get("validEndAt")));
        source.put("createdAt", dateText(row.get("createdAt")));
        source.put("updatedAt", dateText(row.get("updatedAt")));
        return source;
    }

    private String rowId(Map<String, Object> row) {
        return row == null ? "" : HybridIdCodec.toBase62FromDatabaseValue(row.get("id"));
    }

    private String dateText(Object value) {
        if (value instanceof OffsetDateTime time) {
            return time.toInstant().toString();
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant().toString();
        }
        if (value instanceof java.util.Date date) {
            return date.toInstant().toString();
        }
        String text = text(value);
        if (text.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(text).toInstant().toString();
        } catch (RuntimeException ignored) {
            return java.time.Instant.parse(text).atZone(ZoneId.systemDefault()).toInstant().toString();
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

    private void ensureIndexAndAlias() {
        try {
            ensureConcreteIndex();
            ensureAlias();
        } catch (IOException e) {
            throw new IllegalStateException("Admin coupon template Elasticsearch index initialization failed.", e);
        }
    }

    private void ensureConcreteIndex() throws IOException {
        if (endpointExists("/" + COUPON_TEMPLATE_INDEX_NAME)) {
            return;
        }
        Request create = new Request("PUT", "/" + COUPON_TEMPLATE_INDEX_NAME);
        create.setJsonEntity(CREATE_INDEX_JSON);
        restClient.performRequest(create);
        log.info("Admin coupon template Elasticsearch concrete index created: index={}", COUPON_TEMPLATE_INDEX_NAME);
    }

    private void ensureAlias() throws IOException {
        if (endpointExists("/" + COUPON_TEMPLATE_INDEX_NAME + "/_alias/" + COUPON_TEMPLATE_INDEX_ALIAS)) {
            return;
        }
        if (endpointExists("/_alias/" + COUPON_TEMPLATE_INDEX_ALIAS)) {
            throw new IllegalStateException(
                    "Admin coupon template Elasticsearch alias already points to another index: alias="
                            + COUPON_TEMPLATE_INDEX_ALIAS + ", expectedIndex=" + COUPON_TEMPLATE_INDEX_NAME);
        }
        if (endpointExists("/" + COUPON_TEMPLATE_INDEX_ALIAS)) {
            throw new IllegalStateException(
                    "Admin coupon template Elasticsearch alias name is occupied by a concrete index: alias="
                            + COUPON_TEMPLATE_INDEX_ALIAS);
        }
        Request alias = new Request("POST", "/_aliases");
        alias.setJsonEntity("""
                {
                  "actions": [
                    { "add": { "index": "%s", "alias": "%s" } }
                  ]
                }
                """.formatted(COUPON_TEMPLATE_INDEX_NAME, COUPON_TEMPLATE_INDEX_ALIAS));
        restClient.performRequest(alias);
        log.info("Admin coupon template Elasticsearch alias added: alias={}, index={}",
                COUPON_TEMPLATE_INDEX_ALIAS, COUPON_TEMPLATE_INDEX_NAME);
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

    private int safePageSize() {
        return Math.max(MIN_PAGE_SIZE, Math.min(MAX_PAGE_SIZE, pageSize <= 0 ? DEFAULT_PAGE_SIZE : pageSize));
    }

    private List<String> normalizeCouponIds(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .map(this::normalizeCouponId)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private String normalizeCouponId(String value) {
        String text = text(value);
        if (!text.matches(HybridIdCodec.BASE62_PATTERN)) {
            return "";
        }
        return text;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
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
