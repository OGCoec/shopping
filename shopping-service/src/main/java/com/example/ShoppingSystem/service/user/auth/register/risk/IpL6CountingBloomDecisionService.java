package com.example.ShoppingSystem.service.user.auth.register.risk;

public interface IpL6CountingBloomDecisionService {
    public Integer resolveFastL6ScoreIfHit(String publicIp);

    public void syncMembershipByScore(String publicIp, int score);

    public long batchSyncMembershipByScore(String family, java.util.List<String> ips, int score);

    public String resolveFilterKeyForFamily(String family);
}
