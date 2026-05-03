package com.example.ShoppingSystem.service.user.auth.register.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.example.ShoppingSystem.service.user.auth.register.RegisterFlowProperties;
import com.example.ShoppingSystem.service.user.auth.register.RegisterFlowSessionService;
import com.example.ShoppingSystem.service.user.auth.register.model.RegisterFlowSession;
import com.example.ShoppingSystem.service.user.auth.register.model.RegisterFlowStep;
import com.example.ShoppingSystem.service.user.auth.register.model.RegisterFlowValidationError;
import com.example.ShoppingSystem.service.user.auth.register.model.RegisterFlowValidationResult;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Redis-backed register flow session service.
 */
@Service
public class RegisterFlowSessionServiceImpl implements RegisterFlowSessionService {

    private final StringRedisTemplate stringRedisTemplate;
    private final RegisterFlowProperties properties;

    public RegisterFlowSessionServiceImpl(StringRedisTemplate stringRedisTemplate,
                                          RegisterFlowProperties properties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.properties = properties;
    }

    @Override
    public RegisterFlowSession startFlow(String email,
                                         String deviceFingerprint,
                                         String preAuthToken) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedDeviceFingerprint = normalizeText(deviceFingerprint);
        String normalizedPreAuthToken = normalizeText(preAuthToken);
        if (StrUtil.hasBlank(normalizedEmail, normalizedDeviceFingerprint, normalizedPreAuthToken)) {
            throw new IllegalArgumentException("Register flow start requires email, device fingerprint, and preauth token.");
        }

        RegisterFlowSession session = RegisterFlowSession.builder()
                .flowId(IdUtil.nanoId(48))
                .preAuthToken(normalizedPreAuthToken)
                .deviceFingerprint(normalizedDeviceFingerprint)
                .email(normalizedEmail)
                .step(RegisterFlowStep.PASSWORD)
                .riskLevel("")
                .requirePhoneBinding(false)
                .completed(false)
                .build();
        save(session);
        return session;
    }

    @Override
    public RegisterFlowValidationResult validate(String flowId, String preAuthToken) {
        return validateInternal(flowId, preAuthToken, null, false);
    }

    @Override
    public RegisterFlowValidationResult validate(String flowId,
                                                 String preAuthToken,
                                                 String deviceFingerprint) {
        return validateInternal(flowId, preAuthToken, deviceFingerprint, true);
    }

    @Override
    public RegisterFlowSession readFlow(String flowId) {
        String normalizedFlowId = normalizeText(flowId);
        if (normalizedFlowId == null) {
            return null;
        }
        return deserialize(stringRedisTemplate.opsForValue().get(flowKey(normalizedFlowId)));
    }

    @Override
    public RegisterFlowSession updateStep(String flowId,
                                          RegisterFlowStep step,
                                          String riskLevel,
                                          boolean requirePhoneBinding,
                                          boolean completed) {
        RegisterFlowSession existing = readFlow(flowId);
        if (existing == null || step == null) {
            return null;
        }
        RegisterFlowSession updated = RegisterFlowSession.builder()
                .flowId(existing.getFlowId())
                .preAuthToken(existing.getPreAuthToken())
                .deviceFingerprint(existing.getDeviceFingerprint())
                .email(existing.getEmail())
                .step(step)
                .riskLevel(normalizeRiskLevel(riskLevel))
                .requirePhoneBinding(requirePhoneBinding)
                .completed(completed)
                .build();
        save(updated);
        return updated;
    }

    private RegisterFlowValidationResult validateInternal(String flowId,
                                                          String preAuthToken,
                                                          String deviceFingerprint,
                                                          boolean requireDeviceFingerprint) {
        String normalizedFlowId = normalizeText(flowId);
        if (normalizedFlowId == null) {
            return RegisterFlowValidationResult.invalid(RegisterFlowValidationError.MISSING_FLOW_ID);
        }

        RegisterFlowSession existing = readFlow(normalizedFlowId);
        if (existing == null) {
            return RegisterFlowValidationResult.invalid(RegisterFlowValidationError.EXPIRED);
        }

        String normalizedPreAuthToken = normalizeText(preAuthToken);
        if (!StrUtil.equals(existing.getPreAuthToken(), normalizedPreAuthToken)) {
            return RegisterFlowValidationResult.invalid(RegisterFlowValidationError.PREAUTH_MISMATCH);
        }

        if (requireDeviceFingerprint) {
            String normalizedDeviceFingerprint = normalizeText(deviceFingerprint);
            if (!StrUtil.equals(existing.getDeviceFingerprint(), normalizedDeviceFingerprint)) {
                return RegisterFlowValidationResult.invalid(RegisterFlowValidationError.DEVICE_MISMATCH);
            }
        }

        RegisterFlowSession refreshed = RegisterFlowSession.builder()
                .flowId(existing.getFlowId())
                .preAuthToken(existing.getPreAuthToken())
                .deviceFingerprint(existing.getDeviceFingerprint())
                .email(existing.getEmail())
                .step(existing.getStep())
                .riskLevel(normalizeRiskLevel(existing.getRiskLevel()))
                .requirePhoneBinding(existing.isRequirePhoneBinding())
                .completed(existing.isCompleted())
                .build();
        save(refreshed);
        return RegisterFlowValidationResult.valid(refreshed);
    }

    private void save(RegisterFlowSession session) {
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

    private String serialize(RegisterFlowSession session) {
        JSONObject json = JSONUtil.createObj();
        json.set("flowId", session.getFlowId());
        json.set("preAuthToken", session.getPreAuthToken());
        json.set("deviceFingerprint", session.getDeviceFingerprint());
        json.set("email", session.getEmail());
        json.set("step", session.getStep() == null ? "" : session.getStep().name());
        json.set("riskLevel", normalizeRiskLevel(session.getRiskLevel()));
        json.set("requirePhoneBinding", session.isRequirePhoneBinding());
        json.set("completed", session.isCompleted());
        return json.toString();
    }

    private RegisterFlowSession deserialize(String json) {
        if (StrUtil.isBlank(json)) {
            return null;
        }
        try {
            JSONObject object = JSONUtil.parseObj(json);
            RegisterFlowStep step = RegisterFlowStep.fromStorage(object.getStr("step"));
            if (step == null) {
                return null;
            }
            return RegisterFlowSession.builder()
                    .flowId(normalizeText(object.getStr("flowId")))
                    .preAuthToken(normalizeText(object.getStr("preAuthToken")))
                    .deviceFingerprint(normalizeText(object.getStr("deviceFingerprint")))
                    .email(normalizeEmail(object.getStr("email")))
                    .step(step)
                    .riskLevel(normalizeRiskLevel(object.getStr("riskLevel")))
                    .requirePhoneBinding(Boolean.TRUE.equals(object.getBool("requirePhoneBinding")))
                    .completed(Boolean.TRUE.equals(object.getBool("completed")))
                    .build();
        } catch (Exception ignored) {
            return null;
        }
    }

    private long ttlMillis() {
        return TimeUnit.MINUTES.toMillis(Math.max(1, properties.getTtlMinutes()));
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
