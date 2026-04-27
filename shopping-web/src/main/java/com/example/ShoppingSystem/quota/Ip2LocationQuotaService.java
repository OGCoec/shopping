package com.example.ShoppingSystem.quota;

import com.example.ShoppingSystem.redisdata.Ip2LocationQuotaRedisKeys;
import com.example.ShoppingSystem.redisdata.Ip2LocationQuotaRedisKeys.AccountType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * IP2Location.io quota service backed by Redis.
 */
@Service
public class Ip2LocationQuotaService {

    private static final Logger log = LoggerFactory.getLogger(Ip2LocationQuotaService.class);
    private static final DefaultRedisScript<List> UPSERT_SCRIPT = new DefaultRedisScript<>();
    private static final DefaultRedisScript<List> DECR_SCRIPT = new DefaultRedisScript<>();
    private static final DefaultRedisScript<List> COMPENSATE_SCRIPT = new DefaultRedisScript<>();
    private static final DefaultRedisScript<List> REBUILD_SCRIPT = new DefaultRedisScript<>();
    private static final DefaultRedisScript<Long> ROUND_ROBIN_NEXT_SCRIPT = new DefaultRedisScript<>();

    static {
        UPSERT_SCRIPT.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/ip2location_quota_upsert.lua")));
        UPSERT_SCRIPT.setResultType(List.class);

        DECR_SCRIPT.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/ip2location_quota_decr.lua")));
        DECR_SCRIPT.setResultType(List.class);

        COMPENSATE_SCRIPT.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/ip2location_quota_compensate.lua")));
        COMPENSATE_SCRIPT.setResultType(List.class);

        REBUILD_SCRIPT.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/ip2location_quota_rebuild.lua")));
        REBUILD_SCRIPT.setResultType(List.class);

        ROUND_ROBIN_NEXT_SCRIPT.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/ip2location_round_robin_next.lua")));
        ROUND_ROBIN_NEXT_SCRIPT.setResultType(Long.class);
    }

    private final StringRedisTemplate quotaRedisTemplate;

    public Ip2LocationQuotaService(@Qualifier("ip2LocationQuotaRedisTemplate") StringRedisTemplate quotaRedisTemplate) {
        this.quotaRedisTemplate = quotaRedisTemplate;
    }

    public List initializeMonthlyQuota(String apiKey, long remainingQuota) {
        return initializeMonthlyQuota(apiKey, remainingQuota, AccountType.STARTER);
    }

    public List initializeMonthlyQuota(String apiKey, AccountType accountType) {
        AccountType safeAccountType = safeAccountType(accountType);
        return initializeMonthlyQuota(apiKey, safeAccountType.defaultMonthlyQuota(), safeAccountType);
    }

    public List initializeMonthlyQuota(String apiKey, long remainingQuota, AccountType accountType) {
        LocalDateTime now = LocalDateTime.now();
        AccountType safeAccountType = safeAccountType(accountType);
        String quotaKey = buildQuotaKey(apiKey, now, safeAccountType);
        long safeRemainingQuota = Math.max(0L, remainingQuota);
        List result = quotaRedisTemplate.execute(
                UPSERT_SCRIPT,
                Arrays.asList(quotaKey, Ip2LocationQuotaRedisKeys.QUOTA_COUNT_KEY),
                String.valueOf(safeRemainingQuota)
        );
        applyQuotaKeyLifecycle(quotaKey, safeAccountType);
        log.info("Initialized IP2Location quota key, quotaKey={}, accountType={}, remainingQuota={}, defaultMonthlyQuota={}, ttl={}",
                quotaKey,
                safeAccountType,
                safeRemainingQuota,
                resolveQuotaAmount(safeAccountType),
                formatTtl(resolveQuotaTtl(safeAccountType)));
        return result;
    }

    public List decrementQuota(String quotaKey) {
        return quotaRedisTemplate.execute(
                DECR_SCRIPT,
                Arrays.asList(quotaKey, Ip2LocationQuotaRedisKeys.QUOTA_COUNT_KEY)
        );
    }

    public List compensateQuota(String quotaKey) {
        return quotaRedisTemplate.execute(
                COMPENSATE_SCRIPT,
                Arrays.asList(quotaKey, Ip2LocationQuotaRedisKeys.QUOTA_COUNT_KEY)
        );
    }

