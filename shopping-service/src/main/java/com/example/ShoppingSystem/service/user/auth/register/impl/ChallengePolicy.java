package com.example.ShoppingSystem.service.user.auth.register.impl;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.service.user.auth.register.model.ChallengeSelection;
import org.springframework.stereotype.Service;

import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.CHALLENGE_CLOUDFLARE_TURNSTILE;
import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.CHALLENGE_GOOGLE_RECAPTCHA_V2;
import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.CHALLENGE_GOOGLE_RECAPTCHA_V3_LEGACY;
import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.CHALLENGE_HCAPTCHA;
import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.CHALLENGE_HUTOOL_SHEAR;
import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.CHALLENGE_OPERATION_TIMEOUT;
import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.CHALLENGE_TIANAI;
import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.SUBTYPE_TIANAI_CONCAT;
import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.SUBTYPE_TIANAI_ROTATE;
import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.SUBTYPE_TIANAI_SLIDER;
import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.SUBTYPE_TIANAI_WORD_IMAGE_CLICK;

/**
 * 注册挑战策略服务。
 * <p>
 * 该类只负责“策略决策”，不做 Redis、网络调用或持久化：
 * 1) 总分 -> 风险等级映射；
 * 2) 风险等级 -> challenge 分流；
 * 3) challengeType/subType 规范化；
 * 4) 当前请求 challenge 选择合并规则（pending + risk + submitted）。
 */
@Service
public class ChallengePolicy {

    /**
     * 根据综合分映射风险等级。
     * 分数越高，风险越低。
     */
    public String resolveRiskLevel(int totalScore) {
        if (totalScore >= 8500) {
            return "L1";
        }
        if (totalScore >= 7500) {
            return "L2";
        }
        if (totalScore >= 6000) {
            return "L3";
        }
        if (totalScore >= 4800) {
            return "L4";
        }
        if (totalScore >= 3000) {
            return "L5";
        }
        return "L6";
    }

    /**
     * 按风险等级分流 challenge。
     * <p>
     * 当前策略（保持原逻辑）：
     * - L1：无挑战
     * - L2：50% 无挑战，25% Hutool，25% Tianai
     * - L3：1/3 Hutool，2/3 Tianai（四子类型离散分桶）
     * - L4：Turnstile / hCaptcha / Tianai 三选一
     * - L5：Turnstile / hCaptcha / OperationTimeout
     * - L6：OperationTimeout
     */
    public ChallengeSelection resolveChallengeSelection(String riskLevel) {
        return switch (riskLevel) {
            case "L1" -> ChallengeSelection.none();
            case "L2" -> {
                int choice = RandomUtil.randomInt(4);
                if (choice < 2) {
                    yield ChallengeSelection.none();
                }
                if (choice == 2) {
                    yield new ChallengeSelection(CHALLENGE_HUTOOL_SHEAR, null);
                }
                yield new ChallengeSelection(CHALLENGE_TIANAI, randomTianaiSubType());
            }
            case "L3" -> resolveL3ChallengeSelection(RandomUtil.randomInt(6));
            case "L4" -> {
                int choice = RandomUtil.randomInt(4);
                if (choice == 0) {
                    yield new ChallengeSelection(CHALLENGE_CLOUDFLARE_TURNSTILE, null);
                }
                if (choice == 1) {
                    yield new ChallengeSelection(CHALLENGE_HCAPTCHA, null);
                }
                if (choice == 2) {
                    yield new ChallengeSelection(CHALLENGE_GOOGLE_RECAPTCHA_V2, null);
                }
                yield new ChallengeSelection(CHALLENGE_TIANAI, randomTianaiSubType());
            }
            case "L5" -> {
                int choice = RandomUtil.randomInt(5);
                if (choice == 0) {
                    yield new ChallengeSelection(CHALLENGE_CLOUDFLARE_TURNSTILE, null);
                }
                if (choice == 1) {
                    yield new ChallengeSelection(CHALLENGE_HCAPTCHA, null);
                }
                if (choice == 2) {
                    yield new ChallengeSelection(CHALLENGE_GOOGLE_RECAPTCHA_V2, null);
                }
                yield new ChallengeSelection(CHALLENGE_OPERATION_TIMEOUT, null);
            }
            case "L6" -> new ChallengeSelection(CHALLENGE_OPERATION_TIMEOUT, null);
            default -> ChallengeSelection.none();
        };
    }

