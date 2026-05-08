package com.example.ShoppingSystem.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;

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
}
