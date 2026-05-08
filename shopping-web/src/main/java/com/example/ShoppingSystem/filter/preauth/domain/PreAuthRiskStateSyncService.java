package com.example.ShoppingSystem.filter.preauth.domain;

import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.filter.preauth.model.PreAuthBinding;
import com.example.ShoppingSystem.loginflow.LoginFlowCookieFactory;
import com.example.ShoppingSystem.redisdata.LoginRedisKeys;
import com.example.ShoppingSystem.redisdata.RegisterRedisKeys;
import com.example.ShoppingSystem.registerflow.RegisterFlowCookieFactory;
import com.example.ShoppingSystem.service.user.auth.login.LoginFlowSessionService;
import com.example.ShoppingSystem.service.user.auth.login.impl.LoginChallengePolicy;
import com.example.ShoppingSystem.service.user.auth.login.impl.LoginChallengeSessionService;
import com.example.ShoppingSystem.service.user.auth.login.model.LoginFlowSession;
import com.example.ShoppingSystem.service.user.auth.register.RegisterFlowSessionService;
import com.example.ShoppingSystem.service.user.auth.register.impl.ChallengeSessionService;
import com.example.ShoppingSystem.service.user.auth.register.model.RegisterFlowSession;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Keeps risk-derived login/register Redis state aligned with the latest pre-auth binding.
 */
@Service
public class PreAuthRiskStateSyncService {

    private static final Logger log = LoggerFactory.getLogger(PreAuthRiskStateSyncService.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final LoginFlowCookieFactory loginFlowCookieFactory;
    private final RegisterFlowCookieFactory registerFlowCookieFactory;
    private final LoginFlowSessionService loginFlowSessionService;
    private final RegisterFlowSessionService registerFlowSessionService;
    private final LoginChallengeSessionService loginChallengeSessionService;
    private final ChallengeSessionService registerChallengeSessionService;
    private final LoginChallengePolicy loginChallengePolicy;

    public PreAuthRiskStateSyncService(StringRedisTemplate stringRedisTemplate,
                                       LoginFlowCookieFactory loginFlowCookieFactory,
                                       RegisterFlowCookieFactory registerFlowCookieFactory,
                                       LoginFlowSessionService loginFlowSessionService,
                                       RegisterFlowSessionService registerFlowSessionService,
                                       LoginChallengeSessionService loginChallengeSessionService,
                                       ChallengeSessionService registerChallengeSessionService,
                                       LoginChallengePolicy loginChallengePolicy) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.loginFlowCookieFactory = loginFlowCookieFactory;
        this.registerFlowCookieFactory = registerFlowCookieFactory;
        this.loginFlowSessionService = loginFlowSessionService;
        this.registerFlowSessionService = registerFlowSessionService;
        this.loginChallengeSessionService = loginChallengeSessionService;
        this.registerChallengeSessionService = registerChallengeSessionService;
        this.loginChallengePolicy = loginChallengePolicy;
    }

    public void syncAfterBindingSaved(PreAuthBinding previous,
                                      PreAuthBinding current,
                                      HttpServletRequest request) {
        if (current == null || request == null || !riskStateChanged(previous, current)) {
            return;
        }

        boolean bindingRiskLevelChanged = !sameRiskLevel(previous == null ? null : previous.riskLevel(), current.riskLevel());
        boolean loginDerivedStateReset = syncLoginFlow(current, request, bindingRiskLevelChanged);
        boolean registerDerivedStateReset = syncRegisterFlow(current, request, bindingRiskLevelChanged);

        if (bindingRiskLevelChanged || loginDerivedStateReset || registerDerivedStateReset) {
            loginChallengeSessionService.consumeWafVerified(current.token());
        }
    }

    public void forceClearDerivedState(PreAuthBinding current, HttpServletRequest request) {
        if (current == null || request == null) {
            return;
        }

        syncLoginFlow(current, request, true);
        syncRegisterFlow(current, request, true);
        loginChallengeSessionService.consumeWafVerified(current.token());
    }

    private boolean syncLoginFlow(PreAuthBinding current,
                                  HttpServletRequest request,
                                  boolean bindingRiskLevelChanged) {
        String flowId = loginFlowCookieFactory.resolveFlowId(request);
        if (StrUtil.isBlank(flowId)) {
            return false;
        }

        LoginFlowSession session = loginFlowSessionService.readFlow(flowId);
        if (session == null || !StrUtil.equals(session.getPreAuthToken(), current.token())) {
            return false;
        }

        String effectiveRiskLevel = stricterRiskLevel(session.getRiskLevel(), current.riskLevel());
        int requiredFactorCount = requiredFactorCount(effectiveRiskLevel, session.getAvailableFactors() == null
                ? 0
                : session.getAvailableFactors().size());
        boolean shouldUpdateFlow = !sameRiskLevel(session.getRiskLevel(), effectiveRiskLevel)
                || session.getRequiredFactorCount() != requiredFactorCount;
        if (shouldUpdateFlow) {
            loginFlowSessionService.updateRiskLevel(
                    session.getFlowId(),
                    effectiveRiskLevel,
                    requiredFactorCount,
                    session.isRequirePhoneBinding()
            );
        }

        boolean resetDerivedState = true;
        if (resetDerivedState) {
            loginChallengeSessionService.clearPendingChallengeSelection(session.getEmail(), session.getDeviceFingerprint());
            stringRedisTemplate.delete(LoginRedisKeys.EMAIL_CODE_PREFIX + session.getFlowId());
            log.info("PreAuth risk sync cleared login derived state, flowId={}, riskLevel={}, riskLevelChanged={}, flowUpdated={}",
                    session.getFlowId(), effectiveRiskLevel, bindingRiskLevelChanged, shouldUpdateFlow);
        }
        return resetDerivedState;
    }

