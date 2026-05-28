package com.example.ShoppingSystem.mapper.risk;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.Map;

@Mapper
public interface UserRiskProfileMapper {

    Map<String, Object> findUserRiskStateByUserId(@Param("userId") Long userId);

    int upsertUserAuthLockState(@Param("userId") Long userId,
                                @Param("currentEnvScore") int currentEnvScore,
                                @Param("behaviorScoreDelta") int behaviorScoreDelta,
                                @Param("currentScore") int currentScore,
                                @Param("riskLevel") String riskLevel,
                                @Param("lockCount") int lockCount,
                                @Param("lockedAt") OffsetDateTime lockedAt,
                                @Param("lockUntil") OffsetDateTime lockUntil,
                                @Param("lockReason") String lockReason,
                                @Param("updatedAt") OffsetDateTime updatedAt);

    int insertUserRiskScoreEvent(@Param("id") Long id,
                                 @Param("userId") Long userId,
                                 @Param("eventType") String eventType,
                                 @Param("scoreBefore") int scoreBefore,
                                 @Param("scoreDelta") int scoreDelta,
                                 @Param("scoreAfter") int scoreAfter,
                                 @Param("riskLevelBefore") String riskLevelBefore,
                                 @Param("riskLevelAfter") String riskLevelAfter,
                                 @Param("reason") String reason,
                                 @Param("ip") String ip,
                                 @Param("deviceFingerprint") String deviceFingerprint,
                                 @Param("metadataJson") String metadataJson,
                                 @Param("createdAt") OffsetDateTime createdAt);

    int touchUserNetworkState(@Param("userId") Long userId,
                              @Param("defaultScore") int defaultScore,
                              @Param("defaultRiskLevel") String defaultRiskLevel,
                              @Param("seenAt") OffsetDateTime seenAt,
                              @Param("currentIp") String currentIp,
                              @Param("deviceFingerprint") String deviceFingerprint,
                              @Param("updatedAt") OffsetDateTime updatedAt);

    int markRiskRecoveryStarted(@Param("userId") Long userId,
                                @Param("startedAt") OffsetDateTime startedAt);

    int recoverStableUnlockedUsers(@Param("lockCount") int lockCount,
                                   @Param("cutoff") OffsetDateTime cutoff,
                                   @Param("scoreBonus") int scoreBonus,
                                   @Param("stableDays") int stableDays,
                                   @Param("now") OffsetDateTime now,
                                   @Param("limit") int limit);

    int recoverStableUnlockedUsersByReason(@Param("lockReason") String lockReason,
                                           @Param("eventType") String eventType,
                                           @Param("eventReason") String eventReason,
                                           @Param("lockCount") int lockCount,
                                           @Param("cutoff") OffsetDateTime cutoff,
                                           @Param("scoreBonus") int scoreBonus,
                                           @Param("stableDays") int stableDays,
                                           @Param("now") OffsetDateTime now,
                                           @Param("limit") int limit);
}
