package com.example.ShoppingSystem.admin.service.auth;

import com.example.ShoppingSystem.admin.config.AdminSecurityProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;
import com.example.ShoppingSystem.admin.service.common.AdminServiceException;
import com.example.ShoppingSystem.admin.service.mail.AdminMailService;

public interface AdminEmailCodeService {
    public void sendFirstLoginEmailCode(String email);

    public void verifyAndClear(String email, String code);

    public long ttlSeconds();

    public long cooldownSeconds();
}
