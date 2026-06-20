package com.example.ShoppingSystem.coupon.service.impl.CouponClaimService;

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
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

import com.example.ShoppingSystem.coupon.service.CouponClaimService;
import com.example.ShoppingSystem.coupon.service.CouponRedisCacheService;
import com.example.ShoppingSystem.coupon.service.CouponRedisKeys;
@Service
public class CouponClaimServiceImpl implements CouponClaimService {

    private static final Logger log = LoggerFactory.getLogger(CouponClaimService.class);

    private static final int LUA_OK = 0;
    private static final int LUA_CACHE_MISSING = 1;
    private static final int LUA_STATUS_NOT_ACTIVE = 2;
    private static final int LUA_NOT_STARTED = 3;
    private static final int LUA_ENDED = 4;
    private static final int LUA_ALREADY_CLAIMED = 5;
    private static final int LUA_SOLD_OUT = 6;
    private static final Duration REBUILD_LOCK_TTL = Duration.ofSeconds(10);

    private final StringRedisTemplate stringRedisTemplate;
    private final HybridSemaphoreIdWorker hybridSemaphoreIdWorker;
    private final CouponRedisCacheService couponRedisCacheService;
    private final CouponClaimMessagePublisher couponClaimMessagePublisher;
    private final DefaultRedisScript<List> claimScript;
    private final DefaultRedisScript<List> compensateScript;

