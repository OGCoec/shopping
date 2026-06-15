package com.example.ShoppingSystem.service.captcha.strategy;

import cn.hutool.core.util.StrUtil;

public record CaptchaStrategyKey(String challengeType, String challengeSubType) {

    public static CaptchaStrategyKey of(String challengeType, String challengeSubType) {
        return new CaptchaStrategyKey(normalize(challengeType), normalize(challengeSubType));
    }

    public String mapKey() {
        return challengeType + "|" + StrUtil.nullToEmpty(challengeSubType);
    }

    private static String normalize(String value) {
        return StrUtil.blankToDefault(value, "").trim().toUpperCase();
    }
}
