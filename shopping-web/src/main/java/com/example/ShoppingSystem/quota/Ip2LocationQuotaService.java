package com.example.ShoppingSystem.quota;

import com.example.ShoppingSystem.redisdata.Ip2LocationQuotaRedisKeys;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * IP2Location.io 月额度服务。
 * 使用 Lua 脚本保证 quota key 与总额度 count 的更新原子一致。
 */
@Service
public class Ip2LocationQuotaService {

    private static final DateTimeFormatter QUOTA_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH:mm");

    private static final DefaultRedisScript<List> UPSERT_SCRIPT = new DefaultRedisScript<>();
    private static final DefaultRedisScript<List> DECR_SCRIPT = new DefaultRedisScript<>();
    private static final DefaultRedisScript<List> COMPENSATE_SCRIPT = new DefaultRedisScript<>();
    private static final DefaultRedisScript<List> REBUILD_SCRIPT = new DefaultRedisScript<>();

    static {
        UPSERT_SCRIPT.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/ip2location_quota_upsert.lua")));
        UPSERT_SCRIPT.setResultType(List.class);

        DECR_SCRIPT.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/ip2location_quota_decr.lua")));
        DECR_SCRIPT.setResultType(List.class);

        COMPENSATE_SCRIPT.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/ip2location_quota_compensate.lua")));
        COMPENSATE_SCRIPT.setResultType(List.class);

        REBUILD_SCRIPT.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/ip2location_quota_rebuild.lua")));
        REBUILD_SCRIPT.setResultType(List.class);
    }

    private final StringRedisTemplate quotaRedisTemplate;

    public Ip2LocationQuotaService(@Qualifier("ip2LocationQuotaRedisTemplate") StringRedisTemplate quotaRedisTemplate) {
        this.quotaRedisTemplate = quotaRedisTemplate;
    }

    /**
     * 初始化或更新某个 API Key 的额度键，并原子维护总额度 count。
     */
    public List initializeMonthlyQuota(String apiKey, long remainingQuota) {
        LocalDateTime now = LocalDateTime.now();
        String quotaKey = buildQuotaKey(apiKey, now);
        return quotaRedisTemplate.execute(
                UPSERT_SCRIPT,
                Arrays.asList(quotaKey, Ip2LocationQuotaRedisKeys.QUOTA_COUNT_KEY),
                String.valueOf(remainingQuota)
        );
    }

    /**
     * 对指定 key 扣减 1 次额度，并原子扣减总额度 count。
     */
    public List decrementQuota(String quotaKey) {
        return quotaRedisTemplate.execute(
                DECR_SCRIPT,
                Arrays.asList(quotaKey, Ip2LocationQuotaRedisKeys.QUOTA_COUNT_KEY)
        );
    }

    /**
     * 外部 API 调用失败时补偿 1 次额度，并原子补偿总额度 count。
     */
    public List compensateQuota(String quotaKey) {
        return quotaRedisTemplate.execute(
                COMPENSATE_SCRIPT,
                Arrays.asList(quotaKey, Ip2LocationQuotaRedisKeys.QUOTA_COUNT_KEY)
        );
    }

    /**
     * 仅做 Redis 额度侧的调用许可判定，不触发任何外部 HTTP 请求。
     * 规则：
     * 1) ip2location:quota:count <= 0 时禁止调用；
     * 2) ip2location:quota:count >= 1 时按轮询选择 quota key；
     * 3) 当前 key 不可扣减时自动跳过，尝试下一个 key。
     */
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

        // count 与各 key 可能出现漂移，兜底重建后再返回禁止调用。
        rebuildQuotaCount();
        return QuotaAcquireResult.denied(getTotalQuotaCount(), "quota_exhausted_after_round_robin");
    }

    /**
     * 返回当前 Redis 里的总额度 count。无法解析时按 0 处理。
     */
    public long getTotalQuotaCount() {
        String raw = quotaRedisTemplate.opsForValue().get(Ip2LocationQuotaRedisKeys.QUOTA_COUNT_KEY);
        return parseLong(raw, 0L);
    }

    /**
     * 每 30 分钟扫描一次额度键，满 1 个月后自动创建新的额度键并删除旧键。
     * 完成后调用 Lua 脚本原子重算总额度 count。
     */
    public void refreshMonthlyQuota() {
        Set<String> keys = getQuotaKeys();
        if (keys.isEmpty()) {
            rebuildQuotaCount();
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        for (String key : keys) {
            String[] parts = key.split(":", 4);
            if (parts.length < 4) {
                continue;
            }

            LocalDateTime storedDateTime;
            try {
                storedDateTime = LocalDateTime.parse(parts[2], QUOTA_TIME_FORMATTER);
            } catch (Exception ignored) {
                continue;
            }

            String apiKey = parts[3];
            if (now.isBefore(storedDateTime.plusMonths(1))) {
                continue;
            }

            // 创建新月份额度 key
            String newQuotaKey = buildQuotaKey(apiKey, now);
            quotaRedisTemplate.execute(
                    UPSERT_SCRIPT,
                    Arrays.asList(newQuotaKey, Ip2LocationQuotaRedisKeys.QUOTA_COUNT_KEY),
                    String.valueOf(Ip2LocationQuotaRedisKeys.MONTHLY_QUOTA)
            );

            // 旧 key 无 TTL，必须手动删除
            if (!newQuotaKey.equals(key)) {
                quotaRedisTemplate.delete(key);
            }
        }

        // 原子重建 count，兜底修正中间过程的任何漂移
        rebuildQuotaCount();
    }

    /**
     * 从所有额度 key 重建总额度 count，作为兜底修正。
     */
    public List rebuildQuotaCount() {
        return quotaRedisTemplate.execute(
                REBUILD_SCRIPT,
                Collections.singletonList(Ip2LocationQuotaRedisKeys.QUOTA_COUNT_KEY),
                Ip2LocationQuotaRedisKeys.QUOTA_PREFIX
        );
    }

    public String buildQuotaKey(String apiKey, LocalDateTime dateTime) {
        return Ip2LocationQuotaRedisKeys.QUOTA_PREFIX + dateTime.format(QUOTA_TIME_FORMATTER) + ":" + apiKey;
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
        Long cursor = quotaRedisTemplate.opsForValue().increment(Ip2LocationQuotaRedisKeys.QUOTA_ROUND_ROBIN_CURSOR_KEY);
        long cursorValue = cursor == null ? 0L : cursor;
        return (int) Math.floorMod(cursorValue, size);
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

    public record QuotaAcquireResult(boolean allowCall, String quotaKey, long totalQuotaCount, String reason) {

        public static QuotaAcquireResult allowed(String quotaKey, long totalQuotaCount) {
            return new QuotaAcquireResult(true, quotaKey, totalQuotaCount, null);
        }

        public static QuotaAcquireResult denied(long totalQuotaCount, String reason) {
            return new QuotaAcquireResult(false, null, totalQuotaCount, reason);
        }
    }
}
