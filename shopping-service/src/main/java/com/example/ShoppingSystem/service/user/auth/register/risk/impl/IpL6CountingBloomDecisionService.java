package com.example.ShoppingSystem.service.user.auth.register.risk.impl;

import com.example.ShoppingSystem.redisfilter.CountingBloomFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * L6 IP counting-bloom fast decision service.
 * <p>
 * Responsibility:
 * 1) Check whether publicIp hits the L6 counting bloom filter.
 * 2) Return a fast L6 score when hit.
 * 3) Return null on miss or degradation, so normal score chain can continue.
 */
@Service
public class IpL6CountingBloomDecisionService {

    private static final int DEFAULT_L6_FAST_SCORE = 2999;

    private final CountingBloomFilter countingBloomFilter;
    private final StringRedisTemplate stringRedisTemplate;

    @Value("${register.ip-l6-counting-bloom.realtime-enabled:true}")
    private boolean realtimeEnabled;

    @Value("${register.ip-l6-counting-bloom.l6-fast-score:2999}")
    private int l6FastScore;

    @Value("${register.ip-l6-counting-bloom.ipv4-key:register:ip:l6:cbf:ipv4}")
    private String ipv4FilterKey;

    @Value("${register.ip-l6-counting-bloom.ipv6-key:register:ip:l6:cbf:ipv6}")
    private String ipv6FilterKey;

    @Value("${register.ip-l6-counting-bloom.member-key-prefix:register:ip:l6:member:}")
    private String memberKeyPrefix;

    public IpL6CountingBloomDecisionService(CountingBloomFilter countingBloomFilter,
                                            StringRedisTemplate stringRedisTemplate) {
        this.countingBloomFilter = countingBloomFilter;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * Resolve fast L6 score when bloom filter hits.
     *
     * @param publicIp public IP
     * @return L6 fast score when hit, otherwise null
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
            if (Boolean.TRUE.equals(hit)) {
                return normalizeFastScore(l6FastScore);
            }
            return null;
        } catch (RuntimeException ignored) {
            // Degrade to normal scoring chain when bloom lookup is unavailable.
            return null;
        }
    }

    /**
     * 按最终分数同步 L6 布隆成员关系。
     * <p>
     * 规则：
     * 1) score < 3000：加入 L6 计数布隆；
     * 2) score >= 3000：从 L6 计数布隆移除。
     * <p>
     * 幂等控制：
     * - 使用 Redis 精确成员标记 key，避免重复 add 导致计数持续累加。
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
            String memberKey = memberKeyPrefix + ip;
            Boolean firstSeen = stringRedisTemplate.opsForValue().setIfAbsent(memberKey, "1");
            if (Boolean.TRUE.equals(firstSeen)) {
                countingBloomFilter.add(filterKey, ip);
            }
        } catch (RuntimeException ignored) {
            // Ignore sync failure; real-time main flow should not be interrupted.
        }
    }

    private void removeMembership(String filterKey, String ip) {
        try {
            String memberKey = memberKeyPrefix + ip;
            Boolean removed = stringRedisTemplate.delete(memberKey);
            if (Boolean.TRUE.equals(removed)) {
                countingBloomFilter.delete(filterKey, ip);
            }
        } catch (RuntimeException ignored) {
            // Ignore sync failure; real-time main flow should not be interrupted.
        }
    }
}
