package com.example.ShoppingSystem.admin.service.product;

import com.example.ShoppingSystem.admin.dto.AdminProductSpuDetailResponse;
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
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import com.example.ShoppingSystem.admin.service.common.AdminServiceException;

public interface AdminProductDetailCacheService {
    public AdminProductSpuDetailResponse getDetail(Long spuId, Supplier<AdminProductSpuDetailResponse> loader);

    public void invalidateAfterCommit(Collection<Long> spuIds);

    public void syncCreatedProductAfterCommit(Long spuId);

    public void deleteProductBloomIdsAfterCommit(Collection<Long> spuIds);
}
