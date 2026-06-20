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

public interface AdminProductSpuIndexService {

    public static final String PRODUCT_SPU_INDEX_ALIAS = "shopping_product_spu";
    public void initializeOnStartup();

    public void syncProductsAfterCommit(Collection<Long> spuIds);

    public void syncProductsByCategoryIdsAfterCommit(Collection<Long> categoryIds);

    public void deleteProductsAfterCommit(Collection<Long> spuIds);
}
