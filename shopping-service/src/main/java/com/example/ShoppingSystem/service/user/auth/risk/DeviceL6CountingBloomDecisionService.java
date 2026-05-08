package com.example.ShoppingSystem.service.user.auth.risk;

import com.example.ShoppingSystem.redisfilter.CountingBloomFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * L6 device fingerprint counting-bloom fast decision service.
 */
@Service
public class DeviceL6CountingBloomDecisionService {

    private static final int DEFAULT_L6_FAST_SCORE = 2999;

    private final CountingBloomFilter countingBloomFilter;

    @Value("${register.device-l6-counting-bloom.realtime-enabled:true}")
    private boolean realtimeEnabled;

    @Value("${register.device-l6-counting-bloom.l6-fast-score:2999}")
    private int l6FastScore;

    @Value("${register.device-l6-counting-bloom.key:register:device:l6:cbf}")
    private String filterKey;

    public DeviceL6CountingBloomDecisionService(CountingBloomFilter countingBloomFilter) {
        this.countingBloomFilter = countingBloomFilter;
    }

    public Integer resolveFastL6ScoreIfHit(String deviceFingerprint) {
        if (!realtimeEnabled || deviceFingerprint == null || deviceFingerprint.isBlank()) {
            return null;
        }

        String normalizedFingerprint = deviceFingerprint.trim();
        try {
            Boolean hit = countingBloomFilter.exists(filterKey, normalizedFingerprint);
            return Boolean.TRUE.equals(hit) ? normalizeFastScore(l6FastScore) : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public void syncMembershipByScore(String deviceFingerprint, int score) {
        if (deviceFingerprint == null || deviceFingerprint.isBlank()) {
            return;
        }

        String normalizedFingerprint = deviceFingerprint.trim();
        if (score < 3000) {
            ensureMembership(normalizedFingerprint);
        } else {
            removeMembership(normalizedFingerprint);
        }
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

    private void ensureMembership(String deviceFingerprint) {
        try {
            if (!Boolean.TRUE.equals(countingBloomFilter.exists(filterKey, deviceFingerprint))) {
                countingBloomFilter.add(filterKey, deviceFingerprint);
            }
        } catch (RuntimeException ignored) {
            // Ignore sync failure; real-time main flow should not be interrupted.
        }
    }

    private void removeMembership(String deviceFingerprint) {
        try {
            if (Boolean.TRUE.equals(countingBloomFilter.exists(filterKey, deviceFingerprint))) {
                countingBloomFilter.delete(filterKey, deviceFingerprint);
            }
        } catch (RuntimeException ignored) {
            // Ignore sync failure; real-time main flow should not be interrupted.
        }
    }
}
