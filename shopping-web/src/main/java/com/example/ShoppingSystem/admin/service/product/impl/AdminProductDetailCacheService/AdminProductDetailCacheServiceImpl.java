package com.example.ShoppingSystem.admin.service.product.impl.AdminProductDetailCacheService;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import com.example.ShoppingSystem.admin.service.common.AdminServiceException;

import com.example.ShoppingSystem.admin.service.product.AdminProductDetailCacheService;
@Slf4j
@Service
public class AdminProductDetailCacheServiceImpl implements AdminProductDetailCacheService {

    private static final String DETAIL_CACHE_KEY_PREFIX = "shopping:admin:product:spu:detail:";
    private static final String DETAIL_CACHE_NULL_MARKER = "__NULL__";
    private static final int DETAIL_LOCAL_CACHE_MAX_SIZE = 10_000;
    private static final Duration DETAIL_LOCAL_CACHE_MAX_TTL = Duration.ofMinutes(20);
    private static final Duration DETAIL_REDIS_POSITIVE_TTL = Duration.ofMinutes(10);
    private static final Duration DETAIL_REDIS_POSITIVE_JITTER = Duration.ofMinutes(5);
    private static final Duration DETAIL_REDIS_NEGATIVE_TTL = Duration.ofSeconds(60);
    private static final Duration DETAIL_REDIS_NEGATIVE_JITTER = Duration.ofSeconds(30);

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final CountingBloomFilter countingBloomFilter;
    private final Cache<Long, ProductDetailCacheEntry> detailLocalCache;

    @Value("${shopping.admin.product-spu-bloom.enabled:true}")
    private boolean productSpuBloomEnabled;

    @Value("${shopping.admin.product-spu-bloom.key:shopping:admin:product:spu:id:cbf}")
    private String productSpuBloomKey;

    public AdminProductDetailCacheServiceImpl(StringRedisTemplate stringRedisTemplate,
                                          ObjectMapper objectMapper,
                                          CountingBloomFilter countingBloomFilter) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.countingBloomFilter = countingBloomFilter;
        this.detailLocalCache = Caffeine.newBuilder()
                .maximumSize(DETAIL_LOCAL_CACHE_MAX_SIZE)
                .expireAfterWrite(DETAIL_LOCAL_CACHE_MAX_TTL)
                .build();
    }

    public AdminProductSpuDetailResponse getDetail(Long spuId, Supplier<AdminProductSpuDetailResponse> loader) {
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
        AdminProductSpuDetailResponse detail = loader.get();
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

    public void syncCreatedProductAfterCommit(Long spuId) {
        if (spuId == null || spuId <= 0) {
            return;
        }
        runAfterCommit(() -> {
            invalidate(List.of(spuId));
            if (!productSpuBloomEnabled) {
                return;
            }
            try {
                countingBloomFilter.add(productSpuBloomKey, spuId);
            } catch (Exception e) {
                log.warn("[商品管理] 新商品 SPU ID 写入 Bloom 失败，spuId={}", spuId, e);
            }
        });
    }

    public void deleteProductBloomIdsAfterCommit(Collection<Long> spuIds) {
        List<Long> ids = normalizeLongCollection(spuIds);
        if (ids.isEmpty() || !productSpuBloomEnabled) {
            return;
        }
        runAfterCommit(() -> {
            try {
                countingBloomFilter.deleteAllLongs(productSpuBloomKey, ids);
            } catch (Exception e) {
                log.warn("[商品管理] 批量删除商品 SPU ID Bloom 失败，count={}", ids.size(), e);
            }
        });
    }

    private boolean mightProductExist(Long spuId) {
        if (!productSpuBloomEnabled) {
            return true;
        }
        try {
            return Boolean.TRUE.equals(countingBloomFilter.exists(productSpuBloomKey, spuId));
        } catch (Exception e) {
            log.warn("[商品管理] SPU ID Bloom 查询失败，降级继续走缓存/DB，spuId={}", spuId, e);
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
            log.warn("[商品管理] 读取商品详情 Redis 缓存失败，spuId={}", spuId, e);
            return null;
        }
        if (raw == null || raw.isBlank()) {
            return null;
        }
        if (DETAIL_CACHE_NULL_MARKER.equals(raw)) {
            return ProductDetailCacheEntry.notFound(System.currentTimeMillis() + negativeTtl().toMillis());
        }
        try {
            AdminProductSpuDetailResponse detail = objectMapper.readValue(raw, AdminProductSpuDetailResponse.class);
            return ProductDetailCacheEntry.found(detail, System.currentTimeMillis() + positiveTtl().toMillis());
        } catch (JsonProcessingException e) {
            log.warn("[商品管理] 商品详情 Redis 缓存 JSON 无效，spuId={}", spuId, e);
            invalidate(List.of(spuId));
            return null;
        }
    }

    private void writeLocalDetailCache(Long spuId, ProductDetailCacheEntry entry) {
        if (spuId != null && entry != null) {
            detailLocalCache.put(spuId, entry);
        }
    }

    private void writeRedisDetailCache(Long spuId, AdminProductSpuDetailResponse detail) {
        try {
            stringRedisTemplate.opsForValue().set(detailCacheKey(spuId), objectMapper.writeValueAsString(detail), positiveTtl());
        } catch (Exception e) {
            log.warn("[商品管理] 写入商品详情 Redis 缓存失败，spuId={}", spuId, e);
        }
    }

    private void writeRedisNegativeDetailCache(Long spuId) {
        try {
            stringRedisTemplate.opsForValue().set(detailCacheKey(spuId), DETAIL_CACHE_NULL_MARKER, negativeTtl());
        } catch (Exception e) {
            log.warn("[商品管理] 写入商品详情空值缓存失败，spuId={}", spuId, e);
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
            log.warn("[商品管理] 批量删除商品详情 Redis 缓存失败，count={}", redisKeys.size(), e);
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

    private AdminServiceException productNotFoundException() {
        return new AdminServiceException(
                "ADMIN_PRODUCT_SPU_NOT_FOUND",
                "商品不存在。",
                HttpStatus.NOT_FOUND);
    }

    private record ProductDetailCacheEntry(boolean found,
                                           AdminProductSpuDetailResponse detail,
                                           long expiresAtEpochMillis) {
        static ProductDetailCacheEntry found(AdminProductSpuDetailResponse detail, long expiresAtEpochMillis) {
            return new ProductDetailCacheEntry(true, detail, expiresAtEpochMillis);
        }

        static ProductDetailCacheEntry notFound(long expiresAtEpochMillis) {
            return new ProductDetailCacheEntry(false, null, expiresAtEpochMillis);
        }
    }
}
