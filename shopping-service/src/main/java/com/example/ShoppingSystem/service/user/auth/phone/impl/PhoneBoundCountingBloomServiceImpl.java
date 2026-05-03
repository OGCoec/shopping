package com.example.ShoppingSystem.service.user.auth.phone.impl;

import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.config.PhoneBoundCountingBloomProperties;
import com.example.ShoppingSystem.mapper.UserLoginIdentityMapper;
import com.example.ShoppingSystem.redisfilter.CountingBloomFilter;
import com.example.ShoppingSystem.service.user.auth.phone.PhoneBoundCountingBloomService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

@Service
public class PhoneBoundCountingBloomServiceImpl implements PhoneBoundCountingBloomService {

    private static final Logger log = LoggerFactory.getLogger(PhoneBoundCountingBloomServiceImpl.class);

    private final CountingBloomFilter countingBloomFilter;
    private final UserLoginIdentityMapper userLoginIdentityMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final PhoneBoundCountingBloomProperties properties;
    private final Executor executor;

    public PhoneBoundCountingBloomServiceImpl(CountingBloomFilter countingBloomFilter,
                                              UserLoginIdentityMapper userLoginIdentityMapper,
                                              StringRedisTemplate stringRedisTemplate,
                                              PhoneBoundCountingBloomProperties properties,
                                              @Qualifier("phoneBoundCountingBloomExecutor") Executor executor) {
        this.countingBloomFilter = countingBloomFilter;
        this.userLoginIdentityMapper = userLoginIdentityMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.properties = properties;
        this.executor = executor;
    }

    @Override
    public void rebuildFromDatabase() {
        if (!properties.isEnabled()) {
            log.info("Phone-bound counting bloom initialization disabled.");
            return;
        }
        int safeCapacity = Math.max(600000, properties.getCapacity());
        int safeHashCount = Math.max(4, Math.min(25, properties.getHashCount()));
        int safeCounterBytes = properties.getCounterBytes() == 2 ? 2 : 1;
        int safePageSize = Math.max(100, properties.getPageSize());

        long start = System.currentTimeMillis();
        countingBloomFilter.reinit(properties.getKey(), safeCapacity, safeHashCount, safeCounterBytes);

        long totalRows = userLoginIdentityMapper.countVerifiedPhones();
        long offset = 0L;
        long loadedRows = 0L;
        while (true) {
            List<String> page = userLoginIdentityMapper.listVerifiedPhones(safePageSize, offset);
            if (page == null || page.isEmpty()) {
                break;
            }
            List<String> normalizedPhones = normalizePhones(page);
            loadedRows += countingBloomFilter.addAllItems(properties.getKey(), normalizedPhones);
            for (String phone : normalizedPhones) {
                stringRedisTemplate.opsForValue().set(memberKey(phone), "1");
            }
            offset += page.size();
        }

        log.info("Phone-bound counting bloom initialized, dbRows={}, loadedRows={}, capacity={}, hashCount={}, counterBytes={}, elapsedMs={}",
                totalRows, loadedRows, safeCapacity, safeHashCount, safeCounterBytes, System.currentTimeMillis() - start);
    }

    @Override
    public PhoneBoundLookupResult lookupVerifiedPhone(String normalizedE164) {
        if (!properties.isEnabled() || StrUtil.isBlank(normalizedE164)) {
            return new PhoneBoundLookupResult(false, true, "PHONE_BOUND_BLOOM_UNAVAILABLE");
        }
        try {
            boolean mightContain = Boolean.TRUE.equals(countingBloomFilter.exists(properties.getKey(), normalizedE164.trim()));
            return new PhoneBoundLookupResult(true, mightContain, mightContain ? "PHONE_ALREADY_BOUND" : "PHONE_BOUND_BLOOM_MISS");
        } catch (RuntimeException e) {
            log.warn("Phone-bound counting bloom lookup failed, degrading to DB lookup, phone={}, reason={}",
                    normalizedE164, e.getMessage());
            return new PhoneBoundLookupResult(false, true, "PHONE_BOUND_BLOOM_UNAVAILABLE");
        }
    }

    @Override
    public boolean mightContainVerifiedPhone(String normalizedE164) {
        PhoneBoundLookupResult result = lookupVerifiedPhone(normalizedE164);
        return !result.available() || result.mightContain();
    }

    @Override
    public void addVerifiedPhoneAsync(String normalizedE164) {
        if (!properties.isEnabled() || StrUtil.isBlank(normalizedE164)) {
            return;
        }
        String phone = normalizedE164.trim();
        executor.execute(() -> addVerifiedPhone(phone));
    }

    private void addVerifiedPhone(String phone) {
        try {
            Boolean firstSeen = stringRedisTemplate.opsForValue().setIfAbsent(memberKey(phone), "1");
            if (Boolean.TRUE.equals(firstSeen)) {
                countingBloomFilter.add(properties.getKey(), phone);
            }
        } catch (RuntimeException e) {
            log.warn("Phone-bound counting bloom add failed, phone={}, reason={}", phone, e.getMessage());
        }
    }

    private List<String> normalizePhones(List<String> phones) {
        List<String> normalizedPhones = new ArrayList<>(phones.size());
        for (String phone : phones) {
            if (StrUtil.isBlank(phone)) {
                continue;
            }
            String normalized = phone.trim().replace(" ", "").replace("-", "").replace("(", "").replace(")", "");
            if (normalized.startsWith("+")) {
                normalizedPhones.add(normalized);
            }
        }
        return normalizedPhones;
    }

    private String memberKey(String phone) {
        return properties.getMemberKeyPrefix() + phone;
    }
}
