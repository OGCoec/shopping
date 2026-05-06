package com.example.ShoppingSystem.service.user.profile.impl;

import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.Utils.AliyunUtils;
import com.example.ShoppingSystem.avatar.AvatarMetadata;
import com.example.ShoppingSystem.avatar.AvatarMetadataUtils;
import com.example.ShoppingSystem.mapper.UserProfileMapper;
import com.example.ShoppingSystem.service.user.profile.UserAvatarService;
import com.example.ShoppingSystem.service.user.profile.UserAvatarUploadMessagePublisher;
import com.example.ShoppingSystem.service.user.profile.mq.UserAvatarUploadMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Set;

@Service
public class UserAvatarServiceImpl implements UserAvatarService {

    private static final Logger log = LoggerFactory.getLogger(UserAvatarServiceImpl.class);
    private static final String AVATAR_BUCKET = "shopping6655";
    private static final String USER_CONTEXT_CACHE_KEY_PREFIX = "auth:user:context:";
    private static final long MAX_FILE_SIZE_BYTES = 2L * 1024L * 1024L;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp", "bmp");

    private final AliyunUtils aliyunUtils;
    private final UserProfileMapper userProfileMapper;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final UserAvatarUploadMessagePublisher userAvatarUploadMessagePublisher;

    public UserAvatarServiceImpl(AliyunUtils aliyunUtils,
                                 UserProfileMapper userProfileMapper,
                                 ObjectMapper objectMapper,
                                 StringRedisTemplate stringRedisTemplate,
                                 UserAvatarUploadMessagePublisher userAvatarUploadMessagePublisher) {
        this.aliyunUtils = aliyunUtils;
        this.userProfileMapper = userProfileMapper;
        this.objectMapper = objectMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.userAvatarUploadMessagePublisher = userAvatarUploadMessagePublisher;
    }

    @Override
    public void submitAvatarUpload(Long userId, String originalFilename, String contentType, byte[] fileBytes) {
        requireUserId(userId);
        validateImagePayload(originalFilename, contentType, fileBytes);
        userAvatarUploadMessagePublisher.publishAvatarUpload(userId, originalFilename, contentType, fileBytes);
    }

    @Override
    public void processAvatarUpload(UserAvatarUploadMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("Avatar upload message is required.");
        }
        requireUserId(message.getUserId());
        validateImagePayload(message.getOriginalFilename(), message.getContentType(), message.getFileBytes());

        String oldAvatarJson = userProfileMapper.findAvatarById(message.getUserId());
        AvatarMetadata oldAvatar = AvatarMetadataUtils.parse(oldAvatarJson, objectMapper);
        String objectKey = buildObjectKey(message.getUserId(), message.getMessageId(), message.getOriginalFilename(), message.getContentType());
        String uploadedUrl = aliyunUtils.uploadFileToBucket(
                AVATAR_BUCKET,
                AliyunUtils.HONG_KONG_OSS_REGION,
                AliyunUtils.HONG_KONG_OSS_ENDPOINT,
                objectKey,
                message.getFileBytes()
        ).join();

        AvatarMetadata newAvatar = AvatarMetadata.builder()
                .bucket(AVATAR_BUCKET)
                .objectKey(objectKey)
                .url(uploadedUrl)
                .uploadedAtEpochMilli(System.currentTimeMillis())
                .build();

        try {
            ensureProfileRow(message.getUserId());
            int updated = userProfileMapper.updateAvatarById(
                    message.getUserId(),
                    uploadedUrl
            );
            if (updated <= 0) {
                throw new IllegalStateException("Failed to update avatar URL.");
            }
            evictUserContext(message.getUserId());
        } catch (Exception ex) {
            deleteQuietly(newAvatar);
            throw ex;
        }

