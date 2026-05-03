package com.example.ShoppingSystem.service.user.auth.login.impl;

import cn.hutool.core.util.IdUtil;
import com.example.ShoppingSystem.Utils.SnowflakeIdWorker;
import com.example.ShoppingSystem.entity.entity.UserLoginIdentity;
import com.example.ShoppingSystem.mapper.UserLoginIdentityMapper;
import com.example.ShoppingSystem.service.user.auth.login.MicrosoftAuthService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Microsoft 登录身份服务实现。
 */
@Service
public class MicrosoftAuthServiceImpl implements MicrosoftAuthService {

    private final UserLoginIdentityMapper userLoginIdentityMapper;
    private final SnowflakeIdWorker snowflakeIdWorker;

    public MicrosoftAuthServiceImpl(UserLoginIdentityMapper userLoginIdentityMapper,
                                    SnowflakeIdWorker snowflakeIdWorker) {
        this.userLoginIdentityMapper = userLoginIdentityMapper;
        this.snowflakeIdWorker = snowflakeIdWorker;
    }

    @Override
    @Transactional
    public UserLoginIdentity loginByMicrosoft(String microsoftId, String microsoftEmail) {
        UserLoginIdentity existingByMicrosoft = userLoginIdentityMapper.findByMicrosoftId(microsoftId);
        if (existingByMicrosoft != null) {
            userLoginIdentityMapper.updateLastLoginAtById(existingByMicrosoft.getId());
            return existingByMicrosoft;
        }

        String normalizedEmail = normalizeEmail(microsoftEmail);
        if (normalizedEmail == null) {
            return createMicrosoftIdentity(microsoftId, null);
        }

        UserLoginIdentity existingByEmail = userLoginIdentityMapper.findByEmail(normalizedEmail);
        if (existingByEmail != null) {
            userLoginIdentityMapper.bindMicrosoftIdById(existingByEmail.getId(), microsoftId);
            existingByEmail.setMicrosoftId(microsoftId);
            existingByEmail.setLastLoginAt(OffsetDateTime.now());
            return existingByEmail;
        }

        return createMicrosoftIdentity(microsoftId, normalizedEmail);
    }

    private UserLoginIdentity createMicrosoftIdentity(String microsoftId, String email) {
        UserLoginIdentity created = buildNewMicrosoftIdentity(microsoftId, email);
        userLoginIdentityMapper.insertMicrosoftIdentity(created);
        return created;
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase();
    }

    private UserLoginIdentity buildNewMicrosoftIdentity(String microsoftId, String email) {
        OffsetDateTime now = OffsetDateTime.now();
        Long identityId = snowflakeIdWorker.nextId();
        return UserLoginIdentity.builder()
                .id(identityId)
                .userId(identityId)
                .email(email)
                .emailPasswordHash(null)
                .emailVerified(Boolean.FALSE)
                .phone(null)
                .phoneVerified(Boolean.FALSE)
                .githubId(null)
                .googleId(null)
                .microsoftId(microsoftId)
                .tokenVersion(IdUtil.fastSimpleUUID().substring(0, 24))
                .totpEnabled(Boolean.FALSE)
                .status("ACTIVE")
                .lastLoginAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
