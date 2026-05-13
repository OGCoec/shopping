package com.example.ShoppingSystem.security;

import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.filter.preauth.domain.PreAuthRiskService;
import com.example.ShoppingSystem.filter.preauth.model.PreAuthBinding;
import com.example.ShoppingSystem.filter.preauth.store.PreAuthBindingRepository;
import com.example.ShoppingSystem.filter.preauth.support.PreAuthProperties;
import com.example.ShoppingSystem.filter.preauth.support.PreAuthRequestResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;

@Service
public class OAuth2PreAuthRiskGuard {

    private static final String BLOCKED_ERROR = "PREAUTH_RETRY_LATER";
    private static final String BLOCKED_MESSAGE = "当前操作过于频繁，请稍后重试";
    private static final String PREAUTH_MISSING_MESSAGE = "登录环境校验缺失，请刷新页面后重试";
    private static final String PREAUTH_EXPIRED_MESSAGE = "登录环境校验已过期，请刷新页面后重试";
    private static final String IP_CHANGED_MESSAGE = "当前网络环境发生变化，请刷新页面后重试";
    private static final int L6_SCORE_THRESHOLD = 3000;

    private final PreAuthProperties properties;
    private final PreAuthRequestResolver requestResolver;
    private final PreAuthBindingRepository bindingRepository;
    private final PreAuthRiskService riskService;

    public OAuth2PreAuthRiskGuard(PreAuthProperties properties,
                                  PreAuthRequestResolver requestResolver,
                                  PreAuthBindingRepository bindingRepository,
                                  PreAuthRiskService riskService) {
        this.properties = properties;
        this.requestResolver = requestResolver;
        this.bindingRepository = bindingRepository;
        this.riskService = riskService;
    }

    public OAuth2PreAuthRiskDecision evaluate(HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return OAuth2PreAuthRiskDecision.allow();
        }

        String token = requestResolver.resolveIncomingToken(request);
        if (StrUtil.isBlank(token)) {
            return OAuth2PreAuthRiskDecision.block(
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "PREAUTH_MISSING",
                    PREAUTH_MISSING_MESSAGE
            );
        }

        PreAuthBinding binding = bindingRepository.load(token);
        if (binding == null) {
            return OAuth2PreAuthRiskDecision.block(
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "PREAUTH_EXPIRED",
                    PREAUTH_EXPIRED_MESSAGE
            );
        }

        String currentIp = requestResolver.resolveClientIp(request);
        if (StrUtil.isNotBlank(binding.currentIp()) && !StrUtil.equals(binding.currentIp(), currentIp)) {
            return OAuth2PreAuthRiskDecision.block(
                    HttpServletResponse.SC_CONFLICT,
                    "PREAUTH_IP_CHANGED_WAF_REQUIRED",
                    IP_CHANGED_MESSAGE
            );
        }

        if (isBlockedBinding(binding)) {
            return OAuth2PreAuthRiskDecision.block(
                    HttpServletResponse.SC_FORBIDDEN,
                    BLOCKED_ERROR,
                    BLOCKED_MESSAGE
            );
        }

        return OAuth2PreAuthRiskDecision.allow();
    }

    private boolean isBlockedBinding(PreAuthBinding binding) {
        return riskService.isBlockedRisk(binding.riskLevel())
                || binding.score() < L6_SCORE_THRESHOLD
                || binding.ipScore() < L6_SCORE_THRESHOLD
                || binding.deviceScore() < L6_SCORE_THRESHOLD;
    }
}
