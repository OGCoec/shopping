package com.example.ShoppingSystem.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.OffsetDateTime;
import java.util.Map;

@Mapper
public interface UserRiskProfileMapper {

    @Select("""
            SELECT current_score AS "currentScore",
                   risk_level AS "riskLevel",
                   current_env_score AS "currentEnvScore",
                   behavior_score_delta AS "behaviorScoreDelta",
                   lock_count AS "lockCount",
                   lock_until AS "lockUntil",
                   lock_reason AS "lockReason",
                   risk_recovery_started_at AS "riskRecoveryStartedAt",
                   last_risk_penalty_at AS "lastRiskPenaltyAt",
                   last_login_at AS "lastLoginAt",
                   last_login_ip AS "lastLoginIp",
                   last_device_fingerprint AS "lastDeviceFingerprint"
            FROM user_risk_profile
            WHERE user_id = #{userId}
            LIMIT 1
            """)
    Map<String, Object> findUserRiskStateByUserId(@Param("userId") Long userId);

    @Update("""
            INSERT INTO user_risk_profile (
                user_id,
                register_base_score,
                current_env_score,
                behavior_score_delta,
                current_score,
                risk_level,
                lock_count,
                last_locked_at,
                lock_until,
                lock_reason,
                risk_recovery_started_at,
                last_risk_penalty_at,
                updated_at
            ) VALUES (
                #{userId},
                #{currentEnvScore},
                #{currentEnvScore},
                #{behaviorScoreDelta},
                #{currentScore},
                #{riskLevel},
                #{lockCount},
                #{lockedAt},
                #{lockUntil},
                #{lockReason},
                NULL,
                #{lockedAt},
                #{updatedAt}
            )
            ON CONFLICT (user_id) DO UPDATE
            SET current_env_score = EXCLUDED.current_env_score,
                behavior_score_delta = EXCLUDED.behavior_score_delta,
                current_score = EXCLUDED.current_score,
                risk_level = EXCLUDED.risk_level,
                lock_count = EXCLUDED.lock_count,
                last_locked_at = EXCLUDED.last_locked_at,
                lock_until = EXCLUDED.lock_until,
                lock_reason = EXCLUDED.lock_reason,
                risk_recovery_started_at = NULL,
                last_risk_penalty_at = EXCLUDED.last_risk_penalty_at,
                updated_at = EXCLUDED.updated_at
            """)
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
                #{ip},
                #{deviceFingerprint},
                CAST(#{metadataJson} AS jsonb),
                #{createdAt}
            )
            """)
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

    @Update("""
            INSERT INTO user_risk_profile (
                user_id,
                register_base_score,
                current_env_score,
                behavior_score_delta,
                current_score,
                risk_level,
                last_login_at,
                last_login_ip,
                last_device_fingerprint,
                updated_at
            ) VALUES (
                #{userId},
                #{defaultScore},
                #{defaultScore},
                0,
                #{defaultScore},
                #{defaultRiskLevel},
                #{seenAt},
                #{currentIp},
                #{deviceFingerprint},
                #{updatedAt}
            )
            ON CONFLICT (user_id) DO UPDATE
            SET last_login_at = #{seenAt},
                last_login_ip = CASE
                    WHEN btrim(COALESCE(CAST(#{currentIp} AS TEXT), '')) = ''
                    THEN user_risk_profile.last_login_ip
                    ELSE #{currentIp}
                END,
                last_device_fingerprint = CASE
                    WHEN btrim(COALESCE(CAST(#{deviceFingerprint} AS TEXT), '')) = ''
                    THEN user_risk_profile.last_device_fingerprint
                    ELSE #{deviceFingerprint}
                END,
                updated_at = #{updatedAt}
            """)
    int touchUserNetworkState(@Param("userId") Long userId,
                              @Param("defaultScore") int defaultScore,
                              @Param("defaultRiskLevel") String defaultRiskLevel,
                              @Param("seenAt") OffsetDateTime seenAt,
                              @Param("currentIp") String currentIp,
                              @Param("deviceFingerprint") String deviceFingerprint,
                              @Param("updatedAt") OffsetDateTime updatedAt);

    @Update("""
            UPDATE user_risk_profile
            SET risk_recovery_started_at = #{startedAt},
                lock_until = NULL,
                updated_at = #{startedAt}
            WHERE user_id = #{userId}
              AND lock_count > 0
            """)
    int markRiskRecoveryStarted(@Param("userId") Long userId,
                                @Param("startedAt") OffsetDateTime startedAt);

    @Select("""
            WITH target AS (
                SELECT urp.user_id,
                       urp.current_score AS score_before,
                       urp.risk_level AS risk_level_before
                FROM user_risk_profile urp
                JOIN user_login_identity uli ON uli.user_id = urp.user_id
                WHERE uli.status = 'ACTIVE'
                  AND urp.lock_count = #{lockCount}
                  AND (urp.lock_reason = 'AUTH_FAIL_LOCK_30M' OR urp.lock_reason IS NULL)
                  AND urp.risk_recovery_started_at IS NOT NULL
                  AND urp.risk_recovery_started_at <= #{cutoff}
                  AND (
                      urp.last_risk_penalty_at IS NULL
                      OR urp.last_risk_penalty_at <= urp.risk_recovery_started_at
                  )
                ORDER BY urp.risk_recovery_started_at, urp.user_id
                LIMIT #{limit}
                FOR UPDATE OF urp SKIP LOCKED
            ),
            updated AS (
                UPDATE user_risk_profile urp
                SET lock_count = GREATEST(0, urp.lock_count - 1),
                    behavior_score_delta = urp.behavior_score_delta + #{scoreBonus},
                    current_score = LEAST(10000, urp.current_score + #{scoreBonus}),
                    risk_level = CASE
                        WHEN LEAST(10000, urp.current_score + #{scoreBonus}) >= 8500 THEN 'L1'
                        WHEN LEAST(10000, urp.current_score + #{scoreBonus}) >= 7500 THEN 'L2'
                        WHEN LEAST(10000, urp.current_score + #{scoreBonus}) >= 6000 THEN 'L3'
                        WHEN LEAST(10000, urp.current_score + #{scoreBonus}) >= 4800 THEN 'L4'
                        WHEN LEAST(10000, urp.current_score + #{scoreBonus}) >= 3000 THEN 'L5'
                        ELSE 'L6'
                    END,
                    risk_recovery_started_at = CASE
                        WHEN urp.lock_count - 1 <= 0 THEN NULL
                        ELSE #{now}
                    END,
                    lock_reason = CASE
                        WHEN urp.lock_count - 1 <= 0 THEN NULL
                        ELSE urp.lock_reason
                    END,
                    updated_at = #{now}
                FROM target
                WHERE urp.user_id = target.user_id
                RETURNING urp.user_id,
                          target.score_before,
                          LEAST(10000, target.score_before + #{scoreBonus}) AS score_after,
                          target.risk_level_before,
                          urp.risk_level AS risk_level_after
            ),
            numbered AS (
                SELECT updated.*,
                       ROW_NUMBER() OVER (ORDER BY updated.user_id)::BIGINT AS event_sequence
                FROM updated
            ),
            event_insert AS (
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
                )
                SELECT (
                           (((EXTRACT(EPOCH FROM CAST(#{now} AS TIMESTAMPTZ)) * 1000)::BIGINT - 1767225600000) << 22)
                           | (31::BIGINT << 17)
                           | (CAST(#{lockCount} AS BIGINT) << 12)
                           | numbered.event_sequence
                       ) AS id,
                       numbered.user_id,
                       'AUTH_LOCK_RECOVERY',
                       numbered.score_before,
                       numbered.score_after - numbered.score_before,
                       numbered.score_after,
                       numbered.risk_level_before,
                       numbered.risk_level_after,
                       'AUTH_LOCK_RECOVERY',
                       NULL,
                       NULL,
                       jsonb_build_object(
                           'lockCountBefore', #{lockCount},
                           'lockCountAfter', GREATEST(0, #{lockCount} - 1),
                           'stableDays', #{stableDays},
                           'scoreBonus', #{scoreBonus}
                       ),
                       #{now}
                FROM numbered
                RETURNING 1
            )
            SELECT COUNT(1)
            FROM updated
            """)
    int recoverStableUnlockedUsers(@Param("lockCount") int lockCount,
                                   @Param("cutoff") OffsetDateTime cutoff,
                                   @Param("scoreBonus") int scoreBonus,
                                   @Param("stableDays") int stableDays,
                                   @Param("now") OffsetDateTime now,
                                   @Param("limit") int limit);

    @Select("""
            WITH target AS (
                SELECT urp.user_id,
                       urp.current_score AS score_before,
                       urp.risk_level AS risk_level_before
                FROM user_risk_profile urp
                JOIN user_login_identity uli ON uli.user_id = urp.user_id
                WHERE uli.status = 'ACTIVE'
                  AND urp.lock_count = #{lockCount}
                  AND urp.lock_reason = #{lockReason}
                  AND urp.risk_recovery_started_at IS NOT NULL
                  AND urp.risk_recovery_started_at <= #{cutoff}
                  AND (
                      urp.last_risk_penalty_at IS NULL
                      OR urp.last_risk_penalty_at <= urp.risk_recovery_started_at
                  )
                ORDER BY urp.risk_recovery_started_at, urp.user_id
                LIMIT #{limit}
                FOR UPDATE OF urp SKIP LOCKED
            ),
            updated AS (
                UPDATE user_risk_profile urp
                SET lock_count = GREATEST(0, urp.lock_count - 1),
                    behavior_score_delta = urp.behavior_score_delta + #{scoreBonus},
                    current_score = LEAST(10000, urp.current_score + #{scoreBonus}),
                    risk_level = CASE
                        WHEN LEAST(10000, urp.current_score + #{scoreBonus}) >= 8500 THEN 'L1'
                        WHEN LEAST(10000, urp.current_score + #{scoreBonus}) >= 7500 THEN 'L2'
                        WHEN LEAST(10000, urp.current_score + #{scoreBonus}) >= 6000 THEN 'L3'
                        WHEN LEAST(10000, urp.current_score + #{scoreBonus}) >= 4800 THEN 'L4'
                        WHEN LEAST(10000, urp.current_score + #{scoreBonus}) >= 3000 THEN 'L5'
                        ELSE 'L6'
                    END,
                    risk_recovery_started_at = CASE
                        WHEN urp.lock_count - 1 <= 0 THEN NULL
                        ELSE #{now}
                    END,
                    lock_reason = CASE
                        WHEN urp.lock_count - 1 <= 0 THEN NULL
                        ELSE urp.lock_reason
                    END,
                    updated_at = #{now}
                FROM target
                WHERE urp.user_id = target.user_id
                RETURNING urp.user_id,
                          target.score_before,
                          LEAST(10000, target.score_before + #{scoreBonus}) AS score_after,
                          target.risk_level_before,
                          urp.risk_level AS risk_level_after
            ),
            numbered AS (
                SELECT updated.*,
                       ROW_NUMBER() OVER (ORDER BY updated.user_id)::BIGINT AS event_sequence
                FROM updated
            ),
            event_insert AS (
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
                )
                SELECT (
                           (((EXTRACT(EPOCH FROM CAST(#{now} AS TIMESTAMPTZ)) * 1000)::BIGINT - 1767225600000) << 22)
                           | (30::BIGINT << 17)
                           | (CAST(#{lockCount} AS BIGINT) << 12)
                           | numbered.event_sequence
                       ) AS id,
                       numbered.user_id,
                       #{eventType},
                       numbered.score_before,
                       numbered.score_after - numbered.score_before,
                       numbered.score_after,
                       numbered.risk_level_before,
                       numbered.risk_level_after,
                       #{eventReason},
                       NULL,
                       NULL,
                       jsonb_build_object(
                           'lockReason', #{lockReason},
                           'lockCountBefore', #{lockCount},
                           'lockCountAfter', GREATEST(0, #{lockCount} - 1),
                           'stableDays', #{stableDays},
                           'scoreBonus', #{scoreBonus}
                       ),
                       #{now}
                FROM numbered
                RETURNING 1
            )
            SELECT COUNT(1)
            FROM updated
            """)
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
