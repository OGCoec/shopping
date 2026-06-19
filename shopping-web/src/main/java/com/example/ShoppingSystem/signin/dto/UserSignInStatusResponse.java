package com.example.ShoppingSystem.signin.dto;

public record UserSignInStatusResponse(boolean success,
                                       String code,
                                       String message,
                                       boolean signedInCurrentPeriod,
                                       long availablePoints,
                                       long totalEarnedPoints,
                                       int continuousCount,
                                       int cycleDay,
                                       int nextMilestoneCycleDay,
                                       int periodsToNextMilestone,
                                       int nextMilestoneRewardPoints,
                                       String periodUnit) {

    public static UserSignInStatusResponse authRequired() {
        return new UserSignInStatusResponse(
                false,
                "SIGN_IN_AUTH_REQUIRED",
                "Current user is not authenticated.",
                false,
                0L,
                0L,
                0,
                0,
                0,
                0,
                0,
                ""
        );
    }

    public static UserSignInStatusResponse status(boolean signedInCurrentPeriod,
                                                  long availablePoints,
                                                  long totalEarnedPoints,
                                                  int continuousCount,
                                                  int cycleDay,
                                                  int nextMilestoneCycleDay,
                                                  int periodsToNextMilestone,
                                                  int nextMilestoneRewardPoints,
                                                  String periodUnit) {
        return new UserSignInStatusResponse(
                true,
                "SIGN_IN_STATUS",
                "签到状态",
                signedInCurrentPeriod,
                availablePoints,
                totalEarnedPoints,
                continuousCount,
                cycleDay,
                nextMilestoneCycleDay,
                periodsToNextMilestone,
                nextMilestoneRewardPoints,
                periodUnit == null ? "" : periodUnit
        );
    }
}
