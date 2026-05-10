package com.example.ShoppingSystem.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Mapper
public interface UserAccountSelfDeletionMapper {

    @Insert("""
            INSERT INTO user_account_self_deletion (
                id,
                user_id,
                email,
                email_hash,
                phone,
                phone_hash,
                is_deleted,
                deletion_reason,
                deleted_at,
                created_at
            ) VALUES (
                #{id},
                #{userId},
                #{email},
                #{emailHash},
                #{phone},
                #{phoneHash},
                FALSE,
                #{deletionReason},
                #{deletedAt},
                #{createdAt}
            )
            ON CONFLICT (email_hash) DO UPDATE
            SET user_id = EXCLUDED.user_id,
                email = EXCLUDED.email,
                phone = EXCLUDED.phone,
                phone_hash = EXCLUDED.phone_hash,
                is_deleted = FALSE,
                deletion_reason = EXCLUDED.deletion_reason,
                deleted_at = EXCLUDED.deleted_at,
                created_at = EXCLUDED.created_at
            """)
    int upsertPendingSelfDeletion(@Param("id") Long id,
                                  @Param("userId") Long userId,
                                  @Param("email") String email,
                                  @Param("emailHash") String emailHash,
                                  @Param("phone") String phone,
                                  @Param("phoneHash") String phoneHash,
                                  @Param("deletionReason") String deletionReason,
                                  @Param("deletedAt") OffsetDateTime deletedAt,
                                  @Param("createdAt") OffsetDateTime createdAt);

    @Select("""
            WITH due AS (
                SELECT id,
                       user_id,
                       email,
                       phone
                FROM user_account_self_deletion
                WHERE is_deleted = FALSE
                  AND deleted_at IS NOT NULL
                  AND deleted_at <= #{cutoff}
                ORDER BY deleted_at
                LIMIT #{limit}
                FOR UPDATE SKIP LOCKED
            ),
            deleted_profile AS (
                DELETE FROM user_profile p
                USING due d
                WHERE p.id = d.user_id
                RETURNING p.id
            ),
            deleted_identity AS (
                DELETE FROM user_login_identity i
                USING due d
                WHERE i.user_id = d.user_id
                RETURNING i.user_id
            ),
            marked AS (
                UPDATE user_account_self_deletion s
                SET is_deleted = TRUE
                FROM due d
                WHERE s.id = d.id
                  AND s.is_deleted = FALSE
                RETURNING d.user_id AS "userId",
                          d.email AS "email",
                          d.phone AS "phone"
            )
            SELECT "userId",
                   email,
                   phone
            FROM marked
            """)
    List<CleanupMailTarget> completeDueSelfDeletionsBatch(@Param("cutoff") OffsetDateTime cutoff,
                                                          @Param("limit") int limit);

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    class CleanupMailTarget {
        private Long userId;
        private String email;
        private String phone;
    }
}
