package com.example.ShoppingSystem.filter.preauth.domain;
import com.example.ShoppingSystem.filter.preauth.model.PreAuthBinding;
public interface PreAuthIpChangePenaltyService {
    public PreAuthBinding applyShortTermPenalty(PreAuthBinding existing,
                                                String currentIp,
                                                String normalizedFingerprint);
}
