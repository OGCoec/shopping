package com.example.ShoppingSystem.coupon.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import lombok.extern.slf4j.Slf4j;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public interface CouponTemplateSearchService {
    public record SearchResult(long total, List<String> ids, Map<String, String> highlightedNames) {
        }

    public SearchResult searchAdminTemplateIds(String name,
                                               String status,
                                               OffsetDateTime receiveStartAtFrom,
                                               OffsetDateTime receiveEndAtTo,
                                               int page,
                                               int pageSize);

    public SearchResult searchActiveReceivableTemplateIds(String name,
                                                          OffsetDateTime now,
                                                          int page,
                                                          int pageSize);
}
