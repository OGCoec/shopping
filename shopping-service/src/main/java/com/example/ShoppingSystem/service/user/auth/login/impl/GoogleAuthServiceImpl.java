package com.example.ShoppingSystem.service.user.auth.login.impl;

import cn.hutool.core.util.IdUtil;
import com.example.ShoppingSystem.Utils.SnowflakeIdWorker;
import com.example.ShoppingSystem.entity.entity.UserLoginIdentity;
import com.example.ShoppingSystem.mapper.UserLoginIdentityMapper;
import com.example.ShoppingSystem.service.user.auth.login.GoogleAuthService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Google 登录身份服务实现。
 */
@Service
public class GoogleAuthServiceImpl implements GoogleAuthService {

    private final UserLoginIdentityMapper userLoginIdentityMapper;
    private final SnowflakeIdWorker snowflakeIdWorker;

    public GoogleAuthServiceImpl(UserLoginIdentityMapper userLoginIdentityMapper,
                                 SnowflakeIdWorker snowflakeIdWorker) {
        this.userLoginIdentityMapper = userLoginIdentityMapper;
        this.snowflakeIdWorker = snowflakeIdWorker;
    }

    @Override
    @Transactional
    public UserLoginIdentity loginByGoogle(String googleId, String googleEmail) {
        UserLoginIdentity existingByGoogle = userLoginIdentityMapper.findByGoogleId(googleId);
        if (existingByGoogle != null) {
            userLoginIdentityMapper.updateLastLoginAtById(existingByGoogle.getId());
            return existingByGoogle;
        }

        if (googleEmail != null && !googleEmail.isBlank()) {
            String normalizedEmail = googleEmail.trim().toLowerCase();
            UserLoginIdentity existingByEmail = userLoginIdentityMapper.findByEmail(normalizedEmail);
            if (existingByEmail != null) {
                userLoginIdentityMapper.bindGoogleIdById(existingByEmail.getId(), googleId);
                existingByEmail.setGoogleId(googleId);
                existingByEmail.setLastLoginAt(OffsetDateTime.now());
                return existingByEmail;
            }

            UserLoginIdentity created = buildNewGoogleIdentity(googleId, normalizedEmail);
            userLoginIdentityMapper.insertGoogleIdentity(created);
            return created;
        }

        UserLoginIdentity created = buildNewGoogleIdentity(googleId, null);
        userLoginIdentityMapper.insertGoogleIdentity(created);
        return created;
    }

    private UserLoginIdentity buildNewGoogleIdentity(String googleId, String email) {
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
                .googleId(googleId)
                .microsoftId(null)
                .tokenVersion(IdUtil.fastSimpleUUID().substring(0, 24))
                .status("ACTIVE")
                .lastLoginAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
