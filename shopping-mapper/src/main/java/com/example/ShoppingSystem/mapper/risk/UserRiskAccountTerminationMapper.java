package com.example.ShoppingSystem.mapper.risk;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;

@Mapper
public interface UserRiskAccountTerminationMapper {

    int upsertRiskTermination(@Param("id") Long id,
                              @Param("userId") Long userId,
                              @Param("email") String email,
                              @Param("emailHash") String emailHash,
                              @Param("phone") String phone,
                              @Param("phoneHash") String phoneHash,
                              @Param("terminationReason") String terminationReason,
                              @Param("terminatedAt") OffsetDateTime terminatedAt,
                              @Param("createdAt") OffsetDateTime createdAt);

    long countTerminatedEmailHashes();

    List<String> listTerminatedEmailHashes(@Param("limit") int limit,
                                           @Param("offset") long offset);

    boolean existsByEmailHash(@Param("emailHash") String emailHash);

    int deleteExpiredRiskTerminatedIdentities(@Param("cutoff") OffsetDateTime cutoff,
                                             @Param("limit") int limit);
}
