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
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public interface AdminProductSpuSearchService {
    public AdminProductSpuPageResponse searchPage(String name,
                                                  Long categoryId,
                                                  String status,
                                                  int page,
                                                  int pageSize);
}
