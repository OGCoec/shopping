package com.example.ShoppingSystem.service.user.auth.register.risk;

import com.example.ShoppingSystem.redisfilter.CountingBloomFilter;
import org.springframework.beans.factory.annotation.Value;

public interface IpL6CountingBloomDecisionService {
    public Integer resolveFastL6ScoreIfHit(String publicIp);

    public void syncMembershipByScore(String publicIp, int score);

    public long batchSyncMembershipByScore(String family, java.util.List<String> ips, int score);

    public String resolveFilterKeyForFamily(String family);
}
