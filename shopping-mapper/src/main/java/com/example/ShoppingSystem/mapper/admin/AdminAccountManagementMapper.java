package com.example.ShoppingSystem.mapper.admin;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface AdminAccountManagementMapper {

    List<Map<String, Object>> listAccountCreditProfiles(@Param("userId") Long userId,
                                                        @Param("userIds") List<Long> userIds,
                                                        @Param("riskLevel") String riskLevel);

    Map<String, Object> findAccountCreditDetail(@Param("userId") Long userId);

    Map<String, Object> findFirstLoginRecord(@Param("userId") Long userId);

    List<Map<String, Object>> listRiskScoreEvents(@Param("userId") Long userId);

    List<Map<String, Object>> listRecentRiskScoreEvents(@Param("userId") Long userId,
                                                        @Param("limit") int limit);

    Map<String, Object> lockRiskProfileForAdjust(@Param("userId") Long userId);

    int updateRiskProfileScore(@Param("userId") Long userId,
                               @Param("scoreDelta") int scoreDelta,
                               @Param("scoreAfter") int scoreAfter,
                               @Param("riskLevelAfter") String riskLevelAfter,
                               @Param("updatedAt") OffsetDateTime updatedAt);

    int insertRiskScoreEvent(@Param("id") Long id,
                             @Param("userId") Long userId,
                             @Param("eventType") String eventType,
                             @Param("scoreBefore") int scoreBefore,
                             @Param("scoreDelta") int scoreDelta,
                             @Param("scoreAfter") int scoreAfter,
                             @Param("riskLevelBefore") String riskLevelBefore,
                             @Param("riskLevelAfter") String riskLevelAfter,
                             @Param("reason") String reason,
                             @Param("metadataJson") String metadataJson,
                             @Param("createdAt") OffsetDateTime createdAt);

    List<Map<String, Object>> listSelfTerminations(@Param("scope") String scope,
                                                   @Param("userId") Long userId,
                                                   @Param("emailPattern") String emailPattern,
                                                   @Param("phonePattern") String phonePattern,
                                                   @Param("cutoff") OffsetDateTime cutoff);

    Map<String, Object> findSelfTerminationById(@Param("id") Long id);

    int restoreDisabledIdentity(@Param("userId") Long userId,
                                @Param("updatedAt") OffsetDateTime updatedAt);

    int markSelfTerminationRestored(@Param("id") Long id,
                                    @Param("restoredAt") OffsetDateTime restoredAt,
                                    @Param("restoredBy") String restoredBy,
                                    @Param("restoreReason") String restoreReason,
                                    @Param("cutoff") OffsetDateTime cutoff);

    List<Map<String, Object>> listRiskTerminations(@Param("userId") Long userId,
                                                   @Param("emailPattern") String emailPattern,
                                                   @Param("phonePattern") String phonePattern);

    Map<String, Object> findRiskTerminationById(@Param("id") Long id);
}
