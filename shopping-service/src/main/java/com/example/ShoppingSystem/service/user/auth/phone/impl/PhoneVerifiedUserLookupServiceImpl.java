package com.example.ShoppingSystem.service.user.auth.phone.impl;

import com.example.ShoppingSystem.config.PhoneVerifiedUserCountingBloomProperties;
import com.example.ShoppingSystem.mapper.UserLoginIdentityMapper;
import com.example.ShoppingSystem.service.user.auth.phone.PhoneVerifiedUserBloomService;
import com.example.ShoppingSystem.service.user.auth.phone.PhoneVerifiedUserLookupService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class PhoneVerifiedUserLookupServiceImpl implements PhoneVerifiedUserLookupService {

    private static final Logger log = LoggerFactory.getLogger(PhoneVerifiedUserLookupServiceImpl.class);
    private static final String TRUE_VALUE = "1";
    private static final String FALSE_VALUE = "0";

    private final UserLoginIdentityMapper userLoginIdentityMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final PhoneVerifiedUserBloomService phoneVerifiedUserBloomService;
    private final PhoneVerifiedUserCountingBloomProperties properties;
    private final Cache<Long, Boolean> localCache;

    public PhoneVerifiedUserLookupServiceImpl(UserLoginIdentityMapper userLoginIdentityMapper,
                                              StringRedisTemplate stringRedisTemplate,
                                              PhoneVerifiedUserBloomService phoneVerifiedUserBloomService,
                                              PhoneVerifiedUserCountingBloomProperties properties) {
        this.userLoginIdentityMapper = userLoginIdentityMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.phoneVerifiedUserBloomService = phoneVerifiedUserBloomService;
        this.properties = properties;
        this.localCache = Caffeine.newBuilder()
                .maximumSize(Math.max(1000L, properties.getLocalCacheMaximumSize()))
                .expireAfterWrite(Math.max(1, properties.getLocalCacheTtlMinutes()), java.util.concurrent.TimeUnit.MINUTES)
                .build();
    }

    @Override
    public boolean isPhoneVerified(Long userId) {
        if (userId == null || userId <= 0) {
            return false;
        }
        Boolean localValue = localCache.getIfPresent(userId);
        if (localValue != null) {
            return localValue;
        }

        Boolean redisValue = readRedisCache(userId);
        if (redisValue != null) {
            localCache.put(userId, redisValue);
            return redisValue;
        }

        PhoneVerifiedUserBloomService.PhoneVerifiedUserLookupResult bloomResult =
                phoneVerifiedUserBloomService.lookupPhoneVerifiedUser(userId);
        if (bloomResult.available() && !bloomResult.mightContain()) {
            cacheResolvedValue(userId, false);
            return false;
        }

        Boolean dbValue = userLoginIdentityMapper.findPhoneVerifiedByUserId(userId);
        boolean verified = Boolean.TRUE.equals(dbValue);
        if (verified) {
            markPhoneVerified(userId);
        } else {
            cacheResolvedValue(userId, false);
        }
        return verified;
    }

    @Override
    public boolean isPhoneVerified(Long userId, Boolean loadedPhoneVerified) {
        if (userId == null || userId <= 0) {
            return false;
        }
        if (loadedPhoneVerified != null) {
            if (Boolean.TRUE.equals(loadedPhoneVerified)) {
                markPhoneVerified(userId);
                return true;
            }
            cacheResolvedValue(userId, false);
            return false;
        }
        return isPhoneVerified(userId);
    }

    @Override
    public void markPhoneVerified(Long userId) {
        if (userId == null || userId <= 0) {
            return;
        }
        cacheResolvedValue(userId, true);
        phoneVerifiedUserBloomService.addPhoneVerifiedUserAsync(userId);
    }

    private Boolean readRedisCache(Long userId) {
        try {
            String value = stringRedisTemplate.opsForValue().get(cacheKey(userId));
            if (TRUE_VALUE.equals(value)) {
                return true;
            }
            if (FALSE_VALUE.equals(value)) {
                return false;
            }
            return null;
        } catch (RuntimeException e) {
            log.warn("Phone-verified-user Redis cache lookup failed, userId={}, reason={}", userId, e.getMessage());
            return null;
        }
    }

    private void cacheResolvedValue(Long userId, boolean verified) {
        localCache.put(userId, verified);
        try {
            int ttlMinutes = verified ? properties.getRedisPositiveTtlMinutes() : properties.getRedisNegativeTtlMinutes();
            stringRedisTemplate.opsForValue().set(
                    cacheKey(userId),
                    verified ? TRUE_VALUE : FALSE_VALUE,
                    Duration.ofMinutes(Math.max(1, ttlMinutes))
            );
        } catch (RuntimeException e) {
            log.warn("Phone-verified-user Redis cache write failed, userId={}, verified={}, reason={}",
                    userId, verified, e.getMessage());
        }
    }

    private String cacheKey(Long userId) {
        return properties.getCacheKeyPrefix() + userId;
    }
}
