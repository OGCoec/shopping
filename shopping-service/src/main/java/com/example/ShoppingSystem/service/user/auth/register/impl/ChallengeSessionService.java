package com.example.ShoppingSystem.service.user.auth.register.impl;

import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.redisdata.RegisterRedisKeys;
import com.example.ShoppingSystem.service.user.auth.register.model.ChallengeSelection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.CHALLENGE_OPERATION_TIMEOUT;

/**
 * 注册 challenge 会话服务（纯 Redis 操作）。
 * <p>
 * 该类只做以下事情：
 * 1) pending challenge 的读取；
 * 2) pending challenge 的写入与续期；
 * 3) pending challenge 的清理；
 * 4) challenge key 的统一生成。
 * <p>
 * 该类不做风险策略、不做验证码校验、不做业务异常决策。
 */
@Service
public class ChallengeSessionService {

    /**
     * Redis value 存储格式：type|subType。
     * 示例：TIANAI_CAPTCHA|ROTATE
     * 若无子类型，则形如：HUTOOL_SHEAR_CAPTCHA|
     */
    private static final String CHALLENGE_VALUE_SEPARATOR = "|";

    private final StringRedisTemplate stringRedisTemplate;

    @Value("${register.operation-timeout.wait-min-seconds:5}")
    private int operationTimeoutWaitMinSeconds;

    @Value("${register.operation-timeout.wait-max-seconds:10}")
    private int operationTimeoutWaitMaxSeconds;

    public ChallengeSessionService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 读取 pending challenge。
     *
     * @param email 注册邮箱
     * @param deviceFingerprint 设备指纹
     * @return 解析后的 ChallengeSelection；若不存在或入参非法返回 null
     */
    public ChallengeSelection readPendingChallengeSelection(String email, String deviceFingerprint) {
        if (StrUtil.hasBlank(email, deviceFingerprint)) {
            return null;
        }
        String value = stringRedisTemplate.opsForValue().get(pendingChallengeKey(email, deviceFingerprint));
        return parseChallengeSelectionValue(value);
    }

    /**
     * 当前请求挑战类型统一优先使用会话中的 pending 值。
     * 若会话为空，再回退到本次风控计算结果。
     */
    public ChallengeSelection resolveChallengeSelectionForCurrentAttempt(ChallengeSelection pendingChallengeSelection,
                                                                         ChallengeSelection riskBasedChallengeSelection) {
        if (pendingChallengeSelection != null) {
            return pendingChallengeSelection;
        }
        return riskBasedChallengeSelection == null ? ChallengeSelection.none() : riskBasedChallengeSelection;
    }

    /**
     * 保存 pending challenge 到 Redis，并设置 TTL。
     *
     * @param email 注册邮箱
     * @param deviceFingerprint 设备指纹
     * @param challengeSelection challenge 选择结果
     * @return 原始入参 challengeSelection（便于调用方链式返回）
     */
    public ChallengeSelection savePendingChallengeSelection(String email,
                                                            String deviceFingerprint,
                                                            ChallengeSelection challengeSelection) {
        if (StrUtil.hasBlank(email, deviceFingerprint)
                || challengeSelection == null
                || StrUtil.isBlank(challengeSelection.type())) {
            return challengeSelection;
        }
        String pendingKey = pendingChallengeKey(email, deviceFingerprint);
        String value = challengeSelection.type() + CHALLENGE_VALUE_SEPARATOR + StrUtil.nullToEmpty(challengeSelection.subType());
        stringRedisTemplate.opsForValue().set(
                pendingKey,
                value,
                RegisterRedisKeys.CHALLENGE_TTL_MINUTES,
                TimeUnit.MINUTES);
        if (isOperationTimeoutChallenge(challengeSelection.type())) {
            ensureOperationTimeoutWaitUntil(email, deviceFingerprint);
        } else {
            clearOperationTimeoutWaitUntil(email, deviceFingerprint);
        }
        return challengeSelection;
    }

    /**
     * 清理 pending challenge。
     */
    public void clearPendingChallengeSelection(String email, String deviceFingerprint) {
        if (StrUtil.hasBlank(email, deviceFingerprint)) {
            return;
        }
        String pendingKey = pendingChallengeKey(email, deviceFingerprint);
        stringRedisTemplate.delete(pendingKey);
        stringRedisTemplate.delete(operationTimeoutWaitUntilKey(pendingKey));
    }

    /**
     * 刷新 pending challenge TTL。
     * <p>
     * 规则：
     * 1) 入参非法 -> false；
     * 2) Redis 无值 -> 以 expected 重建并返回 true；
     * 3) Redis 有值但与 expected 不一致 -> false；
     * 4) Redis 有值且一致 -> 续期并返回是否成功。
     */
    public boolean refreshPendingChallengeSelection(String email,
                                                    String deviceFingerprint,
                                                    ChallengeSelection expectedChallengeSelection) {
        if (StrUtil.hasBlank(email, deviceFingerprint)
                || expectedChallengeSelection == null
                || StrUtil.isBlank(expectedChallengeSelection.type())) {
            return false;
        }

        String pendingKey = pendingChallengeKey(email, deviceFingerprint);
        ChallengeSelection currentSelection = parseChallengeSelectionValue(
                stringRedisTemplate.opsForValue().get(pendingKey));

        if (currentSelection == null) {
            savePendingChallengeSelection(email, deviceFingerprint, expectedChallengeSelection);
            return true;
        }

        if (!isSameChallengeSelection(currentSelection, expectedChallengeSelection)) {
            return false;
        }

        Boolean pendingRefreshed = stringRedisTemplate.expire(
                pendingKey,
                RegisterRedisKeys.CHALLENGE_TTL_MINUTES,
                TimeUnit.MINUTES);
        if (isOperationTimeoutChallenge(expectedChallengeSelection.type())) {
            refreshOperationTimeoutWaitUntilTtl(email, deviceFingerprint);
        } else {
            clearOperationTimeoutWaitUntil(email, deviceFingerprint);
        }
        return !Boolean.FALSE.equals(pendingRefreshed);
    }

