package com.example.ShoppingSystem.service.user.auth.phone.impl;

import com.example.ShoppingSystem.config.PhoneBoundCountingBloomProperties;
import com.example.ShoppingSystem.config.PhoneVerifiedUserCountingBloomProperties;
import com.example.ShoppingSystem.mapper.UserLoginIdentityMapper;
import com.example.ShoppingSystem.redisfilter.CountingBloomFilter;
import com.example.ShoppingSystem.service.user.auth.phone.PhoneVerifiedUserBloomService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.Executor;

@Service
public class PhoneVerifiedUserBloomServiceImpl implements PhoneVerifiedUserBloomService {

    private static final Logger log = LoggerFactory.getLogger(PhoneVerifiedUserBloomServiceImpl.class);
    private static final String READY_VALUE = "1";
    private static final String ERROR_UNAVAILABLE = "PHONE_VERIFIED_USER_BLOOM_UNAVAILABLE";
    private static final String ERROR_MISS = "PHONE_VERIFIED_USER_BLOOM_MISS";
    private static final String ERROR_HIT = "PHONE_VERIFIED_USER_BLOOM_HIT";

    private final CountingBloomFilter countingBloomFilter;
    private final UserLoginIdentityMapper userLoginIdentityMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final PhoneBoundCountingBloomProperties phoneBoundProperties;
    private final PhoneVerifiedUserCountingBloomProperties properties;
    private final Executor executor;

    public PhoneVerifiedUserBloomServiceImpl(CountingBloomFilter countingBloomFilter,
                                             UserLoginIdentityMapper userLoginIdentityMapper,
                                             StringRedisTemplate stringRedisTemplate,
                                             PhoneBoundCountingBloomProperties phoneBoundProperties,
                                             PhoneVerifiedUserCountingBloomProperties properties,
                                             @Qualifier("phoneBoundCountingBloomExecutor") Executor executor) {
        this.countingBloomFilter = countingBloomFilter;
        this.userLoginIdentityMapper = userLoginIdentityMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.phoneBoundProperties = phoneBoundProperties;
        this.properties = properties;
        this.executor = executor;
    }

    @Override
    public void rebuildFromDatabase() {
        if (!properties.isEnabled()) {
            log.info("Phone-verified-user counting bloom initialization disabled.");
            return;
        }
        int safeCapacity = Math.max(600000, phoneBoundProperties.getCapacity());
        int safeHashCount = Math.max(4, Math.min(25, phoneBoundProperties.getHashCount()));
        int safeCounterBytes = phoneBoundProperties.getCounterBytes() == 2 ? 2 : 1;
        int safePageSize = Math.max(100, phoneBoundProperties.getPageSize());

        long start = System.currentTimeMillis();
        stringRedisTemplate.delete(properties.getReadyKey());
        countingBloomFilter.reinit(properties.getKey(), safeCapacity, safeHashCount, safeCounterBytes);

        long totalRows = userLoginIdentityMapper.countPhoneVerifiedUsers();
        long offset = 0L;
        long loadedRows = 0L;
        while (true) {
            List<Long> page = userLoginIdentityMapper.listPhoneVerifiedUserIds(safePageSize, offset);
            if (page == null || page.isEmpty()) {
                break;
            }
            loadedRows += countingBloomFilter.addAllLongs(properties.getKey(), page);
            offset += page.size();
        }
        stringRedisTemplate.opsForValue().set(properties.getReadyKey(), READY_VALUE);

        log.info("Phone-verified-user counting bloom initialized, dbRows={}, loadedRows={}, capacity={}, hashCount={}, counterBytes={}, elapsedMs={}",
                totalRows, loadedRows, safeCapacity, safeHashCount, safeCounterBytes, System.currentTimeMillis() - start);
    }

    @Override
    public PhoneVerifiedUserLookupResult lookupPhoneVerifiedUser(Long userId) {
        if (!properties.isEnabled() || userId == null || userId <= 0) {
            return new PhoneVerifiedUserLookupResult(false, true, ERROR_UNAVAILABLE);
        }
        try {
            if (!READY_VALUE.equals(stringRedisTemplate.opsForValue().get(properties.getReadyKey()))) {
                return new PhoneVerifiedUserLookupResult(false, true, ERROR_UNAVAILABLE);
            }
            boolean mightContain = Boolean.TRUE.equals(countingBloomFilter.exists(properties.getKey(), userId));
            return new PhoneVerifiedUserLookupResult(true, mightContain, mightContain ? ERROR_HIT : ERROR_MISS);
        } catch (RuntimeException e) {
            log.warn("Phone-verified-user counting bloom lookup failed, userId={}, reason={}", userId, e.getMessage());
            return new PhoneVerifiedUserLookupResult(false, true, ERROR_UNAVAILABLE);
        }
    }

    @Override
    public boolean mightContainPhoneVerifiedUser(Long userId) {
        PhoneVerifiedUserLookupResult result = lookupPhoneVerifiedUser(userId);
        return !result.available() || result.mightContain();
    }

    @Override
    public void addPhoneVerifiedUserAsync(Long userId) {
        if (!properties.isEnabled() || userId == null || userId <= 0) {
            return;
        }
        executor.execute(() -> addPhoneVerifiedUser(userId));
    }

    private void addPhoneVerifiedUser(Long userId) {
        try {
            countingBloomFilter.addIfAbsentLong(properties.getKey(), userId);
        } catch (RuntimeException e) {
            log.warn("Phone-verified-user counting bloom add failed, userId={}, reason={}", userId, e.getMessage());
        }
    }
}
