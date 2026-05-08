package com.example.ShoppingSystem.service.user.auth.risk;

import com.example.ShoppingSystem.mapper.RegisterRiskProfileMapper;
import com.example.ShoppingSystem.redisfilter.CountingBloomFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Startup rebuild service for L6 device fingerprint counting bloom.
 */
@Service
public class L6DeviceCountingBloomInitializerService {

    private static final Logger log = LoggerFactory.getLogger(L6DeviceCountingBloomInitializerService.class);

    private static final int MIN_CAPACITY = 200;
    private static final int MIN_HASH_COUNT = 4;
    private static final int MAX_HASH_COUNT = 25;

    private final CountingBloomFilter countingBloomFilter;
    private final RegisterRiskProfileMapper registerRiskProfileMapper;

    @Value("${register.device-l6-counting-bloom.enabled:true}")
    private boolean enabled;

    @Value("${register.device-l6-counting-bloom.score-threshold:3000}")
    private int scoreThreshold;

    @Value("${register.device-l6-counting-bloom.capacity:150000}")
    private int fixedCapacity;

    @Value("${register.device-l6-counting-bloom.hash-count:7}")
    private int fixedHashCount;

    @Value("${register.device-l6-counting-bloom.page-size:2000}")
    private int pageSize;

    @Value("${register.device-l6-counting-bloom.key:register:device:l6:cbf}")
    private String filterKey;

    public L6DeviceCountingBloomInitializerService(CountingBloomFilter countingBloomFilter,
                                                   RegisterRiskProfileMapper registerRiskProfileMapper) {
        this.countingBloomFilter = countingBloomFilter;
        this.registerRiskProfileMapper = registerRiskProfileMapper;
    }

    public void rebuildL6FilterOnStartup() {
        if (!enabled) {
            log.info("L6 device counting bloom initialization disabled: register.device-l6-counting-bloom.enabled=false");
            return;
        }

        long start = System.currentTimeMillis();
        int safeThreshold = Math.max(scoreThreshold, 1);
        int safePageSize = Math.max(pageSize, 100);
        int safeCapacity = Math.max(MIN_CAPACITY, fixedCapacity);
        int safeHashCount = Math.max(MIN_HASH_COUNT, Math.min(MAX_HASH_COUNT, fixedHashCount));

        countingBloomFilter.reinit(filterKey, safeCapacity, safeHashCount);

        long totalRows = registerRiskProfileMapper.countDeviceFingerprintsByCurrentScoreLessThan(safeThreshold);
        long offset = 0L;
        long loadedRows = 0L;
        while (true) {
            List<String> page = registerRiskProfileMapper.listDeviceFingerprintsByCurrentScoreLessThan(
                    safeThreshold,
                    safePageSize,
                    offset);
            if (page == null || page.isEmpty()) {
                break;
            }
            loadedRows += countingBloomFilter.addAllItems(filterKey, page);
            offset += page.size();
        }

        log.info(
                "L6 device counting bloom initialized: filterKey={}, currentScore<{}, dbRows={}, loadedRows={}, capacity={}, hashCount={}, cost={}ms",
                filterKey,
                safeThreshold,
                totalRows,
                loadedRows,
                safeCapacity,
                safeHashCount,
                System.currentTimeMillis() - start);
    }
}
