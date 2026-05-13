package com.example.ShoppingSystem.service.user.auth.register.risk.impl;

import com.example.ShoppingSystem.redisfilter.CountingBloomFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * L6 IP counting-bloom fast decision service.
 */
@Service
public class IpL6CountingBloomDecisionService {

    private static final int DEFAULT_L6_FAST_SCORE = 2999;

    private final CountingBloomFilter countingBloomFilter;

    @Value("${register.ip-l6-counting-bloom.realtime-enabled:true}")
    private boolean realtimeEnabled;

    @Value("${register.ip-l6-counting-bloom.l6-fast-score:2999}")
    private int l6FastScore;

    @Value("${register.ip-l6-counting-bloom.ipv4-key:register:ip:l6:cbf:ipv4}")
    private String ipv4FilterKey;

    @Value("${register.ip-l6-counting-bloom.ipv6-key:register:ip:l6:cbf:ipv6}")
    private String ipv6FilterKey;

    public IpL6CountingBloomDecisionService(CountingBloomFilter countingBloomFilter) {
        this.countingBloomFilter = countingBloomFilter;
    }

    /**
     * Return a fast L6 score when the IP already exists in the L6 counting bloom filter.
     */
    public Integer resolveFastL6ScoreIfHit(String publicIp) {
        if (!realtimeEnabled || publicIp == null || publicIp.isBlank()) {
            return null;
        }

        String normalizedIp = publicIp.trim();
        String filterKey = resolveFilterKey(normalizedIp);
        if (filterKey == null) {
            return null;
        }

        try {
            Boolean hit = countingBloomFilter.exists(filterKey, normalizedIp);
            return Boolean.TRUE.equals(hit) ? normalizeFastScore(l6FastScore) : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /**
     * Sync L6 membership by final IP score.
     * score < 3000 adds the IP only when the counting bloom does not already contain it.
     * score >= 3000 best-effort deletes the IP when it appears present.
     */
    public void syncMembershipByScore(String publicIp, int score) {
        if (publicIp == null || publicIp.isBlank()) {
            return;
        }
        String normalizedIp = publicIp.trim();
        String filterKey = resolveFilterKey(normalizedIp);
        if (filterKey == null) {
            return;
        }

        if (score < 3000) {
            ensureMembership(filterKey, normalizedIp);
        } else {
            removeMembership(filterKey, normalizedIp);
        }
    }

    private String resolveFilterKey(String ip) {
        if (ip.contains(":")) {
            return ipv6FilterKey;
        }
        if (ip.contains(".")) {
            return ipv4FilterKey;
        }
        return null;
    }

    private int normalizeFastScore(int configuredScore) {
        if (configuredScore < 0) {
            return 0;
        }
        if (configuredScore >= 3000) {
            return DEFAULT_L6_FAST_SCORE;
        }
        return configuredScore;
    }

    private void ensureMembership(String filterKey, String ip) {
        try {
            if (!Boolean.TRUE.equals(countingBloomFilter.exists(filterKey, ip))) {
                countingBloomFilter.add(filterKey, ip);
            }
        } catch (RuntimeException ignored) {
            // Ignore sync failure; real-time main flow should not be interrupted.
        }
    }

    private void removeMembership(String filterKey, String ip) {
        try {
            if (Boolean.TRUE.equals(countingBloomFilter.exists(filterKey, ip))) {
                countingBloomFilter.delete(filterKey, ip);
            }
        } catch (RuntimeException ignored) {
            // Ignore sync failure; real-time main flow should not be interrupted.
        }
    }

    /**
     * 批量同步 L6 成员（管理端操作，一次 Lua 往返）。
     *
     * @param family "ipv4" 或 "ipv6"
     * @param ips    IP 列表
     * @param score  目标分数（score < 3000 → 添加，score >= 3000 → 移除）
     * @return 同步成功的元素数量
     */
    public long batchSyncMembershipByScore(String family, java.util.List<String> ips, int score) {
        if (ips == null || ips.isEmpty()) {
            return 0;
        }
        String filterKey = resolveFilterKeyForFamily(family);
        if (filterKey == null) {
            return 0;
        }
        try {
            if (score < 3000) {
                return countingBloomFilter.addAllItems(filterKey, ips);
            } else {
                return countingBloomFilter.deleteAllItems(filterKey, ips);
            }
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    /**
     * 根据 family 返回对应的布隆过滤器 Redis key。
     */
    public String resolveFilterKeyForFamily(String family) {
        if ("ipv4".equalsIgnoreCase(family)) {
            return ipv4FilterKey;
        }
        if ("ipv6".equalsIgnoreCase(family)) {
            return ipv6FilterKey;
        }
        return null;
    }
}
