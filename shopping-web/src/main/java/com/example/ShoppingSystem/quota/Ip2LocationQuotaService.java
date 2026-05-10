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
import java.util.Collection;
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
    private static final DefaultRedisScript<List> ACQUIRE_SCRIPT = new DefaultRedisScript<>();
    private static final DefaultRedisScript<List> LIST_SCRIPT = new DefaultRedisScript<>();
    private static final DefaultRedisScript<List> BATCH_UPSERT_SCRIPT = new DefaultRedisScript<>();
    private static final DefaultRedisScript<List> BATCH_DELETE_SCRIPT = new DefaultRedisScript<>();
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

        ACQUIRE_SCRIPT.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/ip2location_quota_acquire.lua")));
        ACQUIRE_SCRIPT.setResultType(List.class);

        LIST_SCRIPT.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/ip2location_quota_list.lua")));
        LIST_SCRIPT.setResultType(List.class);

        BATCH_UPSERT_SCRIPT.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/ip2location_quota_batch_upsert.lua")));
        BATCH_UPSERT_SCRIPT.setResultType(List.class);

        BATCH_DELETE_SCRIPT.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/ip2location_quota_batch_delete.lua")));
        BATCH_DELETE_SCRIPT.setResultType(List.class);

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
        List result = quotaRedisTemplate.execute(
                ACQUIRE_SCRIPT,
                Arrays.asList(
                        Ip2LocationQuotaRedisKeys.QUOTA_COUNT_KEY,
                        Ip2LocationQuotaRedisKeys.QUOTA_ROUND_ROBIN_CURSOR_KEY
                ),
                Ip2LocationQuotaRedisKeys.QUOTA_PREFIX
        );
        return resolveAcquireResult(result);
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

    public QuotaKeyListResult listQuotaKeys() {
        List result = quotaRedisTemplate.execute(
                LIST_SCRIPT,
                Collections.singletonList(Ip2LocationQuotaRedisKeys.QUOTA_COUNT_KEY),
                Ip2LocationQuotaRedisKeys.QUOTA_PREFIX
        );
        return resolveQuotaKeyListResult(result);
    }

    public QuotaBatchUpsertResult batchUpsertQuotaKeys(Collection<QuotaKeyUpsertCommand> commands) {
        if (commands == null || commands.isEmpty()) {
            return new QuotaBatchUpsertResult(0, 0, getTotalQuotaCount());
        }
        List<String> args = new ArrayList<>(2 + commands.size() * 4);
        args.add(Ip2LocationQuotaRedisKeys.QUOTA_PREFIX);
        args.add(String.valueOf(commands.size()));
        for (QuotaKeyUpsertCommand command : commands) {
            args.add(command.apiKey());
            args.add(command.quotaKey());
            args.add(String.valueOf(Math.max(0L, command.remainingQuota())));
            args.add(String.valueOf(command.ttlSeconds()));
        }
        List result = quotaRedisTemplate.execute(
                BATCH_UPSERT_SCRIPT,
                Collections.singletonList(Ip2LocationQuotaRedisKeys.QUOTA_COUNT_KEY),
                args.toArray()
        );
        return resolveQuotaBatchUpsertResult(result);
    }

    public QuotaBatchDeleteResult batchDeleteQuotaKeys(Collection<String> quotaKeys) {
        if (quotaKeys == null || quotaKeys.isEmpty()) {
            return new QuotaBatchDeleteResult(0, getTotalQuotaCount());
        }
        List<String> args = new ArrayList<>(2 + quotaKeys.size());
        args.add(Ip2LocationQuotaRedisKeys.QUOTA_PREFIX);
        args.add(String.valueOf(quotaKeys.size()));
        args.addAll(quotaKeys);
        List result = quotaRedisTemplate.execute(
                BATCH_DELETE_SCRIPT,
                Arrays.asList(
                        Ip2LocationQuotaRedisKeys.QUOTA_COUNT_KEY,
                        Ip2LocationQuotaRedisKeys.QUOTA_ROUND_ROBIN_CURSOR_KEY
                ),
                args.toArray()
        );
        return resolveQuotaBatchDeleteResult(result);
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

    private QuotaAcquireResult resolveAcquireResult(List result) {
        if (result == null || result.isEmpty()) {
            return QuotaAcquireResult.denied(0L, "quota_acquire_failed");
        }
        long status = readLong(result.get(0), Long.MIN_VALUE);
        long totalQuota = readLongFromLuaResult(result, 2, 0L);
        if (status == 0L) {
            String quotaKey = readStringFromLuaResult(result, 1, "");
            if (!quotaKey.isBlank()) {
                return QuotaAcquireResult.allowed(quotaKey, totalQuota);
            }
            return QuotaAcquireResult.denied(totalQuota, "quota_acquire_failed");
        }
        return QuotaAcquireResult.denied(totalQuota, readStringFromLuaResult(result, 3, "quota_acquire_failed"));
    }

    private QuotaKeyListResult resolveQuotaKeyListResult(List result) {
        if (result == null || result.size() < 3 || readLong(result.get(0), Long.MIN_VALUE) != 0L) {
            return new QuotaKeyListResult(0L, 0L, Collections.emptyList());
        }
        long aggregateTotal = readLong(result.get(1), 0L);
        long realTotal = readLong(result.get(2), 0L);
        List<QuotaKeySnapshot> snapshots = new ArrayList<>();
        for (int index = 3; index + 2 < result.size(); index += 3) {
            String quotaKey = readStringFromLuaResult(result, index, "");
            if (quotaKey.isBlank()) {
                continue;
            }
            long remainingQuota = readLong(result.get(index + 1), 0L);
            long ttlSeconds = readLong(result.get(index + 2), -2L);
            snapshots.add(new QuotaKeySnapshot(quotaKey, remainingQuota, ttlSeconds));
        }
        return new QuotaKeyListResult(aggregateTotal, realTotal, snapshots);
    }

    private QuotaBatchUpsertResult resolveQuotaBatchUpsertResult(List result) {
        if (result == null || result.isEmpty() || readLong(result.get(0), Long.MIN_VALUE) != 0L) {
            return new QuotaBatchUpsertResult(0, 0, getTotalQuotaCount());
        }
        int upserted = (int) readLongFromLuaResult(result, 1, 0L);
        int oldDeleted = (int) readLongFromLuaResult(result, 2, 0L);
        long totalQuotaCount = readLongFromLuaResult(result, 3, 0L);
        return new QuotaBatchUpsertResult(upserted, oldDeleted, totalQuotaCount);
    }

    private QuotaBatchDeleteResult resolveQuotaBatchDeleteResult(List result) {
        if (result == null || result.isEmpty() || readLong(result.get(0), Long.MIN_VALUE) != 0L) {
            return new QuotaBatchDeleteResult(0, getTotalQuotaCount());
        }
        int deleted = (int) readLongFromLuaResult(result, 1, 0L);
        long totalQuotaCount = readLongFromLuaResult(result, 2, 0L);
        return new QuotaBatchDeleteResult(deleted, totalQuotaCount);
    }

    private long readLongFromLuaResult(List result, int index, long fallback) {
        if (result == null || index < 0 || index >= result.size()) {
            return fallback;
        }
        return readLong(result.get(index), fallback);
    }

    private String readStringFromLuaResult(List result, int index, String fallback) {
        if (result == null || index < 0 || index >= result.size()) {
            return fallback;
        }
        Object value = result.get(index);
        if (value == null) {
            return fallback;
        }
        String text = value.toString();
        return text.isBlank() ? fallback : text;
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

    public record QuotaKeySnapshot(String quotaKey, long remainingQuota, long ttlSeconds) {
    }

    public record QuotaKeyListResult(long aggregateTotalQuotaCount,
                                     long realTotalQuotaCount,
                                     List<QuotaKeySnapshot> quotaKeys) {
    }

    public record QuotaKeyUpsertCommand(String apiKey,
                                        String quotaKey,
                                        long remainingQuota,
                                        long ttlSeconds) {
    }

    public record QuotaBatchUpsertResult(int upsertedCount,
                                         int oldDeletedCount,
                                         long totalQuotaCount) {
    }

    public record QuotaBatchDeleteResult(int deletedCount,
                                         long totalQuotaCount) {
    }
}
