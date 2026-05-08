package com.example.ShoppingSystem.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Mapper for user/device risk profile writeback.
 */
@Mapper
public interface RegisterRiskProfileMapper {

    @Select("""
            SELECT current_score
            FROM device_risk_profile
            WHERE device_fingerprint = #{deviceFingerprint}
            LIMIT 1
            """)
    Integer findDeviceRiskScoreByFingerprint(@Param("deviceFingerprint") String deviceFingerprint);

    @Select("""
            SELECT COUNT(1)
            FROM device_risk_profile
            WHERE current_score < #{scoreThreshold}
              AND device_fingerprint IS NOT NULL
              AND btrim(device_fingerprint) <> ''
            """)
    long countDeviceFingerprintsByCurrentScoreLessThan(@Param("scoreThreshold") int scoreThreshold);

    @Select("""
            SELECT device_fingerprint
            FROM device_risk_profile
            WHERE current_score < #{scoreThreshold}
              AND device_fingerprint IS NOT NULL
              AND btrim(device_fingerprint) <> ''
            ORDER BY device_fingerprint
            LIMIT #{limit}
            OFFSET #{offset}
            """)
    List<String> listDeviceFingerprintsByCurrentScoreLessThan(@Param("scoreThreshold") int scoreThreshold,
                                                              @Param("limit") int limit,
                                                              @Param("offset") long offset);

    @Select("""
            SELECT linked_user_count
            FROM device_risk_profile
            WHERE device_fingerprint = #{deviceFingerprint}
            LIMIT 1
            """)
    Integer findLinkedUserCountByFingerprint(@Param("deviceFingerprint") String deviceFingerprint);

    @Select("""
            SELECT COUNT(1)::INT
            FROM device_user_relation relation
            JOIN device_risk_profile profile ON profile.id = relation.device_id
            WHERE profile.device_fingerprint = #{deviceFingerprint}
            """)
    int countLinkedUsersByFingerprint(@Param("deviceFingerprint") String deviceFingerprint);

    @Select("""
            SELECT current_score AS "currentScore",
                   risk_level AS "riskLevel",
                   last_login_ip AS "lastLoginIp",
                   last_ip_seen_at AS "lastIpSeenAt",
                   last_penalized_ip_transition AS "lastPenalizedIpTransition",
                   last_penalty_at AS "lastPenaltyAt",
                   last_penalty_score AS "lastPenaltyScore",
                   last_penalty_reason AS "lastPenaltyReason",
                   linked_user_count AS "linkedUserCount",
                   linked_user_penalty_tier AS "linkedUserPenaltyTier"
            FROM device_risk_profile
            WHERE device_fingerprint = #{deviceFingerprint}
            LIMIT 1
            """)
    Map<String, Object> findDeviceRiskStateByFingerprint(@Param("deviceFingerprint") String deviceFingerprint);