        deletePreviousAvatar(oldAvatar, objectKey);
        log.info("User avatar updated, userId={}, objectKey={}", message.getUserId(), objectKey);
    }

    @Override
    public boolean deleteAvatar(Long userId) {
        requireUserId(userId);
        String rawAvatar = userProfileMapper.findAvatarById(userId);
        AvatarMetadata avatarMetadata = AvatarMetadataUtils.parse(rawAvatar, objectMapper);
        if (StrUtil.isBlank(rawAvatar)) {
            return false;
        }

        int updated = userProfileMapper.clearAvatarById(userId);
        if (updated <= 0) {
            return false;
        }
        evictUserContext(userId);
        if (avatarMetadata != null) {
            deleteQuietly(avatarMetadata);
        }
        return true;
    }

    private void ensureProfileRow(Long userId) {
        userProfileMapper.insertStubIfAbsent(userId);
    }

    private void evictUserContext(Long userId) {
        stringRedisTemplate.delete(USER_CONTEXT_CACHE_KEY_PREFIX + userId);
    }

    private void deletePreviousAvatar(AvatarMetadata previousAvatar, String currentObjectKey) {
        if (previousAvatar == null || !previousAvatar.hasObjectLocation()) {
            return;
        }
        if (StrUtil.equals(previousAvatar.getBucket(), AVATAR_BUCKET)
                && StrUtil.equals(previousAvatar.getObjectKey(), currentObjectKey)) {
            return;
        }
        deleteQuietly(previousAvatar);
    }

    private void deleteQuietly(AvatarMetadata avatarMetadata) {
        if (avatarMetadata == null || !avatarMetadata.hasObjectLocation()) {
            return;
        }
        try {
            aliyunUtils.deleteFileFromBucket(
                    avatarMetadata.getBucket(),
                    AliyunUtils.HONG_KONG_OSS_REGION,
                    AliyunUtils.HONG_KONG_OSS_ENDPOINT,
                    avatarMetadata.getObjectKey()
            ).join();
        } catch (Exception ex) {
            log.warn("Failed to delete avatar object, bucket={}, objectKey={}, error={}",
                    avatarMetadata.getBucket(), avatarMetadata.getObjectKey(), ex.getMessage());
        }
    }

    private void validateImagePayload(String originalFilename, String contentType, byte[] fileBytes) {
        if (fileBytes == null || fileBytes.length == 0) {
            throw new IllegalArgumentException("Avatar image is required.");
        }
        if (fileBytes.length > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("Avatar image must not exceed 2MB.");
        }
        String normalizedContentType = StrUtil.blankToDefault(contentType, "").trim().toLowerCase(Locale.ROOT);
        if (!normalizedContentType.startsWith("image/")) {
            throw new IllegalArgumentException("Only image files are allowed.");
        }
        String extension = resolveExtension(originalFilename, normalizedContentType);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Only jpg, jpeg, png, gif, webp, and bmp are supported.");
        }
    }

    private String buildObjectKey(Long userId, String messageId, String originalFilename, String contentType) {
        String extension = resolveExtension(originalFilename, contentType);
        String sanitizedMessageId = StrUtil.blankToDefault(messageId, String.valueOf(System.currentTimeMillis()))
                .replaceAll("[^A-Za-z0-9_-]", "");
        return "user/avatar/" + userId + "/" + System.currentTimeMillis() + "-" + sanitizedMessageId + "." + extension;
    }

    private String resolveExtension(String originalFilename, String contentType) {
        String filename = StrUtil.blankToDefault(originalFilename, "").trim();
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex >= 0 && dotIndex < filename.length() - 1) {
            return filename.substring(dotIndex + 1).trim().toLowerCase(Locale.ROOT);
        }

        String normalizedContentType = StrUtil.blankToDefault(contentType, "").trim().toLowerCase(Locale.ROOT);
        return switch (normalizedContentType) {
            case "image/jpeg", "image/jpg" -> "jpg";
            case "image/png" -> "png";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            case "image/bmp" -> "bmp";
            default -> "png";
        };
    }

    private void requireUserId(Long userId) {
        if (userId == null || userId <= 0L) {
            throw new IllegalArgumentException("Current user is not authenticated.");
        }
    }
}
