package com.example.ShoppingSystem.service.user.auth.login;

import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.redisdata.LoginRedisKeys;
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
