package com.example.ShoppingSystem.service.user.profile;

import com.example.ShoppingSystem.service.user.profile.mq.UserAvatarUploadMessage;

public interface UserAvatarService {

    void submitAvatarUpload(Long userId, String originalFilename, String contentType, byte[] fileBytes);

    void processAvatarUpload(UserAvatarUploadMessage message);

    boolean deleteAvatar(Long userId);
}
