package com.example.ShoppingSystem.service.captcha.strategy;

import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CaptchaStrategyRegistry {

    private final Map<String, CaptchaChallengeStrategy> strategies;

    public CaptchaStrategyRegistry(List<CaptchaChallengeStrategy> strategyList) {
        Map<String, CaptchaChallengeStrategy> collected = new LinkedHashMap<>();
        for (CaptchaChallengeStrategy strategy : strategyList) {
            for (CaptchaStrategyKey key : strategy.keys()) {
                String mapKey = key.mapKey();
                CaptchaChallengeStrategy previous = collected.putIfAbsent(mapKey, strategy);
                if (previous != null) {
                    throw new IllegalStateException("Duplicate captcha strategy key: " + mapKey);
                }
            }
        }
        this.strategies = Collections.unmodifiableMap(collected);
    }

    public CaptchaChallengeStrategy resolve(String challengeType, String challengeSubType) {
        return strategies.get(CaptchaStrategyKey.of(challengeType, challengeSubType).mapKey());
    }

    public boolean verify(CaptchaVerifyRequest request) {
        if (request == null) {
            return false;
        }
        CaptchaChallengeStrategy strategy = resolve(request.challengeType(), request.challengeSubType());
        return strategy != null && strategy.verify(request);
    }

    public CaptchaGenerateResult generate(CaptchaGenerateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Captcha generate request is required.");
        }
        CaptchaChallengeStrategy strategy = resolve(request.challengeType(), request.challengeSubType());
        if (strategy == null) {
            throw new IllegalArgumentException("Unsupported captcha strategy: "
                    + CaptchaStrategyKey.of(request.challengeType(), request.challengeSubType()).mapKey());
        }
        return strategy.generate(request);
    }

    public String siteKey(String challengeType, String challengeSubType) {
        CaptchaChallengeStrategy strategy = resolve(challengeType, challengeSubType);
        return strategy == null ? null : strategy.siteKey();
    }
}