    /**
     * L3 分桶映射策略（固定映射，便于测试）。
     *
     * @param bucket 离散分桶编号，取值 [0, 5]
     */
    public static ChallengeSelection resolveL3ChallengeSelection(int bucket) {
        if (bucket < 0 || bucket >= 6) {
            throw new IllegalArgumentException("L3 challenge bucket must be in [0, 5]");
        }
        return switch (bucket) {
            case 0, 1 -> new ChallengeSelection(CHALLENGE_HUTOOL_SHEAR, null);
            case 2 -> new ChallengeSelection(CHALLENGE_TIANAI, SUBTYPE_TIANAI_SLIDER);
            case 3 -> new ChallengeSelection(CHALLENGE_TIANAI, SUBTYPE_TIANAI_ROTATE);
            case 4 -> new ChallengeSelection(CHALLENGE_TIANAI, SUBTYPE_TIANAI_CONCAT);
            default -> new ChallengeSelection(CHALLENGE_TIANAI, SUBTYPE_TIANAI_WORD_IMAGE_CLICK);
        };
    }

    /**
     * 在 Tianai 四个子类型间随机分流。
     */
    public String randomTianaiSubType() {
        int choice = RandomUtil.randomInt(4);
        return switch (choice) {
            case 0 -> SUBTYPE_TIANAI_SLIDER;
            case 1 -> SUBTYPE_TIANAI_ROTATE;
            case 2 -> SUBTYPE_TIANAI_CONCAT;
            default -> SUBTYPE_TIANAI_WORD_IMAGE_CLICK;
        };
    }

    /**
     * 规范化 challengeType（去空格 + 大写）。
     */
    public String normalizeChallengeType(String challengeType) {
        return StrUtil.isBlank(challengeType) ? null : challengeType.trim().toUpperCase();
    }

    /**
     * 判断 challengeType 是否在系统支持名单内。
     */
    public boolean isSupportedChallengeType(String challengeType) {
        return CHALLENGE_HUTOOL_SHEAR.equals(challengeType)
                || CHALLENGE_TIANAI.equals(challengeType)
            || CHALLENGE_CLOUDFLARE_TURNSTILE.equals(challengeType)
            || CHALLENGE_HCAPTCHA.equals(challengeType)
            || CHALLENGE_GOOGLE_RECAPTCHA_V2.equals(challengeType)
            || CHALLENGE_GOOGLE_RECAPTCHA_V3_LEGACY.equals(challengeType)
            || CHALLENGE_OPERATION_TIMEOUT.equals(challengeType);
    }

    /**
     * 规范化期望子类型：
     * 1) 非 Tianai 统一返回 null；
     * 2) Tianai 才保留并大写 expectedChallengeSubType。
     */
    public String normalizeExpectedChallengeSubType(String expectedChallengeType, String expectedChallengeSubType) {
        if (!CHALLENGE_TIANAI.equals(expectedChallengeType)) {
            return null;
        }
        return StrUtil.isBlank(expectedChallengeSubType) ? null : expectedChallengeSubType.trim().toUpperCase();
    }

    /**
     * 决定“当前请求”最终应使用的 challenge。
     * <p>
     * 优先级：
     * 1) 若 pending 存在，直接复用 pending；
     * 2) 若 submittedChallengeType 非法或不支持，回退到风险策略结果；
     * 3) 若 submittedChallengeType 合法，采用 submitted（兼容 pending 过期后的同轮重试）。
     */
    public ChallengeSelection resolveChallengeSelectionForCurrentAttempt(ChallengeSelection pendingChallengeSelection,
                                                                         ChallengeSelection riskBasedChallengeSelection,
                                                                         String submittedChallengeType,
                                                                         String submittedChallengeSubType) {
        if (pendingChallengeSelection != null) {
            return pendingChallengeSelection;
        }

        String normalizedSubmittedType = normalizeChallengeType(submittedChallengeType);
        if (StrUtil.isBlank(normalizedSubmittedType) || !isSupportedChallengeType(normalizedSubmittedType)) {
            return riskBasedChallengeSelection;
        }

        return new ChallengeSelection(
                normalizedSubmittedType,
                normalizeExpectedChallengeSubType(normalizedSubmittedType, submittedChallengeSubType));
    }
}
