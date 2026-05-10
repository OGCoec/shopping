package com.example.ShoppingSystem.service.user.profile.impl;

import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.Utils.SnowflakeIdWorker;
import com.example.ShoppingSystem.entity.entity.UserLoginIdentity;
import com.example.ShoppingSystem.mapper.UserAccountSelfDeletionMapper;
import com.example.ShoppingSystem.mapper.UserLoginIdentityMapper;
import com.example.ShoppingSystem.service.user.profile.UserAccountDeletionMessagePublisher;
import com.example.ShoppingSystem.service.user.profile.UserAccountDeletionService;
import com.example.ShoppingSystem.service.user.profile.mq.UserAccountDeletionMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;

@Service
public class UserAccountDeletionServiceImpl implements UserAccountDeletionService {

    private static final String STATUS_DISABLED = "DISABLED";

    private final UserLoginIdentityMapper userLoginIdentityMapper;
    private final UserAccountSelfDeletionMapper userAccountSelfDeletionMapper;
    private final UserAccountDeletionMessagePublisher userAccountDeletionMessagePublisher;
    private final SnowflakeIdWorker snowflakeIdWorker;

    public UserAccountDeletionServiceImpl(UserLoginIdentityMapper userLoginIdentityMapper,
                                          UserAccountSelfDeletionMapper userAccountSelfDeletionMapper,
                                          UserAccountDeletionMessagePublisher userAccountDeletionMessagePublisher,
                                          SnowflakeIdWorker snowflakeIdWorker) {
        this.userLoginIdentityMapper = userLoginIdentityMapper;
        this.userAccountSelfDeletionMapper = userAccountSelfDeletionMapper;
        this.userAccountDeletionMessagePublisher = userAccountDeletionMessagePublisher;
        this.snowflakeIdWorker = snowflakeIdWorker;
    }

    @Override
    public void submitSelfDeletionRequest(Long userId, String email, String deletionReason, OffsetDateTime requestedAt) {
        if (userId == null) {
            throw new IllegalArgumentException("Current user is not authenticated.");
        }
        String normalizedEmail = normalizeEmail(email);
        if (StrUtil.isBlank(normalizedEmail)) {
            throw new IllegalArgumentException("Current account email is required.");
        }
        String normalizedReason = normalizeReason(deletionReason);
        userAccountDeletionMessagePublisher.publishSelfDeletionRequested(
                userId,
                normalizedEmail,
                normalizedReason,
                requestedAt == null ? OffsetDateTime.now() : requestedAt
        );
    }

    @Override
    @Transactional
    public MailTarget handleSelfDeletionRequested(UserAccountDeletionMessage message) {
        if (message == null || message.getUserId() == null) {
            throw new IllegalArgumentException("Deletion message userId is required.");
        }
        String normalizedReason = normalizeReason(message.getDeletionReason());
        OffsetDateTime requestedAt = fromEpochMilli(message.getRequestedAtEpochMilli());
        UserLoginIdentity identity = userLoginIdentityMapper.findByUserId(message.getUserId());
        if (identity == null) {
            throw new IllegalStateException("User login identity does not exist.");
        }
        String email = normalizeEmail(identity.getEmail());
        if (StrUtil.isBlank(email)) {
            email = normalizeEmail(message.getEmail());
        }
        if (StrUtil.isBlank(email)) {
            throw new IllegalStateException("User email is required for account deletion.");
        }
        String phone = Boolean.TRUE.equals(identity.getPhoneVerified()) ? normalizeText(identity.getPhone()) : null;

        userAccountSelfDeletionMapper.upsertPendingSelfDeletion(
                snowflakeIdWorker.nextId(),
                identity.getUserId(),
                email,
                sha256(email),
                phone,
                phone == null ? null : sha256(phone),
                normalizedReason,
                requestedAt,
                requestedAt
        );
        userLoginIdentityMapper.updateStatusByUserIdAt(identity.getUserId(), STATUS_DISABLED, requestedAt);
        return new MailTarget(identity.getUserId(), email, phone);
    }

    @Override
    @Transactional
    public List<MailTarget> completeExpiredSelfDeletionsBatch(OffsetDateTime cutoff, int limit) {
        OffsetDateTime safeCutoff = cutoff == null ? OffsetDateTime.now().minusDays(7) : cutoff;
        int safeLimit = Math.max(1, Math.min(limit, 500));
        return userAccountSelfDeletionMapper.completeDueSelfDeletionsBatch(safeCutoff, safeLimit)
                .stream()
                .map(target -> new MailTarget(
                        target.getUserId(),
                        normalizeEmail(target.getEmail()),
                        normalizePhone(target.getPhone())))
                .toList();
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        String normalized = email.trim().toLowerCase();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizePhone(String phone) {
        String normalized = normalizeText(phone);
        if (StrUtil.isBlank(normalized)) {
            return null;
        }
        return normalized.replace(" ", "")
                .replace("-", "")
                .replace("(", "")
                .replace(")", "");
    }

    private String normalizeReason(String reason) {
        String normalized = normalizeText(reason);
        if (StrUtil.isBlank(normalized)) {
            throw new IllegalArgumentException("Deletion reason is required.");
        }
        if (normalized.length() > 4000) {
            return normalized.substring(0, 4000);
        }
        return normalized;
    }

    private OffsetDateTime fromEpochMilli(Long epochMilli) {
        if (epochMilli == null || epochMilli <= 0L) {
            return OffsetDateTime.now();
        }
        return OffsetDateTime.ofInstant(Instant.ofEpochMilli(epochMilli), ZoneOffset.UTC);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(StrUtil.nullToEmpty(value).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest is unavailable.", e);
        }
    }
}
