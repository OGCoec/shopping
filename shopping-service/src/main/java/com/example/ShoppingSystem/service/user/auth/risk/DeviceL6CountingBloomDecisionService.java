package com.example.ShoppingSystem.service.user.auth.risk;

import com.example.ShoppingSystem.redisfilter.CountingBloomFilter;
import org.springframework.beans.factory.annotation.Value;

public interface DeviceL6CountingBloomDecisionService {
    public Integer resolveFastL6ScoreIfHit(String deviceFingerprint);

    public void syncMembershipByScore(String deviceFingerprint, int score);
}
