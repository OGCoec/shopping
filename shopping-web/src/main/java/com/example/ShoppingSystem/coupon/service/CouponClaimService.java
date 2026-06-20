package com.example.ShoppingSystem.coupon.service;

import com.example.ShoppingSystem.Utils.HybridIdCodec;
import com.example.ShoppingSystem.Utils.HybridSemaphoreIdWorker;
import com.example.ShoppingSystem.coupon.dto.CouponClaimResponse;
import com.example.ShoppingSystem.coupon.rabbit.CouponClaimMessage;
import com.example.ShoppingSystem.coupon.rabbit.CouponClaimMessagePublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import java.time.Duration;
import java.util.List;

public interface CouponClaimService {
    public CouponClaimResponse claim(String rawCouponTemplateId, Long userId);
}
