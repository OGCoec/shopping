package com.example.ShoppingSystem.admin.dto;

import java.util.List;

public final class AdminAccountManagementDtos {

    private AdminAccountManagementDtos() {
    }

    public record AccountCreditListResponse(String riskLevel,
                                            String status,
                                            int page,
                                            int pageSize,
                                            long total,
                                            boolean hasNext,
                                            List<AccountCreditListItemResponse> items) {
    }

    public record AccountCreditListItemResponse(Long userId,
                                                String email,
                                                String phone,
                                                String status,
                                                int currentScore,
                                                String riskLevel,
                                                int lockCount,
                                                String lockReason,
                                                String lockUntil,
                                                String lastLoginAt,
                                                String updatedAt) {
    }

    public record AccountCreditDetailResponse(Long userId,
                                              String email,
                                              String phone,
                                              String status,
                                              int currentScore,
                                              String riskLevel,
                                              int currentEnvScore,
                                              int behaviorScoreDelta,
                                              int lockCount,
                                              String lockReason,
                                              String lockUntil,
                                              String riskRecoveryStartedAt,
                                              String lastRiskPenaltyAt,
                                              String lastLoginAt,
                                              String lastLoginIp,
                                              String lastDeviceFingerprint,
                                              String updatedAt,
                                              AccountLoginRecordResponse firstLogin,
                                              List<AccountRiskScoreEventResponse> recentEvents) {
    }

    public record AccountRiskScoreEventListResponse(Long userId,
                                                    int page,
                                                    int pageSize,
                                                    long total,
                                                    boolean hasNext,
                                                    List<AccountRiskScoreEventResponse> items) {
    }

    public record AccountRiskScoreEventResponse(Long id,
                                                String eventType,
                                                int scoreBefore,
                                                int scoreDelta,
                                                int scoreAfter,
                                                String riskLevelBefore,
                                                String riskLevelAfter,
                                                String reason,
                                                String ip,
                                                String deviceFingerprint,
                                                String metadata,
                                                String createdAt) {
    }

    public record AccountLoginRecordResponse(String loginType,
                                             String loginIp,
                                             String userAgent,
                                             String deviceFingerprint,
                                             String loginAt) {
    }

    public record AccountScoreAdjustRequest(Integer scoreDelta,
                                            String reason) {
    }

    public record AccountScoreAdjustResponse(Long userId,
                                             int scoreBefore,
                                             int scoreDelta,
                                             int scoreAfter,
                                             String riskLevelBefore,
                                             String riskLevelAfter,
                                             String eventType,
                                             String adjustedAt) {
    }

    public record AccountSelfTerminationListResponse(String scope,
                                                     int page,
                                                     int pageSize,
                                                     long total,
                                                     boolean hasNext,
                                                     List<AccountSelfTerminationItemResponse> items) {
    }

    public record AccountSelfTerminationItemResponse(Long id,
                                                     Long userId,
                                                     String email,
                                                     String phone,
                                                     String status,
                                                     boolean deleted,
                                                     boolean restorable,
                                                     String deletionReason,
                                                     String deletedAt,
                                                     String createdAt,
                                                     String restoredAt,
                                                     String restoredBy,
                                                     String restoreReason) {
    }

    public record AccountRestoreRequest(String reason) {
    }

    public record AccountRestoreResponse(Long id,
                                         Long userId,
                                         String status,
                                         String restoredAt) {
    }

    public record AccountRiskTerminationListResponse(int page,
                                                     int pageSize,
                                                     long total,
                                                     boolean hasNext,
                                                     List<AccountRiskTerminationItemResponse> items) {
    }

    public record AccountRiskTerminationItemResponse(Long id,
                                                     Long userId,
                                                     String email,
                                                     String phone,
                                                     String status,
                                                     int currentScore,
                                                     String riskLevel,
                                                     int lockCount,
                                                     String terminationReason,
                                                     String terminatedAt,
                                                     String createdAt) {
    }

    public record AccountRiskTerminationDetailResponse(AccountRiskTerminationItemResponse termination,
                                                       List<AccountRiskScoreEventResponse> recentEvents) {
    }
}
