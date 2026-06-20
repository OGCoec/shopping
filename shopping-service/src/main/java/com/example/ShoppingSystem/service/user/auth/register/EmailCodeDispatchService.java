package com.example.ShoppingSystem.service.user.auth.register;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import com.example.ShoppingSystem.redisdata.RegisterRedisKeys;
import com.example.ShoppingSystem.service.user.auth.register.RegisterEmailCodeMessagePublisher;
import com.example.ShoppingSystem.service.user.auth.register.model.ChallengeSelection;
import com.example.ShoppingSystem.service.user.auth.register.model.RiskSnapshot;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public interface EmailCodeDispatchService {
    public void dispatchRegisterEmailCode(String email,
                                          String username,
                                          String rawPassword,
                                          String deviceFingerprint,
                                          String publicIp,
                                          RiskSnapshot riskSnapshot,
                                          ChallengeSelection challengeSelection);
}
