package com.example.ShoppingSystem.coupon.service;

import com.example.ShoppingSystem.Utils.HybridIdCodec;
import com.example.ShoppingSystem.mapper.coupon.CouponScopeMapper;
import com.example.ShoppingSystem.mapper.coupon.CouponTemplateMapper;
import com.example.ShoppingSystem.mapper.coupon.UserCouponMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public interface CouponRedisCacheService {
    public boolean writeCouponToRedis(byte[] couponTemplateId);

    public void markDisabled(String couponId);

    public void deleteCouponRuntime(String couponId);
}
