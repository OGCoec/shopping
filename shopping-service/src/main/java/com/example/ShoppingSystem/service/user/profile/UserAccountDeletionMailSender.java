package com.example.ShoppingSystem.service.user.profile;

public interface UserAccountDeletionMailSender {

    void sendDeletionQueued(String email);

    void sendDeletionCompleted(String email);
}
