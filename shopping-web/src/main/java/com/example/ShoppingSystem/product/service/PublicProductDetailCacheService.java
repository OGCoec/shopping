package com.example.ShoppingSystem.product.service;

import com.example.ShoppingSystem.Utils.ProductSkuIdCodec;
import com.example.ShoppingSystem.mapper.product.ProductSpuMapper;
import com.example.ShoppingSystem.product.dto.PublicProductDetailResponse;
import com.example.ShoppingSystem.product.dto.PublicProductSkuResponse;
import com.example.ShoppingSystem.redisfilter.CountingBloomFilter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

public interface PublicProductDetailCacheService {
    public PublicProductDetailResponse getDetail(Long spuId, Supplier<PublicProductDetailResponse> loader);

    public void invalidateAfterCommit(Collection<Long> spuIds);

    public void invalidateByCategoryIdsAfterCommit(Collection<Long> categoryIds);
}
