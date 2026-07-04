package com.example.ShoppingSystem.signin.service.impl.UserSignInService;

import com.example.ShoppingSystem.mapper.signin.UserSignInMapper;
import com.example.ShoppingSystem.signin.config.UserSignInProperties;
import com.example.ShoppingSystem.signin.dto.UserSignInResponse;
import com.example.ShoppingSystem.signin.dto.UserSignInStatusResponse;
import com.example.ShoppingSystem.signin.service.UserSignInService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;

@Service
public class UserSignInServiceImpl implements UserSignInService {

    private static final int CYCLE_LENGTH = 30;
    private static final int DEFAULT_REWARD_POINTS = 1;
    private static final int THREE_DAY_REWARD_POINTS = 3;
    private static final int SEVEN_DAY_REWARD_POINTS = 10;
    private static final int THIRTY_DAY_REWARD_POINTS = 50;
    private static final String PERIOD_UNIT_DAY = "DAY";

    private final UserSignInMapper userSignInMapper;
    private final UserSignInProperties properties;

    public UserSignInServiceImpl(UserSignInMapper userSignInMapper,
                                 UserSignInProperties properties) {
        this.userSignInMapper = userSignInMapper;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public UserSignInStatusResponse status(Long userId) {
        if (userId == null || userId <= 0L) {
            return UserSignInStatusResponse.authRequired();
        }

        LocalDate currentDate = LocalDate.now(properties.resolvedZoneId());
        LocalDate previousDate = currentDate.minusDays(1);
        Map<String, Object> latestRecord = userSignInMapper.findLatestSignRecordByUserId(userId);
        LocalDate latestSignDate = localDateValue(latestRecord, "signDate");
        boolean signedInCurrentPeriod = currentDate.equals(latestSignDate);
        boolean streakStillActive = signedInCurrentPeriod || previousDate.equals(latestSignDate);
        int continuousCount = streakStillActive ? intValue(latestRecord, "continuousCount", 0) : 0;
        int cycleDay = streakStillActive ? intValue(latestRecord, "cycleDay", 0) : 0;
        Milestone next = nextMilestone(cycleDay);
        Map<String, Object> account = userSignInMapper.findPointAccountByUserId(userId);

        return UserSignInStatusResponse.status(
                signedInCurrentPeriod,
                longValue(account, "availablePoints", 0L),
                longValue(account, "totalEarnedPoints", 0L),
                continuousCount,
                cycleDay,
                next.cycleDay(),
                next.periodsToNext(),
                next.rewardPoints(),
                PERIOD_UNIT_DAY
        );
    }

    @Transactional
    public UserSignInResponse signIn(Long userId) {
        if (userId == null || userId <= 0L) {
            return UserSignInResponse.authRequired();
        }

        LocalDate currentDate = LocalDate.now(properties.resolvedZoneId());
        LocalDate previousDate = currentDate.minusDays(1);
        userSignInMapper.acquireUserSignInLock(userId);
        Map<String, Object> latestRecord = userSignInMapper.findLatestSignRecordByUserId(userId);
        LocalDate latestSignDate = localDateValue(latestRecord, "signDate");
        if (currentDate.equals(latestSignDate)) {
            return alreadySigned(userId, latestRecord);
        }

        int continuousCount = previousDate.equals(latestSignDate)
                ? intValue(latestRecord, "continuousCount", 0) + 1
                : 1;
        int cycleDay = cycleDay(continuousCount);
        int rewardPoints = rewardPoints(cycleDay);

        int inserted = userSignInMapper.insertSignRecordIgnore(
                userId,
                currentDate,
                rewardPoints,
                continuousCount,
                cycleDay
        );
        if (inserted == 0) {
            return alreadySigned(userId, userSignInMapper.findLatestSignRecordByUserId(userId));
        }

        Map<String, Object> account = userSignInMapper.addRewardPoints(userId, rewardPoints);
        Milestone next = nextMilestone(cycleDay);
        return UserSignInResponse.signed(
                rewardPoints,
                longValue(account, "availablePoints", 0L),
                longValue(account, "totalEarnedPoints", 0L),
                continuousCount,
                cycleDay,
                next.cycleDay(),
                next.periodsToNext(),
                next.rewardPoints()
        );
    }

    private UserSignInResponse alreadySigned(Long userId, Map<String, Object> latestRecord) {
        Map<String, Object> account = userSignInMapper.findPointAccountByUserId(userId);
        int cycleDay = intValue(latestRecord, "cycleDay", 0);
        Milestone next = cycleDay <= 0 ? Milestone.empty() : nextMilestone(cycleDay);
        return UserSignInResponse.alreadySigned(
                longValue(account, "availablePoints", 0L),
                longValue(account, "totalEarnedPoints", 0L),
                intValue(latestRecord, "continuousCount", 0),
                cycleDay,
                next.cycleDay(),
                next.periodsToNext(),
                next.rewardPoints()
        );
    }

    private int cycleDay(int continuousCount) {
        return ((Math.max(1, continuousCount) - 1) % CYCLE_LENGTH) + 1;
    }

    private int rewardPoints(int cycleDay) {
        return switch (cycleDay) {
            case 3 -> THREE_DAY_REWARD_POINTS;
            case 7 -> SEVEN_DAY_REWARD_POINTS;
            case 30 -> THIRTY_DAY_REWARD_POINTS;
            default -> DEFAULT_REWARD_POINTS;
        };
    }

    private Milestone nextMilestone(int cycleDay) {
        if (cycleDay < 3) {
            return new Milestone(3, 3 - cycleDay, THREE_DAY_REWARD_POINTS);
        }
        if (cycleDay < 7) {
            return new Milestone(7, 7 - cycleDay, SEVEN_DAY_REWARD_POINTS);
        }
        if (cycleDay < 30) {
            return new Milestone(30, 30 - cycleDay, THIRTY_DAY_REWARD_POINTS);
        }
        return new Milestone(3, 3, THREE_DAY_REWARD_POINTS);
    }

    private LocalDate localDateValue(Map<String, Object> row, String key) {
        if (row == null || row.isEmpty()) {
            return null;
        }
        Object value = row.get(key);
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if (text.length() > 10) {
            text = text.substring(0, 10);
        }
        try {
            return LocalDate.parse(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    private int intValue(Map<String, Object> row, String key, int defaultValue) {
        if (row == null || row.isEmpty()) {
            return defaultValue;
        }
        Object value = row.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private long longValue(Map<String, Object> row, String key, long defaultValue) {
        if (row == null || row.isEmpty()) {
            return defaultValue;
        }
        Object value = row.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private record Milestone(int cycleDay,
                             int periodsToNext,
                             int rewardPoints) {
        private static Milestone empty() {
            return new Milestone(0, 0, 0);
        }
    }
}