    public CouponClaimServiceImpl(StringRedisTemplate stringRedisTemplate,
                              HybridSemaphoreIdWorker hybridSemaphoreIdWorker,
                              CouponRedisCacheService couponRedisCacheService,
                              CouponClaimMessagePublisher couponClaimMessagePublisher) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.hybridSemaphoreIdWorker = hybridSemaphoreIdWorker;
        this.couponRedisCacheService = couponRedisCacheService;
        this.couponClaimMessagePublisher = couponClaimMessagePublisher;
        this.claimScript = redisScript("lua/coupon_claim.lua");
        this.compensateScript = redisScript("lua/coupon_claim_compensate.lua");
    }

    public CouponClaimResponse claim(String rawCouponTemplateId, Long userId) {
        if (userId == null || userId <= 0L) {
            return CouponClaimResponse.fail("COUPON_AUTH_REQUIRED", "Current user is not authenticated.");
        }
        byte[] couponTemplateId;
        try {
            couponTemplateId = HybridIdCodec.fromBase62(rawCouponTemplateId);
        } catch (IllegalArgumentException e) {
            return CouponClaimResponse.fail("COUPON_ID_INVALID", "Coupon id is invalid.");
        }

        String couponId = HybridIdCodec.toBase62(couponTemplateId);
        String claimId = HybridIdCodec.toBase62(hybridSemaphoreIdWorker.nextId());
        String userCouponId = HybridIdCodec.toBase62(hybridSemaphoreIdWorker.nextId());
        long nowMs = System.currentTimeMillis();

        List<?> result = executeClaimLua(couponId, String.valueOf(userId), userCouponId, claimId, nowMs);
        int code = resultCode(result);
        if (code == LUA_OK) {
            Long validStartAtEpochMs = resultLong(result, 1);
            Long validEndAtEpochMs = resultLong(result, 2);
            CouponClaimMessage message = buildMessage(
                    claimId,
                    couponId,
                    userCouponId,
                    userId,
                    validStartAtEpochMs,
                    validEndAtEpochMs,
                    nowMs
            );
            try {
                couponClaimMessagePublisher.publish(message);
                return CouponClaimResponse.ok(userCouponId);
            } catch (Exception e) {
                compensate(couponId, String.valueOf(userId), userCouponId, claimId);
                log.warn("[Coupon] claim message publish failed, couponId={}, userId={}, claimId={}",
                        couponId, userId, claimId, e);
                return CouponClaimResponse.fail("COUPON_CLAIM_MQ_FAILED", "Coupon claim failed, please retry.");
            }
        }
        if (code == LUA_CACHE_MISSING) {
            rebuildCouponCache(couponTemplateId, couponId);
            return CouponClaimResponse.fail("COUPON_CACHE_REBUILDING", "Coupon cache is rebuilding, please retry.");
        }
        if (code == LUA_STATUS_NOT_ACTIVE) {
            return CouponClaimResponse.fail("COUPON_NOT_ACTIVE", "Coupon is not active.");
        }
        if (code == LUA_NOT_STARTED) {
            return CouponClaimResponse.fail("COUPON_NOT_STARTED", "Coupon claim has not started.");
        }
        if (code == LUA_ENDED) {
            return CouponClaimResponse.fail("COUPON_CLAIM_ENDED", "Coupon claim has ended.");
        }
        if (code == LUA_ALREADY_CLAIMED) {
            return CouponClaimResponse.fail("COUPON_ALREADY_CLAIMED", "Coupon has already been claimed.");
        }
        if (code == LUA_SOLD_OUT) {
            return CouponClaimResponse.fail("COUPON_SOLD_OUT", "Coupon is sold out.");
        }
        return CouponClaimResponse.fail("COUPON_CLAIM_FAILED", "Coupon claim failed.");
    }

    private List<?> executeClaimLua(String couponId,
                                    String userId,
                                    String userCouponId,
                                    String claimId,
                                    long nowMs) {
        return stringRedisTemplate.execute(
                claimScript,
                List.of(
                        CouponRedisKeys.templateKey(couponId),
                        CouponRedisKeys.stockKey(couponId),
                        CouponRedisKeys.claimedKey(couponId),
                        CouponRedisKeys.claimPendingKey(claimId),
                        CouponRedisKeys.CLAIM_PENDING_INDEX_KEY,
                        CouponRedisKeys.STOCK_DIRTY_KEY
                ),
                String.valueOf(nowMs),
                userId,
                userCouponId,
                claimId,
                couponId,
                String.valueOf(nowMs)
        );
    }

    private void rebuildCouponCache(byte[] couponTemplateId, String couponId) {
        String lockKey = CouponRedisKeys.rebuildLockKey(couponId);
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", REBUILD_LOCK_TTL);
        if (!Boolean.TRUE.equals(locked)) {
            return;
        }
        try {
            Boolean cacheExists = stringRedisTemplate.hasKey(CouponRedisKeys.templateKey(couponId));
            if (!Boolean.TRUE.equals(cacheExists)) {
                boolean rebuilt = couponRedisCacheService.writeCouponToRedis(couponTemplateId);
                if (!rebuilt) {
                    log.warn("[Coupon] coupon cache rebuild skipped, couponId={}", couponId);
                }
            }
        } catch (Exception e) {
            log.warn("[Coupon] coupon cache rebuild failed, couponId={}", couponId, e);
        } finally {
            stringRedisTemplate.delete(lockKey);
        }
    }

    private void compensate(String couponId, String userId, String userCouponId, String claimId) {
        try {
            stringRedisTemplate.execute(
                    compensateScript,
                    List.of(
                            CouponRedisKeys.stockKey(couponId),
                            CouponRedisKeys.claimedKey(couponId),
                            CouponRedisKeys.claimPendingKey(claimId),
                            CouponRedisKeys.CLAIM_PENDING_INDEX_KEY,
                            CouponRedisKeys.STOCK_DIRTY_KEY
                    ),
                    userId,
                    userCouponId,
                    claimId,
                    couponId
            );
        } catch (Exception e) {
            log.warn("[Coupon] claim compensation failed, couponId={}, userId={}, claimId={}",
                    couponId, userId, claimId, e);
        }
    }

    private CouponClaimMessage buildMessage(String claimId,
                                            String couponId,
                                            String userCouponId,
                                            Long userId,
                                            Long validStartAtEpochMs,
                                            Long validEndAtEpochMs,
                                            long createdAtEpochMilli) {
        CouponClaimMessage message = new CouponClaimMessage();
        message.setClaimId(claimId);
        message.setCouponId(couponId);
        message.setUserCouponId(userCouponId);
        message.setUserId(userId);
        message.setValidStartAtEpochMs(validStartAtEpochMs);
        message.setValidEndAtEpochMs(validEndAtEpochMs);
        message.setCreatedAtEpochMilli(createdAtEpochMilli);
        message.setRetryCount(0);
        return message;
    }

    private int resultCode(List<?> result) {
        Long code = resultLong(result, 0);
        return code == null ? -1 : code.intValue();
    }

    private Long resultLong(List<?> result, int index) {
        if (result == null || result.size() <= index || result.get(index) == null) {
            return null;
        }
        Object value = result.get(index);
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private DefaultRedisScript<List> redisScript(String location) {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource(location)));
        script.setResultType(List.class);
        return script;
    }
}
