package com.example.ShoppingSystem.product.service;

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
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public interface PublicProductSearchService {
    public PublicProductSearchResponse search(String keyword, Long categoryId, int page, int pageSize);
}
