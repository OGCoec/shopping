package com.example.ShoppingSystem.admin.dto;

import java.util.List;

public record AdminDeviceRiskDetailResponse(String deviceId,
                                            String fingerprintHash,
                                            String maskedFingerprint,
                                            int currentScore,
                                            String riskLevel,
                                            String firstSeenAt,
                                            String lastSeenAt,
                                            String lastLoginIp,
                                            String lastIpSeenAt,
                                            int linkedUserCount,
                                            int recentDistinctIpCount,
                                            int recentIpSwitchCount,
                                            String lastPenaltyReason,
                                            int lastPenaltyScore,
                                            String lastPenaltyAt,
                                            List<String> usedIpList,
                                            List<AdminDeviceScoreEventResponse> scoreEvents) {
}
