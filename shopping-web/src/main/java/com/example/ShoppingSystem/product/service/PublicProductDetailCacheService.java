package com.example.ShoppingSystem.product.service;

import com.example.ShoppingSystem.mapper.product.ProductSpuMapper;
import com.example.ShoppingSystem.product.dto.PublicProductDetailResponse;
import com.example.ShoppingSystem.redisfilter.CountingBloomFilter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

@Slf4j
@Service
public class PublicProductDetailCacheService {

    private static final String DETAIL_CACHE_KEY_PREFIX = "shopping:product:public:detail:";
    private static final String DETAIL_CACHE_NULL_MARKER = "__NULL__";
    private static final int DETAIL_LOCAL_CACHE_MAX_SIZE = 10_000;
    private static final int CATEGORY_INVALIDATE_PAGE_SIZE = 1000;
    private static final Duration DETAIL_LOCAL_CACHE_MAX_TTL = Duration.ofMinutes(20);
    private static final Duration DETAIL_REDIS_POSITIVE_TTL = Duration.ofMinutes(10);
    private static final Duration DETAIL_REDIS_POSITIVE_JITTER = Duration.ofMinutes(5);
    private static final Duration DETAIL_REDIS_NEGATIVE_TTL = Duration.ofSeconds(60);
    private static final Duration DETAIL_REDIS_NEGATIVE_JITTER = Duration.ofSeconds(30);

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final ProductSpuMapper productSpuMapper;
    private final CountingBloomFilter countingBloomFilter;
    private final Cache<Long, ProductDetailCacheEntry> detailLocalCache;

    @Value("${shopping.admin.product-spu-bloom.enabled:true}")
    private boolean productSpuBloomEnabled;

    @Value("${shopping.admin.product-spu-bloom.key:shopping:admin:product:spu:id:cbf}")
    private String productSpuBloomKey;