    private boolean syncRegisterFlow(PreAuthBinding current,
                                     HttpServletRequest request,
                                     boolean bindingRiskLevelChanged) {
        String flowId = registerFlowCookieFactory.resolveFlowId(request);
        if (StrUtil.isBlank(flowId)) {
            return false;
        }

        RegisterFlowSession session = registerFlowSessionService.readFlow(flowId);
        if (session == null || !StrUtil.equals(session.getPreAuthToken(), current.token())) {
            return false;
        }

        String effectiveRiskLevel = stricterRiskLevel(session.getRiskLevel(), current.riskLevel());
        boolean requirePhoneBinding = shouldRequireRegisterPhoneBinding(effectiveRiskLevel);
        boolean shouldUpdateFlow = !sameRiskLevel(session.getRiskLevel(), effectiveRiskLevel)
                || session.isRequirePhoneBinding() != requirePhoneBinding;
        if (shouldUpdateFlow) {
            registerFlowSessionService.updateRiskLevel(
                    session.getFlowId(),
                    effectiveRiskLevel,
                    requirePhoneBinding
            );
        }

        boolean resetDerivedState = true;
        if (resetDerivedState) {
            registerChallengeSessionService.clearPendingChallengeSelection(session.getEmail(), session.getDeviceFingerprint());
            stringRedisTemplate.delete(RegisterRedisKeys.EMAIL_CODE_PREFIX + session.getEmail());
            stringRedisTemplate.delete(RegisterRedisKeys.EMAIL_CODE_META_PREFIX + session.getEmail());
            stringRedisTemplate.delete(RegisterRedisKeys.EMAIL_CODE_CHALLENGE_PASSED_PREFIX + session.getFlowId());
            log.info("PreAuth risk sync cleared register derived state, flowId={}, riskLevel={}, riskLevelChanged={}, flowUpdated={}",
                    session.getFlowId(), effectiveRiskLevel, bindingRiskLevelChanged, shouldUpdateFlow);
        }
        return resetDerivedState;
    }

    private boolean riskStateChanged(PreAuthBinding previous, PreAuthBinding current) {
        if (previous == null) {
            return false;
        }
        return previous.ipScore() != current.ipScore()
                || previous.deviceScore() != current.deviceScore()
                || previous.score() != current.score()
                || !sameRiskLevel(previous.riskLevel(), current.riskLevel());
    }

    private int requiredFactorCount(String riskLevel, int availableFactorCount) {
        if (loginChallengePolicy.shouldRequireTwoFactors(riskLevel)) {
            return Math.max(1, Math.min(2, Math.max(1, availableFactorCount)));
        }
        return 1;
    }

    private boolean shouldRequireRegisterPhoneBinding(String riskLevel) {
        String normalized = normalizeRiskLevel(riskLevel);
        return "L3".equals(normalized) || "L4".equals(normalized) || "L5".equals(normalized);
    }

    private String stricterRiskLevel(String left, String right) {
        String normalizedLeft = normalizeRiskLevel(left);
        String normalizedRight = normalizeRiskLevel(right);
        if (StrUtil.isBlank(normalizedLeft)) {
            return normalizedRight;
        }
        if (StrUtil.isBlank(normalizedRight)) {
            return normalizedLeft;
        }
        return riskRank(normalizedRight) > riskRank(normalizedLeft) ? normalizedRight : normalizedLeft;
    }

    private boolean sameRiskLevel(String left, String right) {
        return StrUtil.equals(normalizeRiskLevel(left), normalizeRiskLevel(right));
    }

    private int riskRank(String riskLevel) {
        return switch (normalizeRiskLevel(riskLevel)) {
            case "L1" -> 1;
            case "L2" -> 2;
            case "L3" -> 3;
            case "L4" -> 4;
            case "L5" -> 5;
            case "L6" -> 6;
            default -> 0;
        };
    }

    private String normalizeRiskLevel(String riskLevel) {
        if (StrUtil.isBlank(riskLevel)) {
            return "";
        }
        String normalized = riskLevel.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "L1", "L2", "L3", "L4", "L5", "L6" -> normalized;
            default -> "";
        };
    }
}
