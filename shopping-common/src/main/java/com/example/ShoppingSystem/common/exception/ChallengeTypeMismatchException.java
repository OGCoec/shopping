package com.example.ShoppingSystem.common.exception;

/**
 * 当第二次提交验证码结果时，前端声明的挑战类型与服务端 Redis 中挂起的挑战类型不一致时抛出。
 * 该异常用于显式区分“验证码本身校验失败”和“本次提交压根不是同一类挑战”这两种情况。
 */
public class ChallengeTypeMismatchException extends RuntimeException {

    private final String expectedChallengeType;
    private final String expectedChallengeSubType;
    private final String challengeSiteKey;

    public ChallengeTypeMismatchException(String message,
                                          String expectedChallengeType,
                                          String expectedChallengeSubType,
                                          String challengeSiteKey) {
        super(message);
        this.expectedChallengeType = expectedChallengeType;
        this.expectedChallengeSubType = expectedChallengeSubType;
        this.challengeSiteKey = challengeSiteKey;
    }

    public String getExpectedChallengeType() {
        return expectedChallengeType;
    }

    public String getExpectedChallengeSubType() {
        return expectedChallengeSubType;
    }

    public String getChallengeSiteKey() {
        return challengeSiteKey;
    }
}