    @Update("""
            UPDATE device_risk_profile
            SET current_score = CASE
                    WHEN #{penaltyScore} > 0
                        AND btrim(COALESCE(CAST(#{transition} AS TEXT), '')) <> ''
                        AND last_penalized_ip_transition IS DISTINCT FROM #{transition}
                    THEN GREATEST(0, current_score - GREATEST(0, #{penaltyScore}))
                    ELSE current_score
                END,
                risk_level = CASE
                    WHEN #{penaltyScore} > 0
                        AND btrim(COALESCE(CAST(#{transition} AS TEXT), '')) <> ''
                        AND last_penalized_ip_transition IS DISTINCT FROM #{transition}
                    THEN CASE
                        WHEN GREATEST(0, current_score - GREATEST(0, #{penaltyScore})) >= 8500 THEN 'L1'
                        WHEN GREATEST(0, current_score - GREATEST(0, #{penaltyScore})) >= 7500 THEN 'L2'
                        WHEN GREATEST(0, current_score - GREATEST(0, #{penaltyScore})) >= 6000 THEN 'L3'
                        WHEN GREATEST(0, current_score - GREATEST(0, #{penaltyScore})) >= 4800 THEN 'L4'
                        WHEN GREATEST(0, current_score - GREATEST(0, #{penaltyScore})) >= 3000 THEN 'L5'
                        ELSE 'L6'
                    END
                    ELSE risk_level
                END,
                last_seen_at = #{seenAt},
                last_login_ip = #{currentIp},
                last_ip_seen_at = #{seenAt},
                last_penalized_ip_transition = CASE
                    WHEN #{penaltyScore} > 0
                        AND btrim(COALESCE(CAST(#{transition} AS TEXT), '')) <> ''
                        AND last_penalized_ip_transition IS DISTINCT FROM #{transition}
                    THEN #{transition}
                    ELSE last_penalized_ip_transition
                END,
                last_penalty_at = CASE
                    WHEN #{penaltyScore} > 0
                        AND btrim(COALESCE(CAST(#{transition} AS TEXT), '')) <> ''
                        AND last_penalized_ip_transition IS DISTINCT FROM #{transition}
                    THEN CAST(#{seenAt} AS TIMESTAMPTZ)
                    ELSE last_penalty_at
                END,
                last_penalty_score = CASE
                    WHEN #{penaltyScore} > 0
                        AND btrim(COALESCE(CAST(#{transition} AS TEXT), '')) <> ''
                        AND last_penalized_ip_transition IS DISTINCT FROM #{transition}
                    THEN GREATEST(0, #{penaltyScore})
                    ELSE last_penalty_score
                END,
                last_penalty_reason = CASE
                    WHEN #{penaltyScore} > 0
                        AND btrim(COALESCE(CAST(#{transition} AS TEXT), '')) <> ''
                        AND last_penalized_ip_transition IS DISTINCT FROM #{transition}
                    THEN #{penaltyReason}
                    ELSE last_penalty_reason
                END,
                used_ip_list = CASE
                    WHEN CAST(#{currentIp} AS TEXT) IS NULL OR btrim(CAST(#{currentIp} AS TEXT)) = ''
                    THEN used_ip_list
                    WHEN used_ip_list @> jsonb_build_array(#{currentIp})
                    THEN used_ip_list
                    ELSE used_ip_list || jsonb_build_array(#{currentIp})
                END,
                recent_ip_switch_count = CASE
                    WHEN last_login_ip IS NOT NULL
                        AND btrim(last_login_ip) <> ''
                        AND CAST(#{currentIp} AS TEXT) IS NOT NULL
                        AND btrim(CAST(#{currentIp} AS TEXT)) <> ''
                        AND last_login_ip IS DISTINCT FROM #{currentIp}
                    THEN recent_ip_switch_count + 1
                    ELSE recent_ip_switch_count
                END,
                updated_at = #{seenAt}
            WHERE device_fingerprint = #{deviceFingerprint}
            """)
    int applyDeviceRiskIpChangePenalty(@Param("deviceFingerprint") String deviceFingerprint,
                                       @Param("currentIp") String currentIp,
                                       @Param("seenAt") OffsetDateTime seenAt,
                                       @Param("transition") String transition,
                                       @Param("penaltyScore") int penaltyScore,
                                       @Param("penaltyReason") String penaltyReason);

    @Update("""
            UPDATE device_risk_profile
            SET current_score = GREATEST(0, current_score - GREATEST(0, #{penaltyScore})),
                risk_level = CASE
                    WHEN GREATEST(0, current_score - GREATEST(0, #{penaltyScore})) >= 8500 THEN 'L1'
                    WHEN GREATEST(0, current_score - GREATEST(0, #{penaltyScore})) >= 7500 THEN 'L2'
                    WHEN GREATEST(0, current_score - GREATEST(0, #{penaltyScore})) >= 6000 THEN 'L3'
                    WHEN GREATEST(0, current_score - GREATEST(0, #{penaltyScore})) >= 4800 THEN 'L4'
                    WHEN GREATEST(0, current_score - GREATEST(0, #{penaltyScore})) >= 3000 THEN 'L5'
                    ELSE 'L6'
                END,
                linked_user_penalty_tier = #{targetPenaltyTier},
                last_linked_user_penalty_at = CAST(#{penalizedAt} AS TIMESTAMPTZ),
                last_linked_user_penalty_score = GREATEST(0, #{penaltyScore}),
                last_linked_user_penalty_reason = #{penaltyReason},
                updated_at = #{penalizedAt}
            WHERE device_fingerprint = #{deviceFingerprint}
              AND linked_user_count >= #{minimumLinkedUserCount}
              AND linked_user_penalty_tier = #{previousPenaltyTier}
              AND #{targetPenaltyTier} > #{previousPenaltyTier}
              AND #{penaltyScore} > 0
            """)
    int applyDeviceLinkedUserCountPenalty(@Param("deviceFingerprint") String deviceFingerprint,
                                          @Param("previousPenaltyTier") int previousPenaltyTier,
                                          @Param("targetPenaltyTier") int targetPenaltyTier,
                                          @Param("minimumLinkedUserCount") int minimumLinkedUserCount,
                                          @Param("penaltyScore") int penaltyScore,
                                          @Param("penaltyReason") String penaltyReason,
                                          @Param("penalizedAt") OffsetDateTime penalizedAt);

