package com.example.ShoppingSystem.admin.service.product;

import com.example.ShoppingSystem.Utils.ProductSkuIdCodec;
import com.example.ShoppingSystem.redisfilter.CountingBloomFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.List;

@Slf4j
@Service
public class AdminProductSkuBloomService {

    private final CountingBloomFilter countingBloomFilter;

    @Value("${shopping.admin.product-sku-bloom.enabled:true}")
    private boolean enabled;

    @Value("${shopping.admin.product-sku-bloom.key:shopping:admin:product:sku:id:cbf}")
    private String filterKey;

    public AdminProductSkuBloomService(CountingBloomFilter countingBloomFilter) {
        this.countingBloomFilter = countingBloomFilter;
    }

    public boolean mightSkuExist(String skuId) {
        String normalizedSkuId = normalizeSkuId(skuId);
        if (normalizedSkuId.isEmpty()) {
            return false;
        }
        if (!enabled) {
            return true;
        }
        try {
            return Boolean.TRUE.equals(countingBloomFilter.exists(filterKey, normalizedSkuId));
        } catch (Exception e) {
            log.warn("[Product SKU] SKU ID Bloom query failed, falling back to detail cache/DB, skuId={}", normalizedSkuId, e);
            return true;
        }
    }

    public void addSkuIdsAfterCommit(Collection<String> skuIds) {
        List<String> ids = normalizeSkuIds(skuIds);
        if (ids.isEmpty() || !enabled) {
            return;
        }
        runAfterCommit(() -> {
            try {
                countingBloomFilter.addAllItems(filterKey, ids);
            } catch (Exception e) {
                log.warn("[Product SKU] Batch add SKU IDs to Bloom failed, count={}", ids.size(), e);
            }
        });
    }

    public void removeSkuIdsAfterCommit(Collection<String> skuIds) {
        List<String> ids = normalizeSkuIds(skuIds);
        if (ids.isEmpty() || !enabled) {
            return;
        }
        runAfterCommit(() -> {
            try {
                countingBloomFilter.deleteAllItems(filterKey, ids);
            } catch (Exception e) {
                log.warn("[Product SKU] Batch delete SKU IDs from Bloom failed, count={}", ids.size(), e);
            }
        });
    }

    private List<String> normalizeSkuIds(Collection<String> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return List.of();
        }
        return skuIds.stream()
                .map(this::normalizeSkuId)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
    }

    private String normalizeSkuId(String skuId) {
        String value = skuId == null ? "" : skuId.trim();
        return value.matches(ProductSkuIdCodec.BASE62_PATTERN) ? value : "";
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
}
