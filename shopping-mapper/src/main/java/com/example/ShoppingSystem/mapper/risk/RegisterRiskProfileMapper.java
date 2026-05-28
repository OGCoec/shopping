package com.example.ShoppingSystem.mapper.risk;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Mapper for user/device risk profile writeback.
 */
@Mapper
public interface RegisterRiskProfileMapper {

    Integer findDeviceRiskScoreByFingerprint(@Param("deviceFingerprint") String deviceFingerprint);

    long countDeviceFingerprintsByCurrentScoreLessThan(@Param("scoreThreshold") int scoreThreshold);

    List<String> listDeviceFingerprintsByCurrentScoreLessThan(@Param("scoreThreshold") int scoreThreshold,
                                                              @Param("limit") int limit,
                                                              @Param("offset") long offset);

    Integer findLinkedUserCountByFingerprint(@Param("deviceFingerprint") String deviceFingerprint);

    int countLinkedUsersByFingerprint(@Param("deviceFingerprint") String deviceFingerprint);

    Map<String, Object> findDeviceRiskStateByFingerprint(@Param("deviceFingerprint") String deviceFingerprint);

    int applyDeviceRiskIpChangePenalty(@Param("deviceFingerprint") String deviceFingerprint,
                                       @Param("currentIp") String currentIp,
                                       @Param("seenAt") OffsetDateTime seenAt,
                                       @Param("transition") String transition,
                                       @Param("penaltyScore") int penaltyScore,
                                       @Param("penaltyReason") String penaltyReason);

    int applyDeviceLinkedUserCountPenalty(@Param("deviceFingerprint") String deviceFingerprint,
                                          @Param("previousPenaltyTier") int previousPenaltyTier,
                                          @Param("targetPenaltyTier") int targetPenaltyTier,
                                          @Param("minimumLinkedUserCount") int minimumLinkedUserCount,
                                          @Param("penaltyScore") int penaltyScore,
                                          @Param("penaltyReason") String penaltyReason,
                                          @Param("penalizedAt") OffsetDateTime penalizedAt);

    Integer applyDeviceAutomationPenalty(@Param("deviceFingerprint") String deviceFingerprint,
                                         @Param("clientIp") String clientIp,
                                         @Param("penaltyScore") int penaltyScore,
                                         @Param("penaltyReason") String penaltyReason,
                                         @Param("penalizedAt") OffsetDateTime penalizedAt);

    int upsertUserRiskProfile(@Param("userId") Long userId,
                              @Param("currentScore") int currentScore,
                              @Param("riskLevel") String riskLevel,
                              @Param("lastLoginAt") OffsetDateTime lastLoginAt,
                              @Param("lastLoginIp") String lastLoginIp,
                              @Param("lastDeviceFingerprint") String lastDeviceFingerprint,
                              @Param("updatedAt") OffsetDateTime updatedAt);

    String upsertDeviceRiskProfile(@Param("idHex") String idHex,
                                   @Param("deviceFingerprint") String deviceFingerprint,
                                   @Param("currentScore") int currentScore,
                                   @Param("riskLevel") String riskLevel,
                                   @Param("firstSeenAt") OffsetDateTime firstSeenAt,
                                   @Param("lastSeenAt") OffsetDateTime lastSeenAt,
                                   @Param("lastLoginIp") String lastLoginIp,
                                   @Param("lastIpSeenAt") OffsetDateTime lastIpSeenAt,
                                   @Param("lastPenaltyAt") OffsetDateTime lastPenaltyAt,
                                   @Param("lastPenaltyScore") int lastPenaltyScore,
                                   @Param("lastPenaltyReason") String lastPenaltyReason,
                                   @Param("updatedAt") OffsetDateTime updatedAt);

    int upsertDeviceUserRelationSuccess(@Param("idHex") String idHex,
                                        @Param("deviceIdHex") String deviceIdHex,
                                        @Param("userId") Long userId,
                                        @Param("seenAt") OffsetDateTime seenAt);

    int upsertDeviceUserRelationFailure(@Param("idHex") String idHex,
                                        @Param("deviceIdHex") String deviceIdHex,
                                        @Param("userId") Long userId,
                                        @Param("seenAt") OffsetDateTime seenAt);

    int refreshDeviceLinkedUserCount(@Param("deviceIdHex") String deviceIdHex,
                                     @Param("updatedAt") OffsetDateTime updatedAt);
}
