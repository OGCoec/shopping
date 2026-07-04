package com.example.ShoppingSystem.signin.dto;

public record UserSignInResponse(boolean success,
                                 String code,
                                 String message,
                                 boolean signed,
                                 boolean alreadySigned,
                                 int rewardPoints,
                                 long availablePoints,
                                 long totalEarnedPoints,
                                 int continuousCount,
                                 int cycleDay,
                                 int nextMilestoneCycleDay,
                                 int periodsToNextMilestone,
                                 int nextMilestoneRewardPoints) {

    public static UserSignInResponse authRequired() {
        return new UserSignInResponse(
                false,
                "SIGN_IN_AUTH_REQUIRED",
                "Current user is not authenticated.",
                false,
                false,
                0,
                0L,
                0L,
                0,
                0,
                0,
                0,
                0
        );
    }

    public static UserSignInResponse signed(int rewardPoints,
                                            long availablePoints,
                                            long totalEarnedPoints,
                                            int continuousCount,
                                            int cycleDay,
                                            int nextMilestoneCycleDay,
                                            int periodsToNextMilestone,
                                            int nextMilestoneRewardPoints) {
        return new UserSignInResponse(
                true,
                "SIGN_IN_OK",
                "签到成功，+" + rewardPoints + " 积分",
                true,
                false,
                rewardPoints,
                availablePoints,
                totalEarnedPoints,
                continuousCount,
                cycleDay,
                nextMilestoneCycleDay,
                periodsToNextMilestone,
                nextMilestoneRewardPoints
        );
    }

    public static UserSignInResponse alreadySigned(long availablePoints,
                                                   long totalEarnedPoints,
                                                   int continuousCount,
                                                   int cycleDay,
                                                   int nextMilestoneCycleDay,
                                                   int periodsToNextMilestone,
                                                   int nextMilestoneRewardPoints) {
        return new UserSignInResponse(
                true,
                "SIGN_IN_ALREADY_DONE",
                "当天已经签到",
                false,
                true,
                0,
                availablePoints,
                totalEarnedPoints,
                continuousCount,
                cycleDay,
                nextMilestoneCycleDay,
                periodsToNextMilestone,
                nextMilestoneRewardPoints
        );
    }
}
