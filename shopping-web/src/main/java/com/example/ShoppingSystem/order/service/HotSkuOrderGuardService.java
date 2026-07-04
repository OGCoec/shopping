package com.example.ShoppingSystem.order.service;

import com.example.ShoppingSystem.order.redis.OrderRedisKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

@Component
public class HotSkuOrderGuardService {

    private static final Logger log = LoggerFactory.getLogger(HotSkuOrderGuardService.class);

    private static final Duration PENDING_USER_GRACE = Duration.ofSeconds(60);
    private static final Duration DEFAULT_PENDING_USER_TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate stringRedisTemplate;
    private final DefaultRedisScript<Long> compareDeleteScript;
    private final DefaultRedisScript<List> paidCleanupScript;
    private final DefaultRedisScript<List> paidCleanupBatchScript;

    public HotSkuOrderGuardService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.compareDeleteScript = longRedisScript("lua/hot_sku_pending_user_compare_delete.lua");
        this.paidCleanupScript = listRedisScript("lua/hot_sku_paid_cleanup.lua");
        this.paidCleanupBatchScript = listRedisScript("lua/hot_sku_paid_cleanup_batch.lua");
    }

    public boolean claimPendingUser(String skuId, Long userId, String orderNo, OffsetDateTime now, OffsetDateTime expireAt) {
        String pendingUserKey = OrderRedisKeys.hotSkuPendingUserKey(skuId, userId);
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(
                pendingUserKey,
                orderNo,
                Duration.ofMillis(pendingUserTtlMillis(now, expireAt))
        );
        return Boolean.TRUE.equals(acquired);
    }

    public String pendingUserOrderNo(String skuId, Long userId) {
        String value = stringRedisTemplate.opsForValue().get(OrderRedisKeys.hotSkuPendingUserKey(skuId, userId));
        return value == null ? "" : value.trim();
    }

    public boolean compareDeletePendingUser(String skuId, Long userId, String orderNo) {
        if (skuId == null || skuId.isBlank() || userId == null || orderNo == null || orderNo.isBlank()) {
            return false;
        }
        try {
            Long deleted = stringRedisTemplate.execute(
                    compareDeleteScript,
                    List.of(OrderRedisKeys.hotSkuPendingUserKey(skuId, userId)),
                    orderNo
            );
            return deleted != null && deleted > 0L;
        } catch (Exception e) {
            log.warn("[Order] hot SKU pending-user compare-delete failed, skuId={}, userId={}, orderNo={}",
                    skuId, userId, orderNo, e);
            return false;
        }
    }

    public void cleanupPaidOrder(String orderNo) {
        String normalizedOrderNo = normalizeOrderNo(orderNo);
        if (normalizedOrderNo.isBlank()) {
            return;
        }
        try {
            stringRedisTemplate.execute(
                    paidCleanupScript,
                    List.of(OrderRedisKeys.hotSkuHoldKey(normalizedOrderNo)),
                    normalizedOrderNo,
                    OrderRedisKeys.hotSkuPendingUserKeyPrefix()
            );
        } catch (Exception e) {
            log.warn("[Order] hot SKU paid cleanup failed, orderNo={}", normalizedOrderNo, e);
        }
    }

    public void cleanupPaidOrders(Collection<String> orderNos) {
        if (orderNos == null || orderNos.isEmpty()) {
            return;
        }
        List<String> normalizedOrderNos = orderNos.stream()
                .map(this::normalizeOrderNo)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
        if (normalizedOrderNos.isEmpty()) {
            return;
        }
        List<String> keys = normalizedOrderNos.stream()
                .map(OrderRedisKeys::hotSkuHoldKey)
                .toList();
        List<Object> args = new java.util.ArrayList<>(2 + normalizedOrderNos.size());
        args.add(String.valueOf(normalizedOrderNos.size()));
        args.add(OrderRedisKeys.hotSkuPendingUserKeyPrefix());
        args.addAll(normalizedOrderNos);
        try {
            stringRedisTemplate.execute(paidCleanupBatchScript, keys, args.toArray(new Object[0]));
        } catch (Exception e) {
            log.warn("[Order] hot SKU paid batch cleanup failed, size={}", normalizedOrderNos.size(), e);
        }
    }

    public long pendingUserTtlMillis(OffsetDateTime now, OffsetDateTime expireAt) {
        long ttl = expireAt == null || now == null
                ? DEFAULT_PENDING_USER_TTL.toMillis()
                : Duration.between(now, expireAt).toMillis();
        return Math.max(1_000L, ttl + PENDING_USER_GRACE.toMillis());
    }

    private String normalizeOrderNo(String orderNo) {
        return orderNo == null ? "" : orderNo.trim();
    }

    private DefaultRedisScript<Long> longRedisScript(String location) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource(location)));
        script.setResultType(Long.class);
        return script;
    }

    private DefaultRedisScript<List> listRedisScript(String location) {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource(location)));
        script.setResultType(List.class);
        return script;
    }
}
