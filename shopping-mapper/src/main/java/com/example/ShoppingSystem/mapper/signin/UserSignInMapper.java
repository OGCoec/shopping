package com.example.ShoppingSystem.mapper.signin;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.Map;

@Mapper
public interface UserSignInMapper {

    Long acquireUserSignInLock(@Param("userId") Long userId);

    Map<String, Object> findLatestSignRecordByUserId(@Param("userId") Long userId);

    int insertSignRecordIgnore(@Param("userId") Long userId,
                               @Param("signDate") LocalDate signDate,
                               @Param("rewardPoints") int rewardPoints,
                               @Param("continuousCount") int continuousCount,
                               @Param("cycleDay") int cycleDay);

    Map<String, Object> addRewardPoints(@Param("userId") Long userId,
                                        @Param("rewardPoints") int rewardPoints);

    Map<String, Object> findPointAccountByUserId(@Param("userId") Long userId);
}
