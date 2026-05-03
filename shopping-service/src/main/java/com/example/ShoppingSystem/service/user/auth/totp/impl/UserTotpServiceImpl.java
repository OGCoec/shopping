package com.example.ShoppingSystem.service.user.auth.totp.impl;

import com.example.ShoppingSystem.Utils.totp.OtpAuthUriUtils;
import com.example.ShoppingSystem.Utils.totp.TotpCodeGenerator;
import com.example.ShoppingSystem.Utils.totp.TotpCodeVerifier;
import com.example.ShoppingSystem.Utils.totp.TotpSecretGenerator;
import com.example.ShoppingSystem.entity.entity.UserLoginIdentity;
import com.example.ShoppingSystem.mapper.UserLoginIdentityMapper;
import com.example.ShoppingSystem.service.user.auth.totp.UserTotpService;
import com.example.ShoppingSystem.service.user.auth.totp.model.TotpSetupStartResult;
import com.example.ShoppingSystem.service.user.auth.totp.model.TotpVerificationResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.OptionalLong;

@Service
public class UserTotpServiceImpl implements UserTotpService {

    private static final String ISSUER = "Shopping";

    private final UserLoginIdentityMapper userLoginIdentityMapper;

    public UserTotpServiceImpl(UserLoginIdentityMapper userLoginIdentityMapper) {
        this.userLoginIdentityMapper = userLoginIdentityMapper;
    }

    @Override
    @Transactional
    public TotpSetupStartResult startSetup(Long userId) {
        UserLoginIdentity identity = requireIdentity(userId);
        String secret = TotpSecretGenerator.generateBase32Secret(TotpSecretGenerator.DEFAULT_SECRET_BITS);
        userLoginIdentityMapper.savePendingTotpSecret(identity.getId(), encodeSecretForStorage(secret));

        String accountName = resolveAccountName(identity);
        String otpauthUri = OtpAuthUriUtils.buildTotpUri(ISSUER, accountName, secret);
        return TotpSetupStartResult.builder()
                .success(true)
                .message("ok")
                .secret(secret)
                .otpauthUri(otpauthUri)
                .secretBits(TotpSecretGenerator.DEFAULT_SECRET_BITS)
                .digits(TotpCodeGenerator.DEFAULT_DIGITS)
                .periodSeconds(TotpCodeGenerator.DEFAULT_TIME_STEP_SECONDS)
                .build();
    }

    @Override
    @Transactional
    public TotpVerificationResult confirmSetup(Long userId, String code) {
        UserLoginIdentity identity = requireIdentity(userId);
        String secret = decodeSecretFromStorage(identity.getTotpSecretEncrypted());
        if (secret == null || secret.isBlank()) {
            return fail("TOTP setup has not been started.");
        }

        OptionalLong matchedTimeStep = TotpCodeVerifier.findMatchedTimeStep(
                secret,
                code,
                Instant.now(),
                TotpCodeVerifier.DEFAULT_WINDOW
        );
        if (matchedTimeStep.isEmpty()) {
            return fail("TOTP code is incorrect.");
        }

        int enabledRows = userLoginIdentityMapper.enableTotpById(identity.getId());
        if (enabledRows <= 0) {
            return fail("Failed to enable TOTP.");
        }
        userLoginIdentityMapper.updateTotpLastUsedStep(identity.getId(), matchedTimeStep.getAsLong());
        return success("TOTP enabled.", matchedTimeStep.getAsLong());
    }

    @Override
    @Transactional
    public TotpVerificationResult verify(Long userId, String code) {
        UserLoginIdentity identity = requireIdentity(userId);
        if (!Boolean.TRUE.equals(identity.getTotpEnabled())) {
            return fail("TOTP is not enabled.");
        }

        String secret = decodeSecretFromStorage(identity.getTotpSecretEncrypted());
        if (secret == null || secret.isBlank()) {
            return fail("TOTP secret is missing.");
        }

        OptionalLong matchedTimeStep = TotpCodeVerifier.findMatchedTimeStep(
                secret,
                code,
                Instant.now(),
                TotpCodeVerifier.DEFAULT_WINDOW
        );
        if (matchedTimeStep.isEmpty()) {
            return fail("TOTP code is incorrect.");
        }

        int updatedRows = userLoginIdentityMapper.updateTotpLastUsedStep(identity.getId(), matchedTimeStep.getAsLong());
        if (updatedRows <= 0) {
            return fail("TOTP code has already been used.");
        }
        return success("ok", matchedTimeStep.getAsLong());
    }

    @Override
    @Transactional
    public boolean disable(Long userId) {
        UserLoginIdentity identity = requireIdentity(userId);
        return userLoginIdentityMapper.disableTotpById(identity.getId()) > 0;
    }

    private UserLoginIdentity requireIdentity(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        UserLoginIdentity identity = userLoginIdentityMapper.findById(userId);
        if (identity == null) {
            throw new IllegalArgumentException("User login identity not found.");
        }
        return identity;
    }

    private String resolveAccountName(UserLoginIdentity identity) {
        if (identity.getEmail() != null && !identity.getEmail().isBlank()) {
            return identity.getEmail().trim().toLowerCase();
        }
        if (identity.getPhone() != null && !identity.getPhone().isBlank()) {
            return identity.getPhone().trim();
        }
        return String.valueOf(identity.getUserId());
    }

    private TotpVerificationResult success(String message, Long matchedTimeStep) {
        return TotpVerificationResult.builder()
                .success(true)
                .message(message)
                .matchedTimeStep(matchedTimeStep)
                .build();
    }

    private TotpVerificationResult fail(String message) {
        return TotpVerificationResult.builder()
                .success(false)
                .message(message)
                .build();
    }

    private String encodeSecretForStorage(String secret) {
        return secret;
    }

    private String decodeSecretFromStorage(String secretEncrypted) {
        return secretEncrypted;
    }
}
