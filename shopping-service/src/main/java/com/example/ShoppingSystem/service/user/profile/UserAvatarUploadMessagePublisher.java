package com.example.ShoppingSystem.service.user.profile;

import com.example.ShoppingSystem.service.user.profile.mq.UserAvatarUploadMessage;

public interface UserAvatarUploadMessagePublisher {

    void publishAvatarUpload(Long userId, String originalFilename, String contentType, byte[] fileBytes);

    void publishRetry(UserAvatarUploadMessage message, long delayMilli);

    void publishDeadLetter(UserAvatarUploadMessage message);
}
