package com.example.ShoppingSystem.service.user.auth.register;

import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.redisdata.RegisterRedisKeys;
import com.example.ShoppingSystem.service.user.auth.register.model.ChallengeSelection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.CHALLENGE_OPERATION_TIMEOUT;

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
