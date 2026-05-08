package com.example.ShoppingSystem.service.user.profile;

import com.example.ShoppingSystem.service.user.profile.mq.UserAccountDeletionMessage;

import java.time.OffsetDateTime;
import java.util.List;

public interface UserAccountDeletionService {

    void submitSelfDeletionRequest(Long userId, String email, String deletionReason, OffsetDateTime requestedAt);

    MailTarget handleSelfDeletionRequested(UserAccountDeletionMessage message);

    List<MailTarget> completeExpiredSelfDeletionsBatch(OffsetDateTime cutoff, int limit);

    record MailTarget(Long userId, String email) {
    }
}
