package com.example.ShoppingSystem.quota;

import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.filter.preauth.support.PreAuthHashingService;
import com.example.ShoppingSystem.mapper.risk.RegisterRiskProfileMapper;
import com.example.ShoppingSystem.service.user.auth.risk.DeviceL6CountingBloomDecisionService;
import com.example.ShoppingSystem.service.user.auth.risk.DeviceRiskProfileWriteService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public interface DeviceRiskMultiLevelQueryService {
    public int resolveDeviceScore(String deviceFingerprint, String clientIp);
}
