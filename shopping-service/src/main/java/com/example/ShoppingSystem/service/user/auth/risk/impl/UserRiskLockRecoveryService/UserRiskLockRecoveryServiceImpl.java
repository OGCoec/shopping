package com.example.ShoppingSystem.service.user.auth.risk.impl.UserRiskLockRecoveryService;

import com.example.ShoppingSystem.mapper.risk.UserRiskProfileMapper;
import com.example.ShoppingSystem.mapper.user.UserLoginIdentityMapper;
import com.example.ShoppingSystem.service.user.auth.risk.UserRiskLockRecoveryService;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class UserRiskLockRecoveryServiceImpl implements UserRiskLockRecoveryService {

    private static final int MAX_BATCH_SIZE = 4000;

    private final UserRiskProfileMapper userRiskProfileMapper;
    private final UserLoginIdentityMapper userLoginIdentityMapper;

    public UserRiskLockRecoveryServiceImpl(UserRiskProfileMapper userRiskProfileMapper,
                                           UserLoginIdentityMapper userLoginIdentityMapper) {
        this.userRiskProfileMapper = userRiskProfileMapper;
        this.userLoginIdentityMapper = userLoginIdentityMapper;
    }

    @Override
    public int recoverStableUnlockedUsers(int lockCount, Duration stableDuration, int scoreBonus, int batchSize) {
        if (lockCount <= 0 || stableDuration == null || stableDuration.isNegative() || stableDuration.isZero()) {
            return 0;
        }
        int safeLimit = Math.max(1, Math.min(batchSize, MAX_BATCH_SIZE));
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime cutoff = now.minus(stableDuration);
        int stableDays = Math.toIntExact(Math.max(1L, stableDuration.toDays()));
        List<Long> activeUserIds = activeRecoveryUserIds(
                (limit, offset) -> userRiskProfileMapper.listStableUnlockedUserRecoveryCandidates(
                        lockCount,
                        cutoff,
                        limit,
                        offset
                ),
                safeLimit
        );
        if (activeUserIds.isEmpty()) {
            return 0;
        }
        return userRiskProfileMapper.recoverStableUnlockedUsersByUserIds(
                activeUserIds,
                lockCount,
                Math.max(0, scoreBonus),
                stableDays,
                now);
    }

    @Override
    public int recoverStableUnlockedUsersByReason(String lockReason,
                                                  String eventType,
                                                  String eventReason,
                                                  int lockCount,
                                                  Duration stableDuration,
                                                  int scoreBonus,
                                                  int batchSize) {
        if (lockCount <= 0 || stableDuration == null || stableDuration.isNegative() || stableDuration.isZero()
                || lockReason == null || lockReason.isBlank()
                || eventType == null || eventType.isBlank()
                || eventReason == null || eventReason.isBlank()) {
            return 0;
        }
        int safeLimit = Math.max(1, Math.min(batchSize, MAX_BATCH_SIZE));
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime cutoff = now.minus(stableDuration);
        int stableDays = Math.toIntExact(Math.max(1L, stableDuration.toDays()));
        String normalizedLockReason = lockReason.trim();
        List<Long> activeUserIds = activeRecoveryUserIds(
                (limit, offset) -> userRiskProfileMapper.listStableUnlockedUserRecoveryCandidatesByReason(
                        normalizedLockReason,
                        lockCount,
                        cutoff,
                        limit,
                        offset
                ),
                safeLimit
        );
        if (activeUserIds.isEmpty()) {
            return 0;
        }
        return userRiskProfileMapper.recoverStableUnlockedUsersByReasonAndUserIds(
                activeUserIds,
                normalizedLockReason,
                eventType.trim(),
                eventReason.trim(),
                lockCount,
                Math.max(0, scoreBonus),
                stableDays,
                now);
    }

    private List<Long> activeRecoveryUserIds(CandidateLoader candidateLoader, int safeLimit) {
        LinkedHashSet<Long> activeUserIds = new LinkedHashSet<>();
        long offset = 0L;
        while (activeUserIds.size() < safeLimit) {
            int pageSize = safeLimit - activeUserIds.size();
            List<Map<String, Object>> candidates = candidateLoader.load(pageSize, offset);
            if (candidates == null || candidates.isEmpty()) {
                break;
            }
            List<Long> candidateUserIds = userIds(candidates);
            if (!candidateUserIds.isEmpty()) {
                activeUserIds.addAll(userLoginIdentityMapper.listActiveUserIdsByUserIds(candidateUserIds));
            }
            offset += candidates.size();
            if (candidates.size() < pageSize) {
                break;
            }
        }
        return new ArrayList<>(activeUserIds);
    }

    private List<Long> userIds(List<Map<String, Object>> candidates) {
        LinkedHashSet<Long> userIds = new LinkedHashSet<>();
        for (Map<String, Object> candidate : candidates) {
            Object value = candidate.get("userId");
            if (value == null) {
                value = candidate.get("user_id");
            }
            Long userId = toLong(value);
            if (userId != null) {
                userIds.add(userId);
            }
        }
        return new ArrayList<>(userIds);
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @FunctionalInterface
    private interface CandidateLoader {
        List<Map<String, Object>> load(int limit, long offset);
    }
}
