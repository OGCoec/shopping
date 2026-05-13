package com.example.ShoppingSystem.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface AdminDeviceRiskProfileMapper {

    @Select("""
            <script>
            SELECT encode(id, 'hex') AS "deviceId",
                   device_fingerprint AS "deviceFingerprint",
                   current_score AS "currentScore",
                   risk_level AS "riskLevel",
                   first_seen_at AS "firstSeenAt",
                   last_seen_at AS "lastSeenAt",
                   last_login_ip AS "lastLoginIp",
                   linked_user_count AS "linkedUserCount",
                   recent_distinct_ip_count AS "recentDistinctIpCount",
                   recent_ip_switch_count AS "recentIpSwitchCount",
                   last_penalty_reason AS "lastPenaltyReason",
                   last_penalty_score AS "lastPenaltyScore",
                   last_penalty_at AS "lastPenaltyAt"
            FROM device_risk_profile
            <where>
                <if test="riskLevel != null">
                    AND risk_level = #{riskLevel}
                </if>
                <if test="minScore != null">
                    AND current_score &gt;= #{minScore}
                </if>
                <if test="maxScoreExclusive != null">
                    AND current_score &lt; #{maxScoreExclusive}
                </if>
                <if test="queryPattern != null">
                    AND (encode(id, 'hex') LIKE #{queryPattern}
                         OR last_login_ip LIKE #{queryPattern})
                </if>
            </where>
            <choose>
                <when test="sort == 'recent_first'">
                    ORDER BY last_seen_at DESC NULLS LAST, id DESC
                </when>
                <otherwise>
                    ORDER BY current_score ASC, id DESC
                </otherwise>
            </choose>
            </script>
            """)
    List<Map<String, Object>> listDeviceRiskProfiles(@Param("riskLevel") String riskLevel,
                                                     @Param("minScore") Integer minScore,
                                                     @Param("maxScoreExclusive") Integer maxScoreExclusive,
                                                     @Param("queryPattern") String queryPattern,
                                                     @Param("sort") String sort);

    @Select("""
            SELECT encode(id, 'hex') AS "deviceId",
                   device_fingerprint AS "deviceFingerprint",
                   current_score AS "currentScore",
                   risk_level AS "riskLevel",
                   first_seen_at AS "firstSeenAt",
                   last_seen_at AS "lastSeenAt",
                   last_login_ip AS "lastLoginIp",
                   last_ip_seen_at AS "lastIpSeenAt",
                   linked_user_count AS "linkedUserCount",
                   recent_distinct_ip_count AS "recentDistinctIpCount",
                   recent_ip_switch_count AS "recentIpSwitchCount",
                   last_penalty_reason AS "lastPenaltyReason",
                   last_penalty_score AS "lastPenaltyScore",
                   last_penalty_at AS "lastPenaltyAt",
                   used_ip_list AS "usedIpList"
            FROM device_risk_profile
            WHERE id = decode(#{deviceIdHex}, 'hex')
            """)
    Map<String, Object> findDeviceById(@Param("deviceIdHex") String deviceIdHex);

    @Select("""
            SELECT score_before AS "scoreBefore",
                   penalty_score AS "penaltyScore",
                   score_after AS "scoreAfter",
                   reason,
                   created_at AS "createdAt"
            FROM device_risk_score_event
            WHERE device_id = decode(#{deviceIdHex}, 'hex')
            ORDER BY created_at DESC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> listScoreEventsByDeviceId(@Param("deviceIdHex") String deviceIdHex,
                                                        @Param("limit") int limit);
}