    @Select("""
            UPDATE device_risk_profile
            SET current_score = GREATEST(0, current_score - GREATEST(0, #{penaltyScore})),
                risk_level = CASE
                    WHEN GREATEST(0, current_score - GREATEST(0, #{penaltyScore})) >= 8500 THEN 'L1'
                    WHEN GREATEST(0, current_score - GREATEST(0, #{penaltyScore})) >= 7500 THEN 'L2'
                    WHEN GREATEST(0, current_score - GREATEST(0, #{penaltyScore})) >= 6000 THEN 'L3'
                    WHEN GREATEST(0, current_score - GREATEST(0, #{penaltyScore})) >= 4800 THEN 'L4'
                    WHEN GREATEST(0, current_score - GREATEST(0, #{penaltyScore})) >= 3000 THEN 'L5'
                    ELSE 'L6'
                END,
                last_seen_at = #{penalizedAt},
                last_login_ip = CASE
                    WHEN btrim(COALESCE(CAST(#{clientIp} AS TEXT), '')) = ''
                    THEN last_login_ip
                    ELSE #{clientIp}
                END,
                last_ip_seen_at = CASE
                    WHEN btrim(COALESCE(CAST(#{clientIp} AS TEXT), '')) = ''
                    THEN last_ip_seen_at
                    ELSE CAST(#{penalizedAt} AS TIMESTAMPTZ)
                END,
                last_penalty_at = CAST(#{penalizedAt} AS TIMESTAMPTZ),
                last_penalty_score = GREATEST(0, #{penaltyScore}),
                last_penalty_reason = #{penaltyReason},
                updated_at = #{penalizedAt}
            WHERE device_fingerprint = #{deviceFingerprint}
              AND #{penaltyScore} > 0
            RETURNING current_score
            """)
    Integer applyDeviceAutomationPenalty(@Param("deviceFingerprint") String deviceFingerprint,
                                         @Param("clientIp") String clientIp,
                                         @Param("penaltyScore") int penaltyScore,
                                         @Param("penaltyReason") String penaltyReason,
                                         @Param("penalizedAt") OffsetDateTime penalizedAt);

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
                #{currentScore},
                #{currentScore},
                0,
                #{currentScore},
                #{riskLevel},
                #{lastLoginAt},
                #{lastLoginIp},
                #{lastDeviceFingerprint},
                #{updatedAt}
            )
            ON CONFLICT (user_id) DO UPDATE
            SET register_base_score = CASE
                    WHEN user_risk_profile.register_base_score > 0
                    THEN user_risk_profile.register_base_score
                    ELSE EXCLUDED.register_base_score
                END,
                current_env_score = EXCLUDED.current_env_score,
                current_score = EXCLUDED.current_score,
                risk_level = EXCLUDED.risk_level,
                last_login_at = EXCLUDED.last_login_at,
                last_login_ip = EXCLUDED.last_login_ip,
                last_device_fingerprint = EXCLUDED.last_device_fingerprint,
                updated_at = EXCLUDED.updated_at
            """)
    int upsertUserRiskProfile(@Param("userId") Long userId,
                              @Param("currentScore") int currentScore,
                              @Param("riskLevel") String riskLevel,
                              @Param("lastLoginAt") OffsetDateTime lastLoginAt,
                              @Param("lastLoginIp") String lastLoginIp,
                              @Param("lastDeviceFingerprint") String lastDeviceFingerprint,
                              @Param("updatedAt") OffsetDateTime updatedAt);

    @Select("""
            INSERT INTO device_risk_profile (
                id,
                device_fingerprint,
                current_score,
                risk_level,
                first_seen_at,
                last_seen_at,
                last_login_ip,
                last_ip_seen_at,
                last_penalized_ip_transition,
                last_penalty_at,
                last_penalty_score,
                last_penalty_reason,
                used_ip_list,
                linked_user_count,
                recent_distinct_ip_count,
                recent_ip_switch_count,
                updated_at
            ) VALUES (
                decode(#{idHex}, 'hex'),
                #{deviceFingerprint},
                #{currentScore},
                #{riskLevel},
                #{firstSeenAt},
                #{lastSeenAt},
                #{lastLoginIp},
                #{lastIpSeenAt},
                NULL,
                CASE
                    WHEN #{lastPenaltyScore} > 0
                    THEN CAST(#{lastPenaltyAt} AS TIMESTAMPTZ)
                    ELSE NULL::TIMESTAMPTZ
                END,
                GREATEST(0, #{lastPenaltyScore}),
                CASE WHEN #{lastPenaltyScore} > 0 THEN #{lastPenaltyReason} ELSE NULL END,
                CASE
                    WHEN #{lastLoginIp} IS NULL OR btrim(#{lastLoginIp}) = ''
                    THEN '[]'::jsonb
                    ELSE jsonb_build_array(#{lastLoginIp})
                END,
                0,
                0,
                0,
                #{updatedAt}
            )
            ON CONFLICT (device_fingerprint) DO UPDATE
            SET current_score = CASE
                    WHEN #{lastPenaltyScore} > 0
                        AND device_risk_profile.last_login_ip IS NOT NULL
                        AND btrim(device_risk_profile.last_login_ip) <> ''
                        AND EXCLUDED.last_login_ip IS NOT NULL
                        AND btrim(EXCLUDED.last_login_ip) <> ''
                        AND device_risk_profile.last_login_ip IS DISTINCT FROM EXCLUDED.last_login_ip
                        AND device_risk_profile.last_penalized_ip_transition IS DISTINCT FROM
                            (device_risk_profile.last_login_ip || '->' || EXCLUDED.last_login_ip)
                    THEN GREATEST(0, device_risk_profile.current_score - #{lastPenaltyScore})
                    ELSE device_risk_profile.current_score
                END,
                risk_level = CASE
                    WHEN #{lastPenaltyScore} > 0
                        AND device_risk_profile.last_login_ip IS NOT NULL
                        AND btrim(device_risk_profile.last_login_ip) <> ''
                        AND EXCLUDED.last_login_ip IS NOT NULL
                        AND btrim(EXCLUDED.last_login_ip) <> ''
                        AND device_risk_profile.last_login_ip IS DISTINCT FROM EXCLUDED.last_login_ip
                        AND device_risk_profile.last_penalized_ip_transition IS DISTINCT FROM
                            (device_risk_profile.last_login_ip || '->' || EXCLUDED.last_login_ip)
                    THEN CASE
                        WHEN GREATEST(0, device_risk_profile.current_score - #{lastPenaltyScore}) >= 8500 THEN 'L1'
                        WHEN GREATEST(0, device_risk_profile.current_score - #{lastPenaltyScore}) >= 7500 THEN 'L2'
                        WHEN GREATEST(0, device_risk_profile.current_score - #{lastPenaltyScore}) >= 6000 THEN 'L3'
                        WHEN GREATEST(0, device_risk_profile.current_score - #{lastPenaltyScore}) >= 4800 THEN 'L4'
                        WHEN GREATEST(0, device_risk_profile.current_score - #{lastPenaltyScore}) >= 3000 THEN 'L5'
                        ELSE 'L6'
                    END
                    ELSE device_risk_profile.risk_level
                END,
                last_seen_at = EXCLUDED.last_seen_at,
                last_login_ip = EXCLUDED.last_login_ip,
                last_ip_seen_at = EXCLUDED.last_ip_seen_at,
                last_penalized_ip_transition = CASE
                    WHEN #{lastPenaltyScore} > 0
                        AND device_risk_profile.last_login_ip IS NOT NULL
                        AND btrim(device_risk_profile.last_login_ip) <> ''
                        AND EXCLUDED.last_login_ip IS NOT NULL
                        AND btrim(EXCLUDED.last_login_ip) <> ''
                        AND device_risk_profile.last_login_ip IS DISTINCT FROM EXCLUDED.last_login_ip
                        AND device_risk_profile.last_penalized_ip_transition IS DISTINCT FROM
                            (device_risk_profile.last_login_ip || '->' || EXCLUDED.last_login_ip)
                    THEN device_risk_profile.last_login_ip || '->' || EXCLUDED.last_login_ip
                    ELSE device_risk_profile.last_penalized_ip_transition
                END,
                last_penalty_at = CASE
                    WHEN #{lastPenaltyScore} > 0
                        AND device_risk_profile.last_login_ip IS NOT NULL
                        AND btrim(device_risk_profile.last_login_ip) <> ''
                        AND EXCLUDED.last_login_ip IS NOT NULL
                        AND btrim(EXCLUDED.last_login_ip) <> ''
                        AND device_risk_profile.last_login_ip IS DISTINCT FROM EXCLUDED.last_login_ip
                        AND device_risk_profile.last_penalized_ip_transition IS DISTINCT FROM
                            (device_risk_profile.last_login_ip || '->' || EXCLUDED.last_login_ip)
                    THEN CAST(#{lastPenaltyAt} AS TIMESTAMPTZ)
                    ELSE device_risk_profile.last_penalty_at
                END,
                last_penalty_score = CASE
                    WHEN #{lastPenaltyScore} > 0
                        AND device_risk_profile.last_login_ip IS NOT NULL
                        AND btrim(device_risk_profile.last_login_ip) <> ''
                        AND EXCLUDED.last_login_ip IS NOT NULL
                        AND btrim(EXCLUDED.last_login_ip) <> ''
                        AND device_risk_profile.last_login_ip IS DISTINCT FROM EXCLUDED.last_login_ip
                        AND device_risk_profile.last_penalized_ip_transition IS DISTINCT FROM
                            (device_risk_profile.last_login_ip || '->' || EXCLUDED.last_login_ip)
                    THEN #{lastPenaltyScore}
                    ELSE device_risk_profile.last_penalty_score
                END,
                last_penalty_reason = CASE
                    WHEN #{lastPenaltyScore} > 0
                        AND device_risk_profile.last_login_ip IS NOT NULL
                        AND btrim(device_risk_profile.last_login_ip) <> ''
                        AND EXCLUDED.last_login_ip IS NOT NULL
                        AND btrim(EXCLUDED.last_login_ip) <> ''
                        AND device_risk_profile.last_login_ip IS DISTINCT FROM EXCLUDED.last_login_ip
                        AND device_risk_profile.last_penalized_ip_transition IS DISTINCT FROM
                            (device_risk_profile.last_login_ip || '->' || EXCLUDED.last_login_ip)
                    THEN #{lastPenaltyReason}
                    ELSE device_risk_profile.last_penalty_reason
                END,
                used_ip_list = CASE
                    WHEN EXCLUDED.last_login_ip IS NULL OR btrim(EXCLUDED.last_login_ip) = ''
                    THEN device_risk_profile.used_ip_list
                    WHEN device_risk_profile.used_ip_list @> jsonb_build_array(EXCLUDED.last_login_ip)
                    THEN device_risk_profile.used_ip_list
                    ELSE device_risk_profile.used_ip_list || jsonb_build_array(EXCLUDED.last_login_ip)
                END,
                recent_ip_switch_count = CASE
                    WHEN device_risk_profile.last_login_ip IS NOT NULL
                        AND EXCLUDED.last_login_ip IS NOT NULL
                        AND device_risk_profile.last_login_ip IS DISTINCT FROM EXCLUDED.last_login_ip
                    THEN device_risk_profile.recent_ip_switch_count + 1
                    ELSE device_risk_profile.recent_ip_switch_count
                END,
                updated_at = EXCLUDED.updated_at
            RETURNING encode(id, 'hex') AS id
            """)
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

    @Update("""
            INSERT INTO device_user_relation (
                id,
                device_id,
                user_id,
                first_seen_at,
                last_seen_at,
                success_count,
                fail_count
            ) VALUES (
                decode(#{idHex}, 'hex'),
                decode(#{deviceIdHex}, 'hex'),
                #{userId},
                #{seenAt},
                #{seenAt},
                1,
                0
            )
            ON CONFLICT (device_id, user_id) DO UPDATE
            SET last_seen_at = EXCLUDED.last_seen_at,
                success_count = device_user_relation.success_count + 1
            """)
    int upsertDeviceUserRelationSuccess(@Param("idHex") String idHex,
                                        @Param("deviceIdHex") String deviceIdHex,
                                        @Param("userId") Long userId,
                                        @Param("seenAt") OffsetDateTime seenAt);

    @Update("""
            INSERT INTO device_user_relation (
                id,
                device_id,
                user_id,
                first_seen_at,
                last_seen_at,
                success_count,
                fail_count
            ) VALUES (
                decode(#{idHex}, 'hex'),
                decode(#{deviceIdHex}, 'hex'),
                #{userId},
                #{seenAt},
                #{seenAt},
                0,
                1
            )
            ON CONFLICT (device_id, user_id) DO UPDATE
            SET last_seen_at = EXCLUDED.last_seen_at,
                fail_count = device_user_relation.fail_count + 1
            """)
    int upsertDeviceUserRelationFailure(@Param("idHex") String idHex,
                                        @Param("deviceIdHex") String deviceIdHex,
                                        @Param("userId") Long userId,
                                        @Param("seenAt") OffsetDateTime seenAt);

    @Update("""
            UPDATE device_risk_profile
            SET linked_user_count = (
                    SELECT COUNT(1)
                    FROM device_user_relation
                    WHERE device_id = decode(#{deviceIdHex}, 'hex')
                ),
                updated_at = #{updatedAt}
            WHERE id = decode(#{deviceIdHex}, 'hex')
            """)
    int refreshDeviceLinkedUserCount(@Param("deviceIdHex") String deviceIdHex,
                                     @Param("updatedAt") OffsetDateTime updatedAt);
}