    public QuotaAcquireResult acquireQuotaForCall() {
        long totalQuota = getTotalQuotaCount();
        if (totalQuota <= 0) {
            return QuotaAcquireResult.denied(totalQuota, "quota_count_exhausted");
        }

        List<String> quotaKeys = getOrderedQuotaKeys();
        if (quotaKeys.isEmpty()) {
            rebuildQuotaCount();
            return QuotaAcquireResult.denied(getTotalQuotaCount(), "quota_key_not_found");
        }

        int startIndex = nextRoundRobinStartIndex(quotaKeys.size());
        for (int i = 0; i < quotaKeys.size(); i++) {
            int index = (startIndex + i) % quotaKeys.size();
            String quotaKey = quotaKeys.get(index);
            List result = decrementQuota(quotaKey);
            if (isDecrementSuccess(result)) {
                long newTotal = readLongFromLuaResult(result, 2, Math.max(0, totalQuota - 1));
                return QuotaAcquireResult.allowed(quotaKey, newTotal);
            }
        }

        rebuildQuotaCount();
        return QuotaAcquireResult.denied(getTotalQuotaCount(), "quota_exhausted_after_round_robin");
    }

    public long getTotalQuotaCount() {
        String raw = quotaRedisTemplate.opsForValue().get(Ip2LocationQuotaRedisKeys.QUOTA_COUNT_KEY);
        return parseLong(raw, 0L);
    }

    /**
     * Compatibility entry point. The old monthly-reset behavior is intentionally removed.
     * We now only rebuild the aggregate count to correct drift after key expiration.
     */
    public void refreshMonthlyQuota() {
        rebuildQuotaCount();
    }

    public List rebuildQuotaCount() {
        return quotaRedisTemplate.execute(
                REBUILD_SCRIPT,
                Collections.singletonList(Ip2LocationQuotaRedisKeys.QUOTA_COUNT_KEY),
                Ip2LocationQuotaRedisKeys.QUOTA_PREFIX
        );
    }

    public String buildQuotaKey(String apiKey, LocalDateTime dateTime) {
        return buildQuotaKey(apiKey, dateTime, AccountType.STARTER);
    }

    public String buildQuotaKey(String apiKey, LocalDateTime dateTime, AccountType accountType) {
        return Ip2LocationQuotaRedisKeys.buildQuotaKey(accountType, dateTime, apiKey);
    }

    public long resolveQuotaAmount(AccountType accountType) {
        return safeAccountType(accountType).defaultMonthlyQuota();
    }

    public Duration resolveQuotaTtl(AccountType accountType) {
        return safeAccountType(accountType).lifecycleTtl();
    }

    private List<String> getOrderedQuotaKeys() {
        Set<String> keys = getQuotaKeys();
        if (keys.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> ordered = new ArrayList<>(keys);
        ordered.sort(String::compareTo);
        return ordered;
    }

    private int nextRoundRobinStartIndex(int size) {
        Long startIndex = quotaRedisTemplate.execute(
                ROUND_ROBIN_NEXT_SCRIPT,
                Collections.singletonList(Ip2LocationQuotaRedisKeys.QUOTA_ROUND_ROBIN_CURSOR_KEY),
                String.valueOf(size)
        );
        long safeStartIndex = startIndex == null ? 0L : startIndex;
        return (int) Math.floorMod(safeStartIndex, size);
    }

    private boolean isDecrementSuccess(List result) {
        if (result == null || result.isEmpty()) {
            return false;
        }
        return readLong(result.get(0), Long.MIN_VALUE) == 0L;
    }

    private long readLongFromLuaResult(List result, int index, long fallback) {
        if (result == null || index < 0 || index >= result.size()) {
            return fallback;
        }
        return readLong(result.get(index), fallback);
    }

    private long parseLong(String raw, long fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private long readLong(Object value, long fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return parseLong(value.toString(), fallback);
    }

    private Set<String> getQuotaKeys() {
        Set<String> keys = quotaRedisTemplate.keys(Ip2LocationQuotaRedisKeys.QUOTA_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            return Collections.emptySet();
        }
        keys.remove(Ip2LocationQuotaRedisKeys.QUOTA_COUNT_KEY);
        return keys;
    }

    private void applyQuotaKeyLifecycle(String quotaKey, AccountType accountType) {
        Duration ttl = resolveQuotaTtl(accountType);
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            quotaRedisTemplate.persist(quotaKey);
            return;
        }
        quotaRedisTemplate.expire(quotaKey, ttl);
    }

    private AccountType safeAccountType(AccountType accountType) {
        return accountType == null ? AccountType.STARTER : accountType;
    }

    private String formatTtl(Duration ttl) {
        return ttl == null ? "PERSIST" : ttl.toString();
    }

    public record QuotaAcquireResult(boolean allowCall, String quotaKey, long totalQuotaCount, String reason) {

        public static QuotaAcquireResult allowed(String quotaKey, long totalQuotaCount) {
            return new QuotaAcquireResult(true, quotaKey, totalQuotaCount, null);
        }

        public static QuotaAcquireResult denied(long totalQuotaCount, String reason) {
            return new QuotaAcquireResult(false, null, totalQuotaCount, reason);
        }
    }
}
