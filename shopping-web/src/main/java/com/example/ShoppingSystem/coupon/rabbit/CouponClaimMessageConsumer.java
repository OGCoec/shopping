package com.example.ShoppingSystem.coupon.rabbit;

import com.example.ShoppingSystem.Utils.HybridIdCodec;
import com.example.ShoppingSystem.coupon.service.CouponRedisKeys;
import com.example.ShoppingSystem.mapper.coupon.UserCouponMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Component
public class CouponClaimMessageConsumer {

    private static final Logger log = LoggerFactory.getLogger(CouponClaimMessageConsumer.class);

    private final UserCouponMapper userCouponMapper;
    private final CouponClaimMessagePublisher couponClaimMessagePublisher;
    private final CouponClaimRabbitProperties properties;
    private final StringRedisTemplate stringRedisTemplate;
    private final TransactionTemplate transactionTemplate;

    public CouponClaimMessageConsumer(UserCouponMapper userCouponMapper,
                                      CouponClaimMessagePublisher couponClaimMessagePublisher,
                                      CouponClaimRabbitProperties properties,
                                      StringRedisTemplate stringRedisTemplate,
                                      TransactionTemplate transactionTemplate) {
        this.userCouponMapper = userCouponMapper;
        this.couponClaimMessagePublisher = couponClaimMessagePublisher;
        this.properties = properties;
        this.stringRedisTemplate = stringRedisTemplate;
        this.transactionTemplate = transactionTemplate;
    }

    @RabbitListener(
            queues = "${app.rabbitmq.coupon-claim.queue:coupon.claim.queue}",
            containerFactory = "couponClaimRabbitListenerContainerFactory"
    )
    public void consume(CouponClaimMessage message) {
        if (!isMessageUsable(message)) {
            log.warn("[Coupon] invalid claim message skipped, message={}", message);
            return;
        }
        try {
            if (!isPendingAlive(message)) {
                log.info("[Coupon] compensated or consumed claim message skipped, claimId={}, couponId={}, userId={}",
                        message.getClaimId(), message.getCouponId(), message.getUserId());
                return;
            }
            transactionTemplate.executeWithoutResult(status -> insertUserCoupon(message));
            deletePending(message.getClaimId());
            log.info("[Coupon] claim message consumed, claimId={}, couponId={}, userId={}, retryCount={}",
                    message.getClaimId(), message.getCouponId(), message.getUserId(), message.getRetryCount());
        } catch (Exception e) {
            handleFailure(message, e);
        }
    }

    private void insertUserCoupon(CouponClaimMessage message) {
        byte[] userCouponId = HybridIdCodec.fromBase62(message.getUserCouponId());
        byte[] couponTemplateId = HybridIdCodec.fromBase62(message.getCouponId());
        userCouponMapper.insertClaimedCouponIgnore(
                userCouponId,
                message.getUserId(),
                couponTemplateId,
                epochMillisToOffsetDateTime(message.getValidStartAtEpochMs()),
                epochMillisToOffsetDateTime(message.getValidEndAtEpochMs()),
                epochMillisToOffsetDateTime(message.getCreatedAtEpochMilli())
        );
    }

    private boolean isPendingAlive(CouponClaimMessage message) {
        Object pendingUserCouponId = stringRedisTemplate.opsForHash()
                .get(CouponRedisKeys.claimPendingKey(message.getClaimId()), "userCouponId");
        return message.getUserCouponId().equals(String.valueOf(pendingUserCouponId));
    }

    private void deletePending(String claimId) {
        String pendingKey = CouponRedisKeys.claimPendingKey(claimId);
        stringRedisTemplate.executePipelined(new SessionCallback<Object>() {
            @Override
            @SuppressWarnings({"rawtypes", "unchecked"})
            public Object execute(RedisOperations operations) {
                operations.delete(pendingKey);
                operations.opsForSet().remove(CouponRedisKeys.CLAIM_PENDING_INDEX_KEY, claimId);
                return null;
            }
        });
    }

    private void handleFailure(CouponClaimMessage message, Exception exception) {
        String errorMessage = exception.getMessage();
        if (message.getRetryCount() < properties.getMaxRetryCount()) {
            long delayMilli = retryDelay(message.getRetryCount());
            CouponClaimMessage retryMessage = message.nextRetry(errorMessage);
            couponClaimMessagePublisher.publishRetry(retryMessage, delayMilli);
            log.warn("[Coupon] claim message retry scheduled, claimId={}, couponId={}, userId={}, retryCount={}, delayMilli={}, error={}",
                    message.getClaimId(), message.getCouponId(), message.getUserId(), retryMessage.getRetryCount(), delayMilli, errorMessage);
            return;
        }

        CouponClaimMessage deadLetterMessage = message.markFailed(errorMessage);
        couponClaimMessagePublisher.publishDeadLetter(deadLetterMessage);
        log.error("[Coupon] claim message moved to dead letter, claimId={}, couponId={}, userId={}, retryCount={}, error={}",
                message.getClaimId(), message.getCouponId(), message.getUserId(), message.getRetryCount(), errorMessage);
    }

    private boolean isMessageUsable(CouponClaimMessage message) {
        return message != null
                && hasText(message.getClaimId())
                && hasText(message.getCouponId())
                && hasText(message.getUserCouponId())
                && message.getUserId() != null
                && message.getUserId() > 0L
                && message.getValidStartAtEpochMs() != null
                && message.getValidEndAtEpochMs() != null
                && message.getCreatedAtEpochMilli() != null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private OffsetDateTime epochMillisToOffsetDateTime(Long epochMillis) {
        return Instant.ofEpochMilli(epochMillis).atOffset(ZoneOffset.UTC);
    }

    private long retryDelay(int retryCount) {
        return switch (retryCount) {
            case 0 -> 10_000L;
            case 1 -> 30_000L;
            default -> 120_000L;
        };
    }
}
