package com.example.ShoppingSystem.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.OffsetDateTime;
import java.util.List;

@Mapper
public interface UserRiskAccountTerminationMapper {

    @Insert("""
            INSERT INTO user_risk_account_termination (
                id,
                user_id,
                email,
                email_hash,
                phone,
                phone_hash,
                termination_reason,
                terminated_at,
                created_at
            ) VALUES (
                #{id},
                #{userId},
                #{email},
                #{emailHash},
                #{phone},
                #{phoneHash},
                #{terminationReason},
                #{terminatedAt},
                #{createdAt}
            )
            ON CONFLICT (email_hash) DO UPDATE
            SET user_id = EXCLUDED.user_id,
                email = EXCLUDED.email,
                phone = EXCLUDED.phone,
                phone_hash = EXCLUDED.phone_hash,
                termination_reason = EXCLUDED.termination_reason,
                terminated_at = EXCLUDED.terminated_at
            """)
    int upsertRiskTermination(@Param("id") Long id,
                              @Param("userId") Long userId,
                              @Param("email") String email,
                              @Param("emailHash") String emailHash,
                              @Param("phone") String phone,
                              @Param("phoneHash") String phoneHash,
                              @Param("terminationReason") String terminationReason,
                              @Param("terminatedAt") OffsetDateTime terminatedAt,
                              @Param("createdAt") OffsetDateTime createdAt);

    @Select("""
            SELECT COUNT(1)
            FROM user_risk_account_termination
            WHERE email_hash IS NOT NULL
              AND btrim(email_hash) <> ''
            """)
    long countTerminatedEmailHashes();

    @Select("""
            SELECT email_hash
            FROM user_risk_account_termination
            WHERE email_hash IS NOT NULL
              AND btrim(email_hash) <> ''
            ORDER BY email_hash
            LIMIT #{limit}
            OFFSET #{offset}
            """)
    List<String> listTerminatedEmailHashes(@Param("limit") int limit,
                                           @Param("offset") long offset);

    @Select("""
            SELECT EXISTS (
                SELECT 1
                FROM user_risk_account_termination
                WHERE email_hash = #{emailHash}
                LIMIT 1
            )
            """)
    boolean existsByEmailHash(@Param("emailHash") String emailHash);

    @Select("""
            WITH target AS (
                SELECT uli.user_id
                FROM user_login_identity uli
                JOIN user_risk_account_termination termination
                  ON termination.user_id = uli.user_id
                WHERE uli.status = 'RISK_TERMINATED'
                  AND termination.terminated_at <= #{cutoff}
                ORDER BY termination.terminated_at, uli.user_id
                LIMIT #{limit}
                FOR UPDATE OF uli SKIP LOCKED
            ),
            deleted AS (
                DELETE FROM user_login_identity uli
                USING target
                WHERE uli.user_id = target.user_id
                RETURNING uli.user_id
            )
            SELECT COUNT(1)
            FROM deleted
            """)
    int deleteExpiredRiskTerminatedIdentities(@Param("cutoff") OffsetDateTime cutoff,
                                             @Param("limit") int limit);
}
