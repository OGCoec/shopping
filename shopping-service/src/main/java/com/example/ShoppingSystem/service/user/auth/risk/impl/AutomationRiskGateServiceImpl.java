package com.example.ShoppingSystem.service.user.auth.risk.impl;

import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.mapper.IpReputationProfileMapper;
import com.example.ShoppingSystem.redisdata.BotDefenseRedisKeys;
import com.example.ShoppingSystem.service.user.auth.register.risk.impl.IpL6CountingBloomDecisionService;
import com.example.ShoppingSystem.service.user.auth.risk.AutomationRiskGateService;
import com.example.ShoppingSystem.service.user.auth.risk.DeviceRiskProfileWriteService;
import com.example.ShoppingSystem.service.user.auth.risk.IpRiskCacheInvalidator;
import com.example.ShoppingSystem.service.user.auth.risk.model.AutomationRiskDecision;
import com.example.ShoppingSystem.service.user.auth.risk.model.AutomationRiskScene;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

@Service
public class AutomationRiskGateServiceImpl implements AutomationRiskGateService {

    private static final Logger log = LoggerFactory.getLogger(AutomationRiskGateServiceImpl.class);
    private static final String FREQUENT_MESSAGE = "请求过于频繁，请稍后再试。";
    private static final String UNAVAILABLE_MESSAGE = "无法完成请求，请稍后再试。";
    private static final String DISABLED_SUFFIX = "disabled";

    private static final DefaultRedisScript<List> START_GATE_SCRIPT = new DefaultRedisScript<>();
    private static final DefaultRedisScript<List> UNKNOWN_LOGIN_GATE_SCRIPT = new DefaultRedisScript<>();

    static {
        START_GATE_SCRIPT.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/automation_start_gate.lua")));
        START_GATE_SCRIPT.setResultType(List.class);
        UNKNOWN_LOGIN_GATE_SCRIPT.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/automation_unknown_login_gate.lua")));
        UNKNOWN_LOGIN_GATE_SCRIPT.setResultType(List.class);
    }

    private final StringRedisTemplate stringRedisTemplate;
    private final DeviceRiskProfileWriteService deviceRiskProfileWriteService;
    private final IpReputationProfileMapper ipReputationProfileMapper;
    private final IpL6CountingBloomDecisionService ipL6CountingBloomDecisionService;
    private final ObjectProvider<IpRiskCacheInvalidator> ipRiskCacheInvalidatorProvider;

    @Value("${register.ip-risk-multi-level.redis-key-prefix:register:ip:risk:v2:}")
    private String ipRiskRedisKeyPrefix;

