package com.example.ShoppingSystem.service.user.auth.register.model;

/**
 * 当前注册链路的挑战选择结果。
 *
 * @param type 挑战类型（如 TIANAI_CAPTCHA / HCAPTCHA）
 * @param subType 挑战子类型（仅 Tianai 需要；其它类型通常为 null）
 */
public record ChallengeSelection(String type, String subType) {

    /**
     * 返回“无挑战”对象。
     */
    public static ChallengeSelection none() {
        return new ChallengeSelection(null, null);
    }
}
