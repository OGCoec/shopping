package com.example.ShoppingSystem.service.captcha.hutool.impl;

import cn.hutool.captcha.ShearCaptcha;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.redisdata.RegisterRedisKeys;
import com.example.ShoppingSystem.service.captcha.hutool.HutoolCaptchaService;
import com.example.ShoppingSystem.service.captcha.hutool.model.HutoolCaptchaResult;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Hutool 图形验证码服务实现。
 * 使用增强失真的 ShearCaptcha，验证码为 5 位数字和字母，校验时不区分大小写。
 */
@Service
public class HutoolCaptchaServiceImpl implements HutoolCaptchaService {

    private final StringRedisTemplate stringRedisTemplate;

    public HutoolCaptchaServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public HutoolCaptchaResult generateCaptcha(String type, String existingUuid) throws IOException {
        // 适度降低干扰线数量，并通过 alpha 参数让整体干扰线观感更轻。
        ShearCaptcha captcha = new ShearCaptcha(170, 56, 5, 1, 0.35f);
        String captchaText = captcha.getCode().toLowerCase();

        String targetUuid = existingUuid;
        String redisKey = null;
        if (StrUtil.isNotBlank(targetUuid)) {
            redisKey = RegisterRedisKeys.CAPTCHA_CODE_PREFIX + type + ":" + targetUuid;
            if (Boolean.FALSE.equals(stringRedisTemplate.hasKey(redisKey))) {
                targetUuid = null;
            }
        }

        if (StrUtil.isBlank(targetUuid)) {
            targetUuid = IdUtil.nanoId(48);
            redisKey = RegisterRedisKeys.CAPTCHA_CODE_PREFIX + type + ":" + targetUuid;
        }

        stringRedisTemplate.opsForValue().set(
                redisKey,
                captchaText,
                RegisterRedisKeys.CAPTCHA_CODE_TTL_MINUTES,
                TimeUnit.MINUTES
        );

        return HutoolCaptchaResult.builder()
                .uuid(targetUuid)
                .image(captcha.getImageBase64Data())
                .build();
    }

    @Override
    public boolean validateCaptcha(String type, String captchaUuid, String captchaCode) {
        if (StrUtil.hasBlank(type, captchaUuid, captchaCode)) {
            return false;
        }
        String redisKey = RegisterRedisKeys.CAPTCHA_CODE_PREFIX + type + ":" + captchaUuid;
        String cachedCode = stringRedisTemplate.opsForValue().get(redisKey);
        if (StrUtil.isBlank(cachedCode)) {
            return false;
        }
        boolean matched = cachedCode.equalsIgnoreCase(captchaCode.trim());
        if (matched) {
            stringRedisTemplate.delete(redisKey);
        }
        return matched;
    }
}
