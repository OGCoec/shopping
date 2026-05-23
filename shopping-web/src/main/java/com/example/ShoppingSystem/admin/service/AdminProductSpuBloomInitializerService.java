package com.example.ShoppingSystem.admin.service;

import com.example.ShoppingSystem.mapper.ProductSpuMapper;
import com.example.ShoppingSystem.redisfilter.CountingBloomFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class AdminProductSpuBloomInitializerService {

    private static final int MIN_CAPACITY = 200;
    private static final int MIN_HASH_COUNT = 4;
    private static final int MAX_HASH_COUNT = 25;

    private final CountingBloomFilter countingBloomFilter;
    private final ProductSpuMapper productSpuMapper;

    @Value("${shopping.admin.product-spu-bloom.enabled:true}")
    private boolean enabled;

    @Value("${shopping.admin.product-spu-bloom.key:shopping:admin:product:spu:id:cbf}")
    private String filterKey;

    @Value("${shopping.admin.product-spu-bloom.capacity:2000000}")
    private int capacity;

    @Value("${shopping.admin.product-spu-bloom.hash-count:7}")
    private int hashCount;

    @Value("${shopping.admin.product-spu-bloom.counter-bytes:1}")
    private int counterBytes;

    @Value("${shopping.admin.product-spu-bloom.page-size:2000}")
    private int pageSize;

    public AdminProductSpuBloomInitializerService(CountingBloomFilter countingBloomFilter,
                                                 ProductSpuMapper productSpuMapper) {
        this.countingBloomFilter = countingBloomFilter;
        this.productSpuMapper = productSpuMapper;
    }

    public void rebuildOnStartup() {
        if (!enabled) {
            log.info("Admin product SPU ID counting bloom initialization disabled.");
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
            List<Long> ids = productSpuMapper.listAllSpuIds(safePageSize, offset);
            if (ids == null || ids.isEmpty()) {
                break;
            }
            loaded += countingBloomFilter.addAllLongs(filterKey, ids);
            offset += ids.size();
        }
        log.info("Admin product SPU ID counting bloom initialized: key={}, loaded={}, capacity={}, hashCount={}, counterBytes={}, elapsedMs={}",
                filterKey, loaded, safeCapacity, safeHashCount, safeCounterBytes, System.currentTimeMillis() - start);
    }
}
