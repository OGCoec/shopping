package com.example.ShoppingSystem.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface AdminAccountManagementMapper {

    @Select("""
            <script>
            SELECT urp.user_id AS "userId",
                   uli.email,
                   uli.phone,
                   uli.status,
                   urp.current_score AS "currentScore",
                   urp.risk_level AS "riskLevel",
                   urp.lock_count AS "lockCount",
                   urp.lock_reason AS "lockReason",
                   urp.lock_until AS "lockUntil",
                   urp.last_login_at AS "lastLoginAt",
                   urp.updated_at AS "updatedAt"
            FROM user_risk_profile urp
            LEFT JOIN user_login_identity uli ON uli.user_id = urp.user_id
            <where>
                <if test="userId != null">
                    AND urp.user_id = #{userId}
                </if>
                <if test="emailPattern != null">
                    AND LOWER(uli.email) LIKE #{emailPattern}
                </if>
                <if test="phonePattern != null">
                    AND uli.phone LIKE #{phonePattern}
                </if>
                <if test="status != null">
                    AND uli.status = #{status}
                </if>
                <if test="riskLevel != null">
                    AND urp.risk_level = #{riskLevel}
                </if>
            </where>
            ORDER BY urp.current_score ASC, urp.updated_at DESC NULLS LAST, urp.user_id DESC
            </script>
            """)
    List<Map<String, Object>> listAccountCreditProfiles(@Param("userId") Long userId,
                                                        @Param("emailPattern") String emailPattern,
                                                        @Param("phonePattern") String phonePattern,
                                                        @Param("status") String status,
                                                        @Param("riskLevel") String riskLevel);

    @Select("""
            SELECT urp.user_id AS "userId",
                   uli.email,
                   uli.phone,
                   uli.status,
                   urp.current_score AS "currentScore",
                   urp.risk_level AS "riskLevel",
                   urp.current_env_score AS "currentEnvScore",
                   urp.behavior_score_delta AS "behaviorScoreDelta",
                   urp.lock_count AS "lockCount",
                   urp.lock_reason AS "lockReason",
                   urp.lock_until AS "lockUntil",
                   urp.risk_recovery_started_at AS "riskRecoveryStartedAt",
                   urp.last_risk_penalty_at AS "lastRiskPenaltyAt",
                   urp.last_login_at AS "lastLoginAt",
                   urp.last_login_ip AS "lastLoginIp",
                   urp.last_device_fingerprint AS "lastDeviceFingerprint",
                   urp.updated_at AS "updatedAt"
            FROM user_risk_profile urp
            LEFT JOIN user_login_identity uli ON uli.user_id = urp.user_id
            WHERE urp.user_id = #{userId}
            LIMIT 1
            """)
    Map<String, Object> findAccountCreditDetail(@Param("userId") Long userId);

    @Select("""
            SELECT login_type AS "loginType",
                   login_ip AS "loginIp",
                   user_agent AS "userAgent",
                   device_fingerprint AS "deviceFingerprint",
                   login_at AS "loginAt"
            FROM user_login_success_record
            WHERE user_id = #{userId}
            ORDER BY login_at ASC
            LIMIT 1
            """)
    Map<String, Object> findFirstLoginRecord(@Param("userId") Long userId);

    @Select("""
            SELECT id,
                   event_type AS "eventType",
                   score_before AS "scoreBefore",
                   score_delta AS "scoreDelta",
                   score_after AS "scoreAfter",
                   risk_level_before AS "riskLevelBefore",
                   risk_level_after AS "riskLevelAfter",
                   reason,
                   ip,
                   device_fingerprint AS "deviceFingerprint",
                   metadata,
                   created_at AS "createdAt"
            FROM user_risk_score_event
            WHERE user_id = #{userId}
            ORDER BY created_at DESC, id DESC
            """)
    List<Map<String, Object>> listRiskScoreEvents(@Param("userId") Long userId);

    @Select("""
            SELECT id,
                   event_type AS "eventType",
                   score_before AS "scoreBefore",
                   score_delta AS "scoreDelta",
                   score_after AS "scoreAfter",
                   risk_level_before AS "riskLevelBefore",
                   risk_level_after AS "riskLevelAfter",
                   reason,
                   ip,
                   device_fingerprint AS "deviceFingerprint",
                   metadata,
                   created_at AS "createdAt"
            FROM user_risk_score_event
            WHERE user_id = #{userId}
            ORDER BY created_at DESC, id DESC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> listRecentRiskScoreEvents(@Param("userId") Long userId,
                                                        @Param("limit") int limit);

    @Select("""
            SELECT user_id AS "userId",
                   current_score AS "currentScore",
                   risk_level AS "riskLevel",
                   current_env_score AS "currentEnvScore",
                   behavior_score_delta AS "behaviorScoreDelta"
            FROM user_risk_profile
            WHERE user_id = #{userId}
            FOR UPDATE
            """)
    Map<String, Object> lockRiskProfileForAdjust(@Param("userId") Long userId);

    @Update("""
            UPDATE user_risk_profile
            SET current_score = #{scoreAfter},
                risk_level = #{riskLevelAfter},
                behavior_score_delta = behavior_score_delta + #{scoreDelta},
                last_risk_penalty_at = CASE
                    WHEN #{scoreDelta} < 0 THEN #{updatedAt}
                    ELSE last_risk_penalty_at
                END,
                updated_at = #{updatedAt}
            WHERE user_id = #{userId}
            """)
    int updateRiskProfileScore(@Param("userId") Long userId,
                               @Param("scoreDelta") int scoreDelta,
                               @Param("scoreAfter") int scoreAfter,
                               @Param("riskLevelAfter") String riskLevelAfter,
                               @Param("updatedAt") OffsetDateTime updatedAt);

    @Insert("""
            INSERT INTO user_risk_score_event (
                id,
                user_id,
                event_type,
                score_before,
                score_delta,
                score_after,
                risk_level_before,
                risk_level_after,
                reason,
                ip,
                device_fingerprint,
                metadata,
                created_at
            ) VALUES (
                #{id},
                #{userId},
                #{eventType},
                #{scoreBefore},
                #{scoreDelta},
                #{scoreAfter},
                #{riskLevelBefore},
                #{riskLevelAfter},
                #{reason},
                NULL,
                NULL,
                CAST(#{metadataJson} AS jsonb),
                #{createdAt}
            )
            """)
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

    @Select("""
            <script>
            SELECT s.id,
                   s.user_id AS "userId",
                   s.email,
                   s.phone,
                   uli.status,
                   s.is_deleted AS "deleted",
                   (
                       s.is_deleted = FALSE
                       AND s.restored_at IS NULL
                       AND s.deleted_at IS NOT NULL
                       AND s.deleted_at &gt;= #{cutoff}
                   ) AS "restorable",
                   s.deletion_reason AS "deletionReason",
                   s.deleted_at AS "deletedAt",
                   s.created_at AS "createdAt",
                   s.restored_at AS "restoredAt",
                   s.restored_by AS "restoredBy",
                   s.restore_reason AS "restoreReason"
            FROM user_account_self_deletion s
            LEFT JOIN user_login_identity uli ON uli.user_id = s.user_id
            <where>
                <if test="userId != null">
                    AND s.user_id = #{userId}
                </if>
                <if test="emailPattern != null">
                    AND LOWER(s.email) LIKE #{emailPattern}
                </if>
                <if test="phonePattern != null">
                    AND s.phone LIKE #{phonePattern}
                </if>
                <choose>
                    <when test="scope == 'within7Days'">
                        AND s.is_deleted = FALSE
                        AND s.restored_at IS NULL
                        AND s.deleted_at IS NOT NULL
                        AND s.deleted_at &gt;= #{cutoff}
                    </when>
                    <when test="scope == 'expired'">
                        AND s.is_deleted = FALSE
                        AND s.restored_at IS NULL
                        AND s.deleted_at IS NOT NULL
                        AND s.deleted_at &lt; #{cutoff}
                    </when>
                    <when test="scope == 'deleted'">
                        AND s.is_deleted = TRUE
                    </when>
                    <when test="scope == 'restored'">
                        AND s.restored_at IS NOT NULL
                    </when>
                </choose>
            </where>
            ORDER BY s.deleted_at DESC NULLS LAST, s.created_at DESC, s.id DESC
            </script>
            """)
    List<Map<String, Object>> listSelfTerminations(@Param("scope") String scope,
                                                   @Param("userId") Long userId,
                                                   @Param("emailPattern") String emailPattern,
                                                   @Param("phonePattern") String phonePattern,
                                                   @Param("cutoff") OffsetDateTime cutoff);

    @Select("""
            SELECT s.id,
                   s.user_id AS "userId",
                   s.email,
                   s.phone,
                   uli.status,
                   s.is_deleted AS "deleted",
                   s.deletion_reason AS "deletionReason",
                   s.deleted_at AS "deletedAt",
                   s.created_at AS "createdAt",
                   s.restored_at AS "restoredAt",
                   s.restored_by AS "restoredBy",
                   s.restore_reason AS "restoreReason"
            FROM user_account_self_deletion s
            LEFT JOIN user_login_identity uli ON uli.user_id = s.user_id
            WHERE s.id = #{id}
            LIMIT 1
            """)
    Map<String, Object> findSelfTerminationById(@Param("id") Long id);

    @Update("""
            UPDATE user_login_identity
            SET status = 'ACTIVE',
                updated_at = #{updatedAt}
            WHERE user_id = #{userId}
              AND status = 'DISABLED'
            """)
    int restoreDisabledIdentity(@Param("userId") Long userId,
                                @Param("updatedAt") OffsetDateTime updatedAt);

    @Update("""
            UPDATE user_account_self_deletion
            SET restored_at = #{restoredAt},
                restored_by = #{restoredBy},
                restore_reason = #{restoreReason}
            WHERE id = #{id}
              AND is_deleted = FALSE
              AND restored_at IS NULL
              AND deleted_at IS NOT NULL
              AND deleted_at >= #{cutoff}
            """)
    int markSelfTerminationRestored(@Param("id") Long id,
                                    @Param("restoredAt") OffsetDateTime restoredAt,
                                    @Param("restoredBy") String restoredBy,
                                    @Param("restoreReason") String restoreReason,
                                    @Param("cutoff") OffsetDateTime cutoff);

    @Select("""
            <script>
            SELECT t.id,
                   t.user_id AS "userId",
                   t.email,
                   t.phone,
                   uli.status,
                   COALESCE(urp.current_score, 0) AS "currentScore",
                   urp.risk_level AS "riskLevel",
                   COALESCE(urp.lock_count, 0) AS "lockCount",
                   t.termination_reason AS "terminationReason",
                   t.terminated_at AS "terminatedAt",
                   t.created_at AS "createdAt"
            FROM user_risk_account_termination t
            LEFT JOIN user_login_identity uli ON uli.user_id = t.user_id
            LEFT JOIN user_risk_profile urp ON urp.user_id = t.user_id
            <where>
                <if test="userId != null">
                    AND t.user_id = #{userId}
                </if>
                <if test="emailPattern != null">
                    AND LOWER(t.email) LIKE #{emailPattern}
                </if>
                <if test="phonePattern != null">
                    AND t.phone LIKE #{phonePattern}
                </if>
            </where>
            ORDER BY t.terminated_at DESC, t.id DESC
            </script>
            """)
    List<Map<String, Object>> listRiskTerminations(@Param("userId") Long userId,
                                                   @Param("emailPattern") String emailPattern,
                                                   @Param("phonePattern") String phonePattern);

    @Select("""
            SELECT t.id,
                   t.user_id AS "userId",
                   t.email,
                   t.phone,
                   uli.status,
                   COALESCE(urp.current_score, 0) AS "currentScore",
                   urp.risk_level AS "riskLevel",
                   COALESCE(urp.lock_count, 0) AS "lockCount",
                   t.termination_reason AS "terminationReason",
                   t.terminated_at AS "terminatedAt",
                   t.created_at AS "createdAt"
            FROM user_risk_account_termination t
            LEFT JOIN user_login_identity uli ON uli.user_id = t.user_id
            LEFT JOIN user_risk_profile urp ON urp.user_id = t.user_id
            WHERE t.id = #{id}
            LIMIT 1
            """)
    Map<String, Object> findRiskTerminationById(@Param("id") Long id);
}
