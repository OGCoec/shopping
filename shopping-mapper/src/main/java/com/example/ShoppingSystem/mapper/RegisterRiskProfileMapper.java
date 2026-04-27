package com.example.ShoppingSystem.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.OffsetDateTime;

/**
 * Mapper for user/device risk profile writeback during register completion.
 */
@Mapper
public interface RegisterRiskProfileMapper {

    @Update("""
            INSERT INTO user_risk_profile (
                user_id,
                current_score,
                risk_level,
                last_login_at,
                last_login_ip,
                last_device_fingerprint,
                updated_at
            ) VALUES (
                #{userId},
                #{currentScore},
                #{riskLevel},
                #{lastLoginAt},
                #{lastLoginIp},
                #{lastDeviceFingerprint},
                #{updatedAt}
            )
            ON CONFLICT (user_id) DO UPDATE
            SET current_score = EXCLUDED.current_score,
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

    @Update("""
            INSERT INTO device_risk_profile (
                device_fingerprint,
                current_score,
                risk_level,
                first_seen_at,
                last_seen_at,
                last_login_ip,
                linked_user_count,
                recent_distinct_ip_count,
                recent_ip_switch_count,
                updated_at
            ) VALUES (
                #{deviceFingerprint},
                #{currentScore},
                #{riskLevel},
                #{firstSeenAt},
                #{lastSeenAt},
                #{lastLoginIp},
                0,
                0,
                0,
                #{updatedAt}
            )
            ON CONFLICT (device_fingerprint) DO UPDATE
            SET current_score = EXCLUDED.current_score,
                risk_level = EXCLUDED.risk_level,
                last_seen_at = EXCLUDED.last_seen_at,
                last_login_ip = EXCLUDED.last_login_ip,
                updated_at = EXCLUDED.updated_at
            """)
    int upsertDeviceRiskProfile(@Param("deviceFingerprint") String deviceFingerprint,
                                @Param("currentScore") int currentScore,
                                @Param("riskLevel") String riskLevel,
                                @Param("firstSeenAt") OffsetDateTime firstSeenAt,
                                @Param("lastSeenAt") OffsetDateTime lastSeenAt,
                                @Param("lastLoginIp") String lastLoginIp,
                                @Param("updatedAt") OffsetDateTime updatedAt);

    @Update("""
            INSERT INTO device_user_relation (
                id,
                device_fingerprint,
                user_id,
                first_seen_at,
                last_seen_at,
                success_count,
                fail_count
            ) VALUES (
                #{id},
                #{deviceFingerprint},
                #{userId},
                #{seenAt},
                #{seenAt},
                1,
                0
            )
            ON CONFLICT (device_fingerprint, user_id) DO UPDATE
            SET last_seen_at = EXCLUDED.last_seen_at,
                success_count = device_user_relation.success_count + 1
            """)
    int upsertDeviceUserRelation(@Param("id") byte[] id,
                                 @Param("deviceFingerprint") String deviceFingerprint,
                                 @Param("userId") Long userId,
                                 @Param("seenAt") OffsetDateTime seenAt);

    @Update("""
            UPDATE device_risk_profile
            SET linked_user_count = (
                    SELECT COUNT(1)
                    FROM device_user_relation
                    WHERE device_fingerprint = #{deviceFingerprint}
                ),
                updated_at = #{updatedAt}
            WHERE device_fingerprint = #{deviceFingerprint}
            """)
    int refreshDeviceLinkedUserCount(@Param("deviceFingerprint") String deviceFingerprint,
                                     @Param("updatedAt") OffsetDateTime updatedAt);
}