    /**
     * Ensure OPERATION_TIMEOUT wait window exists and return wait-until epoch millis.
     * Existing valid window is reused to keep retry behavior stable.
     */
    public long ensureOperationTimeoutWaitUntil(String email, String deviceFingerprint) {
        if (StrUtil.hasBlank(email, deviceFingerprint)) {
            return System.currentTimeMillis();
        }
        String waitKey = operationTimeoutWaitUntilKey(pendingChallengeKey(email, deviceFingerprint));
        long now = System.currentTimeMillis();
        Long currentWaitUntil = parseLongValue(stringRedisTemplate.opsForValue().get(waitKey));
        if (currentWaitUntil != null && currentWaitUntil > now) {
            stringRedisTemplate.expire(waitKey, RegisterRedisKeys.CHALLENGE_TTL_MINUTES, TimeUnit.MINUTES);
            return currentWaitUntil;
        }

        long nextWaitUntil = now + randomOperationTimeoutWaitMillis();
        stringRedisTemplate.opsForValue().set(
                waitKey,
                String.valueOf(nextWaitUntil),
                RegisterRedisKeys.CHALLENGE_TTL_MINUTES,
                TimeUnit.MINUTES
        );
        return nextWaitUntil;
    }

    /**
     * Read OPERATION_TIMEOUT wait-until epoch millis.
     *
     * @return null when no active wait window is stored.
     */
    public Long readOperationTimeoutWaitUntil(String email, String deviceFingerprint) {
        if (StrUtil.hasBlank(email, deviceFingerprint)) {
            return null;
        }
        String waitKey = operationTimeoutWaitUntilKey(pendingChallengeKey(email, deviceFingerprint));
        return parseLongValue(stringRedisTemplate.opsForValue().get(waitKey));
    }

    /**
     * Remaining wait millis for OPERATION_TIMEOUT.
     */
    public long getOperationTimeoutRemainingMillis(String email, String deviceFingerprint) {
        Long waitUntil = readOperationTimeoutWaitUntil(email, deviceFingerprint);
        if (waitUntil == null) {
            return 0L;
        }
        return Math.max(0L, waitUntil - System.currentTimeMillis());
    }

    /**
     * 比较两个 challenge 选择是否一致（type + subType 全量一致）。
     */
    private boolean isSameChallengeSelection(ChallengeSelection left, ChallengeSelection right) {
        boolean typeMatches = StrUtil.equals(left.type(), right.type());
        boolean subTypeMatches = StrUtil.equals(
                StrUtil.nullToEmpty(left.subType()),
                StrUtil.nullToEmpty(right.subType()));
        return typeMatches && subTypeMatches;
    }

    private boolean isOperationTimeoutChallenge(String challengeType) {
        return CHALLENGE_OPERATION_TIMEOUT.equalsIgnoreCase(StrUtil.blankToDefault(challengeType, ""));
    }

    private void refreshOperationTimeoutWaitUntilTtl(String email, String deviceFingerprint) {
        String waitKey = operationTimeoutWaitUntilKey(pendingChallengeKey(email, deviceFingerprint));
        Boolean waitKeyExists = stringRedisTemplate.hasKey(waitKey);
        if (Boolean.TRUE.equals(waitKeyExists)) {
            stringRedisTemplate.expire(waitKey, RegisterRedisKeys.CHALLENGE_TTL_MINUTES, TimeUnit.MINUTES);
            return;
        }
        ensureOperationTimeoutWaitUntil(email, deviceFingerprint);
    }

    private void clearOperationTimeoutWaitUntil(String email, String deviceFingerprint) {
        String waitKey = operationTimeoutWaitUntilKey(pendingChallengeKey(email, deviceFingerprint));
        stringRedisTemplate.delete(waitKey);
    }

    private String operationTimeoutWaitUntilKey(String pendingKey) {
        return pendingKey + RegisterRedisKeys.CHALLENGE_OPERATION_TIMEOUT_WAIT_UNTIL_SUFFIX;
    }

    private long randomOperationTimeoutWaitMillis() {
        int minSeconds = Math.max(1, operationTimeoutWaitMinSeconds);
        int maxSeconds = Math.max(minSeconds, operationTimeoutWaitMaxSeconds);
        int randomSeconds = ThreadLocalRandom.current().nextInt(minSeconds, maxSeconds + 1);
        return TimeUnit.SECONDS.toMillis(randomSeconds);
    }

    private Long parseLongValue(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * 生成 pending challenge 对应的 Redis key。
     * <p>
     * key = auth:register:challenge: + sha256(email|deviceFingerprint)
     */
    private String pendingChallengeKey(String email, String deviceFingerprint) {
        return RegisterRedisKeys.CHALLENGE_PREFIX + sha256(email + "|" + deviceFingerprint);
    }

    /**
     * 反序列化 Redis value -> ChallengeSelection。
     */
    private ChallengeSelection parseChallengeSelectionValue(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        String[] parts = value.split("\\|", 2);
        String type = StrUtil.trimToNull(parts[0]);
        if (type == null) {
            return null;
        }
        String subType = parts.length > 1 && StrUtil.isNotBlank(parts[1]) ? parts[1] : null;
        return new ChallengeSelection(type, subType);
    }

    /**
     * 计算字符串的 SHA-256 十六进制摘要。
     */
    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest is unavailable", e);
        }
    }
}
