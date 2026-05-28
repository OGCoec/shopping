package com.example.ShoppingSystem.mapper.user;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Mapper
public interface UserAccountSelfDeletionMapper {

    int upsertPendingSelfDeletion(@Param("id") Long id,
                                  @Param("userId") Long userId,
                                  @Param("email") String email,
                                  @Param("emailHash") String emailHash,
                                  @Param("phone") String phone,
                                  @Param("phoneHash") String phoneHash,
                                  @Param("deletionReason") String deletionReason,
                                  @Param("deletedAt") OffsetDateTime deletedAt,
                                  @Param("createdAt") OffsetDateTime createdAt);

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