    public PublicProductDetailCacheService(StringRedisTemplate stringRedisTemplate,
                                           ObjectMapper objectMapper,
                                           ProductSpuMapper productSpuMapper,
                                           CountingBloomFilter countingBloomFilter) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.productSpuMapper = productSpuMapper;
        this.countingBloomFilter = countingBloomFilter;
        this.detailLocalCache = Caffeine.newBuilder()
                .maximumSize(DETAIL_LOCAL_CACHE_MAX_SIZE)
                .expireAfterWrite(DETAIL_LOCAL_CACHE_MAX_TTL)
                .build();
    }

    public PublicProductDetailResponse getDetail(Long spuId, Supplier<PublicProductDetailResponse> loader) {
        if (!mightProductExist(spuId)) {
            throw productNotFoundException();
        }
        ProductDetailCacheEntry localEntry = readLocalDetailCache(spuId);
        if (localEntry != null) {
            if (!localEntry.found()) {
                throw productNotFoundException();
            }
            return localEntry.detail();
        }
        ProductDetailCacheEntry redisEntry = readRedisDetailCache(spuId);
        if (redisEntry != null) {
            writeLocalDetailCache(spuId, redisEntry);
            if (!redisEntry.found()) {
                throw productNotFoundException();
            }
            return redisEntry.detail();
        }
        PublicProductDetailResponse detail = loader.get();
        if (detail == null) {
            ProductDetailCacheEntry negative = ProductDetailCacheEntry.notFound(
                    System.currentTimeMillis() + negativeTtl().toMillis());
            writeLocalDetailCache(spuId, negative);
            writeRedisNegativeDetailCache(spuId);
            throw productNotFoundException();
        }
        ProductDetailCacheEntry positive = ProductDetailCacheEntry.found(
                detail,
                System.currentTimeMillis() + positiveTtl().toMillis());
        writeLocalDetailCache(spuId, positive);
        writeRedisDetailCache(spuId, detail);
        return detail;
    }

    public void invalidateAfterCommit(Collection<Long> spuIds) {
        List<Long> ids = normalizeLongCollection(spuIds);
        if (ids.isEmpty()) {
            return;
        }
        runAfterCommit(() -> invalidate(ids));
    }

    public void invalidateByCategoryIdsAfterCommit(Collection<Long> categoryIds) {
        List<Long> ids = normalizeLongCollection(categoryIds);
        if (ids.isEmpty()) {
            return;
        }
        runAfterCommit(() -> invalidateByCategoryIds(ids));
    }

    private boolean mightProductExist(Long spuId) {
        if (spuId == null || spuId <= 0) {
            return false;
        }
        if (!productSpuBloomEnabled) {
            return true;
        }
        try {
            return Boolean.TRUE.equals(countingBloomFilter.exists(productSpuBloomKey, spuId));
        } catch (Exception e) {
            log.warn("[Product public detail] SPU ID Bloom query failed, falling back to cache/DB, spuId={}", spuId, e);
            return true;
        }
    }

    private ProductDetailCacheEntry readLocalDetailCache(Long spuId) {
        ProductDetailCacheEntry entry = detailLocalCache.getIfPresent(spuId);
        if (entry == null) {
            return null;
        }
        if (entry.expiresAtEpochMillis() <= System.currentTimeMillis()) {
            detailLocalCache.invalidate(spuId);
            return null;
        }
        return entry;
    }

    private ProductDetailCacheEntry readRedisDetailCache(Long spuId) {
        String raw;
        try {
            raw = stringRedisTemplate.opsForValue().get(detailCacheKey(spuId));
        } catch (Exception e) {
            log.warn("[Product public detail] Redis read failed, spuId={}", spuId, e);
            return null;
        }
        if (raw == null || raw.isBlank()) {
            return null;
        }
        if (DETAIL_CACHE_NULL_MARKER.equals(raw)) {
            return ProductDetailCacheEntry.notFound(System.currentTimeMillis() + negativeTtl().toMillis());
        }
        try {
            PublicProductDetailResponse detail = objectMapper.readValue(raw, PublicProductDetailResponse.class);
            return ProductDetailCacheEntry.found(detail, System.currentTimeMillis() + positiveTtl().toMillis());
        } catch (JsonProcessingException e) {
            log.warn("[Product public detail] Redis cache JSON invalid, spuId={}", spuId, e);
            invalidate(List.of(spuId));
            return null;
        }
    }

    private void writeLocalDetailCache(Long spuId, ProductDetailCacheEntry entry) {
        if (spuId != null && entry != null) {
            detailLocalCache.put(spuId, entry);
        }
    }

    private void writeRedisDetailCache(Long spuId, PublicProductDetailResponse detail) {
        try {
            stringRedisTemplate.opsForValue().set(detailCacheKey(spuId), objectMapper.writeValueAsString(detail), positiveTtl());
        } catch (Exception e) {
            log.warn("[Product public detail] Redis write failed, spuId={}", spuId, e);
        }
    }

    private void writeRedisNegativeDetailCache(Long spuId) {
        try {
            stringRedisTemplate.opsForValue().set(detailCacheKey(spuId), DETAIL_CACHE_NULL_MARKER, negativeTtl());
        } catch (Exception e) {
            log.warn("[Product public detail] Redis negative write failed, spuId={}", spuId, e);
        }
    }

    private void invalidateByCategoryIds(List<Long> categoryIds) {
        long offset = 0L;
        while (true) {
            List<Long> spuIds = productSpuMapper.listSpuIdsByCategoryIds(
                    categoryIds,
                    CATEGORY_INVALIDATE_PAGE_SIZE,
                    offset);
            if (spuIds == null || spuIds.isEmpty()) {
                break;
            }
            invalidate(spuIds);
            offset += spuIds.size();
        }
    }

    private void invalidate(Collection<Long> spuIds) {
        List<Long> ids = normalizeLongCollection(spuIds);
        if (ids.isEmpty()) {
            return;
        }
        detailLocalCache.invalidateAll(ids);
        List<String> redisKeys = ids.stream()
                .map(this::detailCacheKey)
                .toList();
        try {
            stringRedisTemplate.delete(redisKeys);
        } catch (Exception e) {
            log.warn("[Product public detail] Redis batch delete failed, count={}", redisKeys.size(), e);
        }
    }

    private String detailCacheKey(Long spuId) {
        return DETAIL_CACHE_KEY_PREFIX + spuId;
    }

    private Duration positiveTtl() {
        long jitterSeconds = DETAIL_REDIS_POSITIVE_JITTER.isZero()
                ? 0
                : ThreadLocalRandom.current().nextLong(DETAIL_REDIS_POSITIVE_JITTER.toSeconds() + 1);
        return DETAIL_REDIS_POSITIVE_TTL.plusSeconds(jitterSeconds);
    }

    private Duration negativeTtl() {
        long jitterSeconds = DETAIL_REDIS_NEGATIVE_JITTER.isZero()
                ? 0
                : ThreadLocalRandom.current().nextLong(DETAIL_REDIS_NEGATIVE_JITTER.toSeconds() + 1);
        return DETAIL_REDIS_NEGATIVE_TTL.plusSeconds(jitterSeconds);
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

    private ResponseStatusException productNotFoundException() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Product is unavailable or does not exist.");
    }

    private record ProductDetailCacheEntry(boolean found,
                                           PublicProductDetailResponse detail,
                                           long expiresAtEpochMillis) {
        static ProductDetailCacheEntry found(PublicProductDetailResponse detail, long expiresAtEpochMillis) {
            return new ProductDetailCacheEntry(true, detail, expiresAtEpochMillis);
        }

        static ProductDetailCacheEntry notFound(long expiresAtEpochMillis) {
            return new ProductDetailCacheEntry(false, null, expiresAtEpochMillis);
        }
    }
}
