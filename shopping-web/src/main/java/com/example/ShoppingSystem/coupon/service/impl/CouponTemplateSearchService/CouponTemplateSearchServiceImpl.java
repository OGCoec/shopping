package com.example.ShoppingSystem.coupon.service.impl.CouponTemplateSearchService;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.ShoppingSystem.coupon.service.CouponTemplateSearchService;
import com.example.ShoppingSystem.coupon.service.AdminCouponTemplateIndexService;
import com.example.ShoppingSystem.coupon.service.CouponTemplateSearchException;
@Slf4j
@Service
public class CouponTemplateSearchServiceImpl implements CouponTemplateSearchService {

    private static final String HIGHLIGHT_START = "[[HL]]";
    private static final String HIGHLIGHT_END = "[[/HL]]";

    private final ElasticsearchClient elasticsearchClient;

    public CouponTemplateSearchServiceImpl(ElasticsearchClient elasticsearchClient) {
        this.elasticsearchClient = elasticsearchClient;
    }

    public SearchResult searchAdminTemplateIds(String name,
                                               String status,
                                               OffsetDateTime receiveStartAtFrom,
                                               OffsetDateTime receiveEndAtTo,
                                               int page,
                                               int pageSize) {
        BoolQuery.Builder bool = baseNameQuery(name);
        bool.mustNot(mustNot -> mustNot.term(term -> term.field("status").value("DELETED")));
        if (status != null && !status.isBlank()) {
            bool.filter(filter -> filter.term(term -> term.field("status").value(status)));
        }
        if (receiveStartAtFrom != null) {
            bool.filter(filter -> filter.range(range -> range
                    .date(date -> date
                            .field("receiveStartAt")
                            .gte(receiveStartAtFrom.toInstant().toString()))));
        }
        if (receiveEndAtTo != null) {
            bool.filter(filter -> filter.range(range -> range
                    .date(date -> date
                            .field("receiveEndAt")
                            .lte(receiveEndAtTo.toInstant().toString()))));
        }
        return search("admin", name, bool, page, pageSize);
    }

    public SearchResult searchActiveReceivableTemplateIds(String name,
                                                          OffsetDateTime now,
                                                          int page,
                                                          int pageSize) {
        BoolQuery.Builder bool = baseNameQuery(name);
        String nowText = now.toInstant().toString();
        bool.filter(filter -> filter.term(term -> term.field("status").value("ACTIVE")));
        bool.filter(filter -> filter.range(range -> range
                .date(date -> date
                        .field("receiveStartAt")
                        .lte(nowText))));
        bool.filter(filter -> filter.range(range -> range
                .date(date -> date
                        .field("receiveEndAt")
                        .gte(nowText))));
        return search("user", name, bool, page, pageSize);
    }

    private BoolQuery.Builder baseNameQuery(String name) {
        return new BoolQuery.Builder()
                .must(must -> must.match(match -> match.field("name").query(name)));
    }

    private SearchResult search(String scene,
                                String name,
                                BoolQuery.Builder bool,
                                int page,
                                int pageSize) {
        int from = Math.max(0, (page - 1) * pageSize);
        try {
            SearchRequest request = new SearchRequest.Builder()
                    .index(AdminCouponTemplateIndexService.COUPON_TEMPLATE_INDEX_ALIAS)
                    .from(from)
                    .size(pageSize)
                    .trackTotalHits(track -> track.enabled(true))
                    .query(new Query.Builder().bool(bool.build()).build())
                    .highlight(highlight -> highlight
                            .preTags(HIGHLIGHT_START)
                            .postTags(HIGHLIGHT_END)
                            .fields("name", field -> field.numberOfFragments(0)))
                    .sort(sort -> sort.score(score -> score.order(SortOrder.Desc)))
                    .sort(sort -> sort.field(field -> field.field("updatedAt").order(SortOrder.Desc)))
                    .sort(sort -> sort.field(field -> field.field("id").order(SortOrder.Asc)))
                    .build();
            SearchResponse<Object> response = elasticsearchClient.search(request, Object.class);
            long total = response.hits().total() == null
                    ? response.hits().hits().size()
                    : response.hits().total().value();
            List<String> ids = response.hits().hits().stream()
                    .map(Hit::id)
                    .filter(id -> id != null && !id.isBlank())
                    .toList();
            Map<String, String> highlightedNames = new LinkedHashMap<>();
            for (Hit<Object> hit : response.hits().hits()) {
                String id = hit.id();
                if (id == null || id.isBlank()) {
                    continue;
                }
                List<String> fragments = hit.highlight().get("name");
                if (fragments != null && !fragments.isEmpty()) {
                    highlightedNames.put(id, fragments.get(0));
                }
            }
            return new SearchResult(total, ids, highlightedNames);
        } catch (IOException | ElasticsearchException e) {
            log.warn("Coupon template Elasticsearch query failed, scene={}, name={}", scene, name, e);
            throw new CouponTemplateSearchException("Coupon search service is temporarily unavailable.", e);
        }
    }
}
