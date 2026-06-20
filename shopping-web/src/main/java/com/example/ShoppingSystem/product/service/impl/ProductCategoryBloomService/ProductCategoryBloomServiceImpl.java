package com.example.ShoppingSystem.product.service.impl.ProductCategoryBloomService;

import com.example.ShoppingSystem.mapper.product.ProductCategoryMapper;
import com.example.ShoppingSystem.redisfilter.CountingBloomFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.List;

import com.example.ShoppingSystem.product.service.ProductCategoryBloomService;
@Slf4j
@Service
public class ProductCategoryBloomServiceImpl implements ProductCategoryBloomService {

    private static final int MIN_CAPACITY = 200;
    private static final int MIN_HASH_COUNT = 4;
    private static final int MAX_HASH_COUNT = 25;

    private final CountingBloomFilter countingBloomFilter;
    private final ProductCategoryMapper productCategoryMapper;

    @Value("${shopping.admin.product-category-bloom.enabled:true}")
    private boolean enabled;

    @Value("${shopping.admin.product-category-bloom.key:shopping:admin:product:category:id:cbf}")
    private String filterKey;

    @Value("${shopping.admin.product-category-bloom.capacity:2000000}")
    private int capacity;

    @Value("${shopping.admin.product-category-bloom.hash-count:7}")
    private int hashCount;

    @Value("${shopping.admin.product-category-bloom.counter-bytes:1}")
    private int counterBytes;

    @Value("${shopping.admin.product-category-bloom.page-size:2000}")
    private int pageSize;

    public ProductCategoryBloomServiceImpl(CountingBloomFilter countingBloomFilter,
                                       ProductCategoryMapper productCategoryMapper) {
        this.countingBloomFilter = countingBloomFilter;
        this.productCategoryMapper = productCategoryMapper;
    }

    public void rebuildOnStartup() {
        if (!enabled) {
            log.info("Product category ID counting bloom initialization disabled.");
            return;
        }
        long start = System.currentTimeMillis();
        int safeCapacity = Math.max(MIN_CAPACITY, capacity);
        int safeHashCount = Math.max(MIN_HASH_COUNT, Math.min(MAX_HASH_COUNT, hashCount));
        int safeCounterBytes = counterBytes == 2 ? 2 : 1;
        int safePageSize = Math.max(100, pageSize);
        countingBloomFilter.reinit(filterKey, safeCapacity, safeHashCount, safeCounterBytes);

        long offset = 0L;
        long loaded = 0L;
        while (true) {
            List<Long> ids = productCategoryMapper.listActiveCategoryIds(safePageSize, offset);
            if (ids == null || ids.isEmpty()) {
                break;
            }
            loaded += countingBloomFilter.addAllLongs(filterKey, ids);
            offset += ids.size();
        }
        log.info("Product category ID counting bloom initialized: key={}, loaded={}, capacity={}, hashCount={}, counterBytes={}, elapsedMs={}",
                filterKey, loaded, safeCapacity, safeHashCount, safeCounterBytes, System.currentTimeMillis() - start);
    }

    public boolean mightActiveCategoryExist(Long categoryId) {
        if (categoryId == null || categoryId < 0) {
            return false;
        }
        if (categoryId == 0) {
            return true;
        }
        if (!enabled) {
            return true;
        }
        try {
            return Boolean.TRUE.equals(countingBloomFilter.exists(filterKey, categoryId));
        } catch (Exception e) {
            log.warn("[Product category] Category ID Bloom query failed, falling back to cache/DB, categoryId={}", categoryId, e);
            return true;
        }
    }

    public void addActiveCategoryIdsAfterCommit(Collection<Long> categoryIds) {
        List<Long> ids = normalizeCategoryIds(categoryIds);
        if (ids.isEmpty() || !enabled) {
            return;
        }
        runAfterCommit(() -> {
            try {
                countingBloomFilter.addAllLongs(filterKey, ids);
            } catch (Exception e) {
                log.warn("[Product category] Batch add category IDs to Bloom failed, count={}", ids.size(), e);
            }
        });
    }

    public void removeActiveCategoryIdsAfterCommit(Collection<Long> categoryIds) {
        List<Long> ids = normalizeCategoryIds(categoryIds);
        if (ids.isEmpty() || !enabled) {
            return;
        }
        runAfterCommit(() -> {
            try {
                countingBloomFilter.deleteAllLongs(filterKey, ids);
            } catch (Exception e) {
                log.warn("[Product category] Batch delete category IDs from Bloom failed, count={}", ids.size(), e);
            }
        });
    }

    private void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private List<Long> normalizeCategoryIds(Collection<Long> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return List.of();
        }
        return categoryIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
    }
}
