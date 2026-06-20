package com.example.ShoppingSystem.coupon.service;

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

public interface AdminCouponTemplateIndexService {

    public static final String COUPON_TEMPLATE_INDEX_ALIAS = "shopping_coupon_template";
    public void initializeOnStartup();

    public void syncCouponTemplatesAfterCommit(Collection<String> couponTemplateIds);

    public void deleteCouponTemplatesAfterCommit(Collection<String> couponTemplateIds);

    public void syncCouponTemplates(Collection<String> couponTemplateIds);

    public void deleteCouponTemplates(Collection<String> couponTemplateIds);
}
