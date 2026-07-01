package com.example.ShoppingSystem.service.user.auth.register;
import com.example.ShoppingSystem.service.user.auth.register.model.ChallengeSelection;
public interface ChallengeSessionService {
    public ChallengeSelection readPendingChallengeSelection(String email, String deviceFingerprint);

    public ChallengeSelection resolveChallengeSelectionForCurrentAttempt(ChallengeSelection pendingChallengeSelection,
                                                                         ChallengeSelection riskBasedChallengeSelection);

    public ChallengeSelection savePendingChallengeSelection(String email,
                                                            String deviceFingerprint,
                                                            ChallengeSelection challengeSelection);

    public void clearPendingChallengeSelection(String email, String deviceFingerprint);

    public boolean refreshPendingChallengeSelection(String email,
                                                    String deviceFingerprint,
                                                    ChallengeSelection expectedChallengeSelection);

    public long ensureOperationTimeoutWaitUntil(String email, String deviceFingerprint);

    public Long readOperationTimeoutWaitUntil(String email, String deviceFingerprint);

    public long getOperationTimeoutRemainingMillis(String email, String deviceFingerprint);
}
