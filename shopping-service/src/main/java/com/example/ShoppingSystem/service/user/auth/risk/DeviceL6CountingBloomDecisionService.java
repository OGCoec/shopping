package com.example.ShoppingSystem.service.user.auth.risk;

public interface DeviceL6CountingBloomDecisionService {
    public Integer resolveFastL6ScoreIfHit(String deviceFingerprint);

    public void syncMembershipByScore(String deviceFingerprint, int score);
}
