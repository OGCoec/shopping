package com.example.ShoppingSystem.service.user.auth.login.impl;

import cn.hutool.core.util.IdUtil;
import com.example.ShoppingSystem.Utils.SnowflakeIdWorker;
import com.example.ShoppingSystem.entity.entity.UserLoginIdentity;
import com.example.ShoppingSystem.mapper.UserLoginIdentityMapper;
import com.example.ShoppingSystem.service.user.auth.login.GithubAuthService;
import com.example.ShoppingSystem.service.user.auth.login.UserAuthAccountUnavailableException;
import com.example.ShoppingSystem.service.user.auth.risk.TerminatedAccountEmailBloomService;
import com.example.ShoppingSystem.service.user.auth.risk.UserAuthFailureRiskService;
import com.example.ShoppingSystem.service.user.auth.risk.model.UserAuthLockStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * GitHub 登录身份服务实现。
 */
@Service
public class GithubAuthServiceImpl implements GithubAuthService {

    private final UserLoginIdentityMapper userLoginIdentityMapper;
    private final UserAuthFailureRiskService userAuthFailureRiskService;
    private final TerminatedAccountEmailBloomService terminatedAccountEmailBloomService;
    private final SnowflakeIdWorker snowflakeIdWorker;

    public GithubAuthServiceImpl(UserLoginIdentityMapper userLoginIdentityMapper,
                                 UserAuthFailureRiskService userAuthFailureRiskService,
                                 TerminatedAccountEmailBloomService terminatedAccountEmailBloomService,
                                 SnowflakeIdWorker snowflakeIdWorker) {
        this.userLoginIdentityMapper = userLoginIdentityMapper;
        this.userAuthFailureRiskService = userAuthFailureRiskService;
        this.terminatedAccountEmailBloomService = terminatedAccountEmailBloomService;
        this.snowflakeIdWorker = snowflakeIdWorker;
    }

    @Override
    @Transactional
    public UserLoginIdentity loginByGithub(String githubId, String githubEmail) {
        UserLoginIdentity existingByGithub = userLoginIdentityMapper.findByGithubId(githubId);
        if (existingByGithub != null) {
            ensureLoginAllowed(existingByGithub);
            userLoginIdentityMapper.updateLastLoginAtById(existingByGithub.getId());
            return existingByGithub;
        }

        if (githubEmail != null && !githubEmail.isBlank()) {
            String normalizedEmail = githubEmail.trim().toLowerCase();
            ensureEmailNotTerminated(normalizedEmail);
            UserLoginIdentity existingByEmail = userLoginIdentityMapper.findByEmail(normalizedEmail);
            if (existingByEmail != null) {
                ensureLoginAllowed(existingByEmail);
                userLoginIdentityMapper.bindGithubIdById(existingByEmail.getId(), githubId);
                existingByEmail.setGithubId(githubId);
                existingByEmail.setLastLoginAt(OffsetDateTime.now());
                return existingByEmail;
            }

            UserLoginIdentity created = buildNewGithubIdentity(githubId, normalizedEmail);
            userLoginIdentityMapper.insertGithubIdentity(created);
            return created;
        }

        UserLoginIdentity created = buildNewGithubIdentity(githubId, null);
        userLoginIdentityMapper.insertGithubIdentity(created);
        return created;
    }

    private UserLoginIdentity buildNewGithubIdentity(String githubId, String email) {
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
                .githubId(githubId)
                .googleId(null)
                .microsoftId(null)
                .tokenVersion(IdUtil.fastSimpleUUID().substring(0, 24))
                .totpEnabled(Boolean.FALSE)
                .status("ACTIVE")
                .lastLoginAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private void ensureLoginAllowed(UserLoginIdentity identity) {
        ensureEmailNotTerminated(identity == null ? null : identity.getEmail());
        UserAuthLockStatus lockStatus = userAuthFailureRiskService.checkAccountStatusAndLock(
                identity.getUserId(),
                identity.getStatus()
        );
        if (lockStatus.isBlocked()) {
            throw new UserAuthAccountUnavailableException(lockStatus.getStatus());
        }
        if ("LOCKED".equalsIgnoreCase(String.valueOf(identity.getStatus()))) {
            identity.setStatus("ACTIVE");
        }
    }

    private void ensureEmailNotTerminated(String email) {
        if (terminatedAccountEmailBloomService.isTerminatedEmail(email)) {
            throw new UserAuthAccountUnavailableException("RISK_TERMINATED");
        }
    }
}
