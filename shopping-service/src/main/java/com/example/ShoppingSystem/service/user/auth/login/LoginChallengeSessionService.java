package com.example.ShoppingSystem.service.user.auth.login;
import com.example.ShoppingSystem.service.user.auth.register.model.ChallengeSelection;
public interface LoginChallengeSessionService {
    public ChallengeSelection readPendingChallengeSelection(String email, String deviceFingerprint);

    public ChallengeSelection savePendingChallengeSelection(String email,
                                                            String deviceFingerprint,
                                                            ChallengeSelection challengeSelection);

    public void clearPendingChallengeSelection(String email, String deviceFingerprint);

    public boolean refreshPendingChallengeSelection(String email,
                                                    String deviceFingerprint,
                                                    ChallengeSelection expectedChallengeSelection);

    public long ensureOperationTimeoutWaitUntil(String email, String deviceFingerprint);

    public Long readOperationTimeoutWaitUntil(String email, String deviceFingerprint);

    public void markWafVerified(String preAuthToken);

    public boolean isWafVerified(String preAuthToken);

    public boolean consumeWafVerified(String preAuthToken);
}
