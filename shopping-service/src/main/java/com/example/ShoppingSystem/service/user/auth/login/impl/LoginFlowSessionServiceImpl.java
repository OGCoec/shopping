package com.example.ShoppingSystem.service.user.auth.login.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.example.ShoppingSystem.service.user.auth.login.LoginFlowProperties;
import com.example.ShoppingSystem.service.user.auth.login.LoginFlowSessionService;
import com.example.ShoppingSystem.service.user.auth.login.model.LoginFactor;
import com.example.ShoppingSystem.service.user.auth.login.model.LoginFlowSession;
import com.example.ShoppingSystem.service.user.auth.login.model.LoginFlowStep;
import com.example.ShoppingSystem.service.user.auth.login.model.LoginFlowValidationError;
import com.example.ShoppingSystem.service.user.auth.login.model.LoginFlowValidationResult;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class LoginFlowSessionServiceImpl implements LoginFlowSessionService {

    private final StringRedisTemplate stringRedisTemplate;
    private final LoginFlowProperties properties;

    public LoginFlowSessionServiceImpl(StringRedisTemplate stringRedisTemplate,
                                       LoginFlowProperties properties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.properties = properties;
    }

    @Override
    public LoginFlowSession startFlow(String email,
                                      Long userId,
                                      String riskLevel,
                                      Set<LoginFactor> availableFactors,
                                      int requiredFactorCount,
                                      boolean requirePhoneBinding,
                                      String deviceFingerprint,
                                      String preAuthToken) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedDeviceFingerprint = normalizeText(deviceFingerprint);
        String normalizedPreAuthToken = normalizeText(preAuthToken);
        if (StrUtil.hasBlank(normalizedEmail, normalizedDeviceFingerprint, normalizedPreAuthToken)) {
            throw new IllegalArgumentException("Login flow requires email, device fingerprint, and preauth token.");
        }
        LoginFlowSession session = LoginFlowSession.builder()
                .flowId(IdUtil.nanoId(48))
                .preAuthToken(normalizedPreAuthToken)
                .deviceFingerprint(normalizedDeviceFingerprint)
                .email(normalizedEmail)
                .userId(userId)
                .riskLevel(normalizeRiskLevel(riskLevel))
                .step(LoginFlowStep.PASSWORD)
                .availableFactors(normalizeFactors(availableFactors))
                .completedFactors(Set.of())
                .requiredFactorCount(Math.max(1, requiredFactorCount))
                .requirePhoneBinding(requirePhoneBinding)
                .completed(false)
                .build();
        save(session);
        return session;
    }

    @Override
    public LoginFlowSession readFlow(String flowId) {
        String normalizedFlowId = normalizeText(flowId);
        if (normalizedFlowId == null) {
            return null;
        }
        return deserialize(stringRedisTemplate.opsForValue().get(flowKey(normalizedFlowId)));
    }

    @Override
    public LoginFlowValidationResult validate(String flowId, String preAuthToken) {
        return validateInternal(flowId, preAuthToken, null, false);
    }

    @Override
    public LoginFlowValidationResult validate(String flowId, String preAuthToken, String deviceFingerprint) {
        return validateInternal(flowId, preAuthToken, deviceFingerprint, true);
    }

    @Override
    public LoginFlowSession updateStep(String flowId,
                                       LoginFlowStep step,
                                       Set<LoginFactor> completedFactors,
                                       boolean requirePhoneBinding,
                                       boolean completed) {
        LoginFlowSession existing = readFlow(flowId);
        if (existing == null || step == null) {
            return null;
        }
        LoginFlowSession updated = LoginFlowSession.builder()
                .flowId(existing.getFlowId())
                .preAuthToken(existing.getPreAuthToken())
                .deviceFingerprint(existing.getDeviceFingerprint())
                .email(existing.getEmail())
                .userId(existing.getUserId())
                .riskLevel(existing.getRiskLevel())
                .step(step)
                .availableFactors(existing.getAvailableFactors())
                .completedFactors(normalizeFactors(completedFactors))
                .requiredFactorCount(existing.getRequiredFactorCount())
                .requirePhoneBinding(requirePhoneBinding)
                .completed(completed)
                .build();
        save(updated);
        return updated;
    }

    @Override
    public LoginFlowSession resolveIdentity(String flowId,
                                            Long userId,
                                            Set<LoginFactor> availableFactors,
                                            int requiredFactorCount,
                                            boolean requirePhoneBinding) {
        LoginFlowSession existing = readFlow(flowId);
        if (existing == null || userId == null) {
            return null;
        }
        LoginFlowSession updated = LoginFlowSession.builder()
                .flowId(existing.getFlowId())
                .preAuthToken(existing.getPreAuthToken())
                .deviceFingerprint(existing.getDeviceFingerprint())
                .email(existing.getEmail())
                .userId(userId)
                .riskLevel(existing.getRiskLevel())
                .step(existing.getStep())
                .availableFactors(normalizeFactors(availableFactors))
                .completedFactors(existing.getCompletedFactors())
                .requiredFactorCount(Math.max(1, requiredFactorCount))
                .requirePhoneBinding(requirePhoneBinding)
                .completed(existing.isCompleted())
                .build();
        save(updated);
        return updated;
    }

    private LoginFlowValidationResult validateInternal(String flowId,
                                                       String preAuthToken,
                                                       String deviceFingerprint,
                                                       boolean requireDeviceFingerprint) {
        String normalizedFlowId = normalizeText(flowId);
        if (normalizedFlowId == null) {
            return LoginFlowValidationResult.invalid(LoginFlowValidationError.MISSING_FLOW_ID);
        }

        LoginFlowSession existing = readFlow(normalizedFlowId);
        if (existing == null) {
            return LoginFlowValidationResult.invalid(LoginFlowValidationError.EXPIRED);
        }

        String normalizedPreAuthToken = normalizeText(preAuthToken);
        if (!StrUtil.equals(existing.getPreAuthToken(), normalizedPreAuthToken)) {
            return LoginFlowValidationResult.invalid(LoginFlowValidationError.PREAUTH_MISMATCH);
        }

        if (requireDeviceFingerprint) {
            String normalizedDeviceFingerprint = normalizeText(deviceFingerprint);
            if (!StrUtil.equals(existing.getDeviceFingerprint(), normalizedDeviceFingerprint)) {
                return LoginFlowValidationResult.invalid(LoginFlowValidationError.DEVICE_MISMATCH);
            }
        }

        save(existing);
        return LoginFlowValidationResult.valid(existing);
    }

    private void save(LoginFlowSession session) {
        stringRedisTemplate.opsForValue().set(
                flowKey(session.getFlowId()),
                serialize(session),
                Math.max(1, properties.getTtlMinutes()),
                TimeUnit.MINUTES
        );
    }

    private String flowKey(String flowId) {
        return properties.getRedisKeyPrefix() + flowId;
    }

    private String serialize(LoginFlowSession session) {
        JSONObject json = JSONUtil.createObj();
        json.set("flowId", session.getFlowId());
        json.set("preAuthToken", session.getPreAuthToken());
        json.set("deviceFingerprint", session.getDeviceFingerprint());
        json.set("email", session.getEmail());
        json.set("userId", session.getUserId());
        json.set("riskLevel", normalizeRiskLevel(session.getRiskLevel()));
        json.set("step", session.getStep() == null ? "" : session.getStep().name());
        json.set("availableFactors", factorsToArray(session.getAvailableFactors()));
        json.set("completedFactors", factorsToArray(session.getCompletedFactors()));
        json.set("requiredFactorCount", session.getRequiredFactorCount());
        json.set("requirePhoneBinding", session.isRequirePhoneBinding());
        json.set("completed", session.isCompleted());
        return json.toString();
    }

    private LoginFlowSession deserialize(String json) {
        if (StrUtil.isBlank(json)) {
            return null;
        }
        try {
            JSONObject object = JSONUtil.parseObj(json);
            LoginFlowStep step = LoginFlowStep.fromStorage(object.getStr("step"));
            if (step == null) {
                return null;
            }
            return LoginFlowSession.builder()
                    .flowId(normalizeText(object.getStr("flowId")))
                    .preAuthToken(normalizeText(object.getStr("preAuthToken")))
                    .deviceFingerprint(normalizeText(object.getStr("deviceFingerprint")))
                    .email(normalizeEmail(object.getStr("email")))
                    .userId(object.getLong("userId"))
                    .riskLevel(normalizeRiskLevel(object.getStr("riskLevel")))
                    .step(step)
                    .availableFactors(parseFactors(object.getJSONArray("availableFactors")))
                    .completedFactors(parseFactors(object.getJSONArray("completedFactors")))
                    .requiredFactorCount(Math.max(1, object.getInt("requiredFactorCount", 1)))
                    .requirePhoneBinding(Boolean.TRUE.equals(object.getBool("requirePhoneBinding")))
                    .completed(Boolean.TRUE.equals(object.getBool("completed")))
                    .build();
        } catch (Exception ignored) {
            return null;
        }
    }

    private JSONArray factorsToArray(Set<LoginFactor> factors) {
        JSONArray array = JSONUtil.createArray();
        for (LoginFactor factor : normalizeFactors(factors)) {
            array.add(factor.name());
        }
        return array;
    }

    private Set<LoginFactor> parseFactors(JSONArray array) {
        Set<LoginFactor> factors = new LinkedHashSet<>();
        if (array == null) {
            return factors;
        }
        for (Object item : array) {
            if (item == null) {
                continue;
            }
            try {
                factors.add(LoginFactor.valueOf(String.valueOf(item).trim().toUpperCase()));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return factors;
    }

    private Set<LoginFactor> normalizeFactors(Set<LoginFactor> factors) {
        if (factors == null || factors.isEmpty()) {
            return new LinkedHashSet<>();
        }
        return new LinkedHashSet<>(factors);
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            return "";
        }
        return email.trim().toLowerCase();
    }

    private String normalizeRiskLevel(String riskLevel) {
        if (riskLevel == null) {
            return "";
        }
        return riskLevel.trim().toUpperCase();
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
