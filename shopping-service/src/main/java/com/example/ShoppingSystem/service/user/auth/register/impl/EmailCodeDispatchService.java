package com.example.ShoppingSystem.service.user.auth.register.impl;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import com.example.ShoppingSystem.redisdata.RegisterRedisKeys;
import com.example.ShoppingSystem.service.user.auth.register.RegisterEmailCodeMessagePublisher;
import com.example.ShoppingSystem.service.user.auth.register.model.ChallengeSelection;
import com.example.ShoppingSystem.service.user.auth.register.model.RiskSnapshot;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 邮箱验证码派发服务。
 * <p>
 * 统一封装以下动作：
 * 1) 生成 6 位邮箱验证码；
 * 2) 写入 Redis 主键（email-code）；
 * 3) 写入 Redis 元数据（email-code:meta）；
 * 4) 投递 MQ 消息触发异步发信。
 */
@Service
public class EmailCodeDispatchService {

    private final StringRedisTemplate stringRedisTemplate;
    private final RegisterEmailCodeMessagePublisher registerEmailCodeMessagePublisher;
    private final PasswordEncoder passwordEncoder;

    public EmailCodeDispatchService(StringRedisTemplate stringRedisTemplate,
                                    RegisterEmailCodeMessagePublisher registerEmailCodeMessagePublisher,
                                    PasswordEncoder passwordEncoder) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.registerEmailCodeMessagePublisher = registerEmailCodeMessagePublisher;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 生成并派发注册邮箱验证码。
     * <p>
     * Redis 元数据字段说明：
     * - email/username/passwordHash/deviceFingerprint/publicIp：用于后续完成注册时复核；
     * - ipScore/deviceScore/totalScore/riskLevel：保留本次挑战时的风控快照；
     * - challengeType/challengeSubType：保留本次命中的挑战类型。
     */
    public void dispatchRegisterEmailCode(String email,
                                          String username,
                                          String rawPassword,
                                          String deviceFingerprint,
                                          String publicIp,
                                          RiskSnapshot riskSnapshot,
                                          ChallengeSelection challengeSelection) {
        String emailCode = RandomUtil.randomNumbers(6);
        String codeKey = RegisterRedisKeys.EMAIL_CODE_PREFIX + email;
        String metaKey = RegisterRedisKeys.EMAIL_CODE_META_PREFIX + email;

        // 1) 保存邮箱验证码正文
        stringRedisTemplate.opsForValue().set(
                codeKey,
                emailCode,
                RegisterRedisKeys.EMAIL_CODE_TTL_MINUTES,
                TimeUnit.MINUTES);

        String challengeType = challengeSelection == null ? null : challengeSelection.type();
        String challengeSubType = challengeSelection == null ? null : challengeSelection.subType();

        // 2) 保存注册元数据，供“验证码确认注册”阶段复用
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("email", email);
        meta.put("username", username);
        meta.put("passwordHash", passwordEncoder.encode(rawPassword));
        meta.put("deviceFingerprint", deviceFingerprint);
        meta.put("publicIp", publicIp);
        meta.put("ipScore", riskSnapshot.ipScore());
        meta.put("deviceScore", riskSnapshot.deviceScore());
        meta.put("totalScore", riskSnapshot.totalScore());
        meta.put("riskLevel", riskSnapshot.riskLevel());
        meta.put("challengeType", challengeType);
        meta.put("challengeSubType", challengeSubType);

        stringRedisTemplate.opsForValue().set(
                metaKey,
                JSONUtil.toJsonStr(meta),
                RegisterRedisKeys.EMAIL_CODE_TTL_MINUTES,
                TimeUnit.MINUTES);

        // 3) 异步投递发信消息，接口线程不等待邮箱网关返回
        registerEmailCodeMessagePublisher.publishRegisterEmailCode(
                email,
                emailCode,
                RegisterRedisKeys.EMAIL_CODE_TTL_MINUTES);
    }
}