    public AutomationRiskGateServiceImpl(StringRedisTemplate stringRedisTemplate,
                                         DeviceRiskProfileWriteService deviceRiskProfileWriteService,
                                         IpReputationProfileMapper ipReputationProfileMapper,
                                         IpL6CountingBloomDecisionService ipL6CountingBloomDecisionService,
                                         ObjectProvider<IpRiskCacheInvalidator> ipRiskCacheInvalidatorProvider) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.deviceRiskProfileWriteService = deviceRiskProfileWriteService;
        this.ipReputationProfileMapper = ipReputationProfileMapper;
        this.ipL6CountingBloomDecisionService = ipL6CountingBloomDecisionService;
        this.ipRiskCacheInvalidatorProvider = ipRiskCacheInvalidatorProvider;
    }

    @Override
    public AutomationRiskDecision checkStart(AutomationRiskScene scene, String deviceFingerprint, String publicIp) {
        AutomationRiskDecision decision = executeStartGate(scene, deviceFingerprint, publicIp);
        applyPenalties(decision, deviceFingerprint, publicIp);
        return decision;
    }

    @Override
    public AutomationRiskDecision recordUnknownLoginIdentifier(String deviceFingerprint, String publicIp) {
        AutomationRiskDecision decision = executeUnknownLoginGate(deviceFingerprint, publicIp);
        applyPenalties(decision, deviceFingerprint, publicIp);
        return decision;
    }

    private AutomationRiskDecision executeStartGate(AutomationRiskScene scene, String deviceFingerprint, String publicIp) {
        AutomationRiskScene normalizedScene = scene == null ? AutomationRiskScene.LOGIN : scene;
        String normalizedDevice = normalizeText(deviceFingerprint);
        String normalizedIp = normalizeText(publicIp);
        boolean deviceEnabled = StrUtil.isNotBlank(normalizedDevice);
        boolean ipEnabled = StrUtil.isNotBlank(normalizedIp);
        if (!deviceEnabled && !ipEnabled) {
            return AutomationRiskDecision.allow();
        }

        try {
            List<String> keys = startKeys(normalizedScene, sha256OrDisabled(normalizedDevice), sha256OrDisabled(normalizedIp));
            List result = stringRedisTemplate.execute(
                    START_GATE_SCRIPT,
                    keys,
                    (Object[]) startArgs(normalizedScene, deviceEnabled, ipEnabled)
            );
            return toDecision(result, FREQUENT_MESSAGE);
        } catch (Exception e) {
            log.warn("Automation start gate failed, scene={}, reason={}", normalizedScene, e.getMessage());
            return AutomationRiskDecision.allow();
        }
    }

    private AutomationRiskDecision executeUnknownLoginGate(String deviceFingerprint, String publicIp) {
        String normalizedDevice = normalizeText(deviceFingerprint);
        String normalizedIp = normalizeText(publicIp);
        boolean deviceEnabled = StrUtil.isNotBlank(normalizedDevice);
        boolean ipEnabled = StrUtil.isNotBlank(normalizedIp);
        if (!deviceEnabled && !ipEnabled) {
            return AutomationRiskDecision.allow();
        }

        try {
            String deviceHash = sha256OrDisabled(normalizedDevice);
            String ipHash = sha256OrDisabled(normalizedIp);
            List<String> keys = Arrays.asList(
                    BotDefenseRedisKeys.LOGIN_BLOCK_DEVICE_PREFIX + deviceHash,
                    BotDefenseRedisKeys.LOGIN_BLOCK_IP_PREFIX + ipHash,
                    BotDefenseRedisKeys.LOGIN_RATE_DEVICE_UNKNOWN_30M_PREFIX + deviceHash,
                    BotDefenseRedisKeys.LOGIN_RATE_IP_UNKNOWN_30M_PREFIX + ipHash
            );
            List result = stringRedisTemplate.execute(
                    UNKNOWN_LOGIN_GATE_SCRIPT,
                    keys,
                    (Object[]) unknownLoginArgs(deviceEnabled, ipEnabled)
            );
            return toDecision(result, UNAVAILABLE_MESSAGE);
        } catch (Exception e) {
            log.warn("Unknown login automation gate failed, reason={}", e.getMessage());
            return AutomationRiskDecision.allow();
        }
    }

    private void applyPenalties(AutomationRiskDecision decision, String deviceFingerprint, String publicIp) {
        if (decision == null) {
            return;
        }
        if (decision.devicePenaltyScore() > 0) {
            applyDevicePenalty(deviceFingerprint, publicIp, decision.devicePenaltyScore(), decision.deviceReason());
        }
        if (decision.ipPenaltyScore() > 0) {
            applyIpPenalty(publicIp, decision.ipPenaltyScore(), decision.ipReason());
        }
    }

    private void applyDevicePenalty(String deviceFingerprint, String publicIp, int penaltyScore, String reason) {
        try {
            deviceRiskProfileWriteService.applyAutomationPenalty(deviceFingerprint, publicIp, penaltyScore, reason);
        } catch (Exception e) {
            log.warn("Automation device penalty failed, reason={}", e.getMessage());
        }
    }

    private void applyIpPenalty(String publicIp, int penaltyScore, String reason) {
        String normalizedIp = normalizeText(publicIp);
        if (StrUtil.isBlank(normalizedIp) || penaltyScore <= 0) {
            return;
        }
        try {
            OffsetDateTime now = OffsetDateTime.now();
            Integer updatedScore = normalizedIp.contains(":")
                    ? ipReputationProfileMapper.applyIpv6AutomationPenalty(normalizedIp, penaltyScore, now)
                    : ipReputationProfileMapper.applyIpv4AutomationPenalty(normalizedIp, penaltyScore, now);
            if (updatedScore == null) {
                return;
            }
            invalidateIpRiskCache(normalizedIp);
            ipL6CountingBloomDecisionService.syncMembershipByScore(normalizedIp, Math.max(0, updatedScore));
        } catch (Exception e) {
            log.warn("Automation IP penalty failed, ip={}, reason={}, error={}", normalizedIp, reason, e.getMessage());
        }
    }

    private void invalidateIpRiskCache(String ip) {
        IpRiskCacheInvalidator invalidator = ipRiskCacheInvalidatorProvider.getIfAvailable();
        if (invalidator != null) {
            invalidator.invalidateIp(ip);
            return;
        }
        try {
            stringRedisTemplate.delete(ipRiskRedisKeyPrefix + ip);
        } catch (Exception ignored) {
        }
    }

    private AutomationRiskDecision toDecision(List result, String blockedMessage) {
        if (result == null || result.isEmpty()) {
            return AutomationRiskDecision.allow();
        }
        boolean blocked = readLong(result, 0) > 0L;
        int devicePenalty = (int) readLong(result, 4);
        int ipPenalty = (int) readLong(result, 5);
        if (!blocked && devicePenalty <= 0 && ipPenalty <= 0) {
            return AutomationRiskDecision.allow();
        }
        return new AutomationRiskDecision(
                blocked,
                blocked ? blockedMessage : "ok",
                readString(result, 1),
                readString(result, 2),
                normalizePositive(readLong(result, 3)),
                devicePenalty,
                ipPenalty,
                normalizePositive(readLong(result, 6)),
                normalizePositive(readLong(result, 7))
        );
    }

    private List<String> startKeys(AutomationRiskScene scene, String deviceHash, String ipHash) {
        if (scene == AutomationRiskScene.REGISTER) {
            return Arrays.asList(
                    BotDefenseRedisKeys.REGISTER_BLOCK_DEVICE_PREFIX + deviceHash,
                    BotDefenseRedisKeys.REGISTER_BLOCK_IP_PREFIX + ipHash,
                    BotDefenseRedisKeys.REGISTER_RATE_DEVICE_START_1S_PREFIX + deviceHash,
                    BotDefenseRedisKeys.REGISTER_RATE_DEVICE_START_1M_PREFIX + deviceHash,
                    BotDefenseRedisKeys.REGISTER_RATE_DEVICE_START_30M_PREFIX + deviceHash,
                    BotDefenseRedisKeys.REGISTER_RATE_IP_START_1S_PREFIX + ipHash,
                    BotDefenseRedisKeys.REGISTER_RATE_IP_START_1M_PREFIX + ipHash,
                    BotDefenseRedisKeys.REGISTER_RATE_IP_START_30M_PREFIX + ipHash
            );
        }
        return Arrays.asList(
                BotDefenseRedisKeys.LOGIN_BLOCK_DEVICE_PREFIX + deviceHash,
                BotDefenseRedisKeys.LOGIN_BLOCK_IP_PREFIX + ipHash,
                BotDefenseRedisKeys.LOGIN_RATE_DEVICE_START_1S_PREFIX + deviceHash,
                BotDefenseRedisKeys.LOGIN_RATE_DEVICE_START_1M_PREFIX + deviceHash,
                BotDefenseRedisKeys.LOGIN_RATE_DEVICE_START_30M_PREFIX + deviceHash,
                BotDefenseRedisKeys.LOGIN_RATE_IP_START_1S_PREFIX + ipHash,
                BotDefenseRedisKeys.LOGIN_RATE_IP_START_1M_PREFIX + ipHash,
                BotDefenseRedisKeys.LOGIN_RATE_IP_START_30M_PREFIX + ipHash
        );
    }

    private String[] startArgs(AutomationRiskScene scene, boolean deviceEnabled, boolean ipEnabled) {
        if (scene == AutomationRiskScene.REGISTER) {
            return args(
                    deviceEnabled, ipEnabled,
                    3, 3600, 300, "REGISTER_BURST_1S",
                    10, 43200, 600, "REGISTER_BURST_1M",
                    30, 259200, 1000, "REGISTER_BURST_30M",
                    80, 2592000, 1600, "REGISTER_BURST_30M_SEVERE",
                    20, 900, 100, "REGISTER_IP_BURST_1S",
                    100, 7200, 300, "REGISTER_IP_BURST_1M",
                    500, 43200, 700, "REGISTER_IP_BURST_30M",
                    1500, 259200, 1200, "REGISTER_IP_BURST_30M_SEVERE"
            );
        }
        return args(
                deviceEnabled, ipEnabled,
                5, 1800, 200, "LOGIN_BURST_1S",
                30, 21600, 500, "LOGIN_BURST_1M",
                100, 259200, 900, "LOGIN_BURST_30M",
                300, 2592000, 1400, "LOGIN_BURST_30M_SEVERE",
                50, 600, 50, "LOGIN_IP_BURST_1S",
                300, 3600, 200, "LOGIN_IP_BURST_1M",
                1500, 28800, 500, "LOGIN_IP_BURST_30M",
                5000, 259200, 1000, "LOGIN_IP_BURST_30M_SEVERE"
        );
    }

    private String[] unknownLoginArgs(boolean deviceEnabled, boolean ipEnabled) {
        return args(
                deviceEnabled, ipEnabled,
                20, 43200, 500, "LOGIN_UNKNOWN_DEVICE_30M",
                50, 259200, 1000, "LOGIN_UNKNOWN_DEVICE_30M_HIGH",
                100, 2592000, 1600, "LOGIN_UNKNOWN_DEVICE_30M_SEVERE",
                300, 7200, 300, "LOGIN_UNKNOWN_IP_30M",
                1000, 43200, 700, "LOGIN_UNKNOWN_IP_30M_HIGH",
                3000, 259200, 1200, "LOGIN_UNKNOWN_IP_30M_SEVERE"
        );
    }

    private String[] args(boolean deviceEnabled, boolean ipEnabled, Object... values) {
        String[] args = new String[values.length + 2];
        args[0] = deviceEnabled ? "1" : "0";
        args[1] = ipEnabled ? "1" : "0";
        for (int index = 0; index < values.length; index += 1) {
            args[index + 2] = String.valueOf(values[index]);
        }
        return args;
    }

    private String sha256OrDisabled(String value) {
        if (StrUtil.isBlank(value)) {
            return DISABLED_SUFFIX;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest is unavailable.", e);
        }
    }

    private String normalizeText(String value) {
        return StrUtil.blankToDefault(value, "").trim();
    }

    private Long normalizePositive(long value) {
        return value > 0L ? value : null;
    }

    private long readLong(List result, int index) {
        if (result == null || index < 0 || index >= result.size()) {
            return 0L;
        }
        Object value = result.get(index);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null || StrUtil.isBlank(value.toString())) {
            return 0L;
        }
        try {
            return Long.parseLong(value.toString().trim());
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private String readString(List result, int index) {
        if (result == null || index < 0 || index >= result.size()) {
            return "";
        }
        Object value = result.get(index);
        return value == null ? "" : value.toString();
    }
}
