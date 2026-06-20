package com.example.ShoppingSystem.signin.service.impl.UserSignInService;

import com.example.ShoppingSystem.mapper.signin.UserSignInMapper;
import com.example.ShoppingSystem.signin.config.UserSignInProperties;
import com.example.ShoppingSystem.signin.dto.UserSignInResponse;
import com.example.ShoppingSystem.signin.dto.UserSignInStatusResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import com.example.ShoppingSystem.signin.service.UserSignInService;
@Service
public class UserSignInServiceImpl implements UserSignInService {

    private static final int CYCLE_LENGTH = 30;
    private static final int DEFAULT_REWARD_POINTS = 1;
    private static final int THREE_DAY_REWARD_POINTS = 3;
    private static final int SEVEN_DAY_REWARD_POINTS = 10;
    private static final int THIRTY_DAY_REWARD_POINTS = 50;

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

        SignPeriod currentPeriod = currentPeriod();
        Map<String, Object> latestRecord = userSignInMapper.findLatestSignRecordByUserId(userId);
        String latestPeriodKey = stringValue(latestRecord, "signPeriodKey");
        boolean signedInCurrentPeriod = currentPeriod.signPeriodKey().equals(latestPeriodKey);
        boolean streakStillActive = signedInCurrentPeriod || currentPeriod.previousPeriodKey().equals(latestPeriodKey);
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
                properties.resolvedPeriodUnit().name()
        );
    }

    @Transactional
    public UserSignInResponse signIn(Long userId) {
        if (userId == null || userId <= 0L) {
            return UserSignInResponse.authRequired();
        }

        SignPeriod currentPeriod = currentPeriod();
        userSignInMapper.acquireUserSignInLock(userId);
        Map<String, Object> latestRecord = userSignInMapper.findLatestSignRecordByUserId(userId);
        String latestPeriodKey = stringValue(latestRecord, "signPeriodKey");
        if (currentPeriod.signPeriodKey().equals(latestPeriodKey)) {
            return alreadySigned(userId, latestRecord);
        }

        int continuousCount = currentPeriod.previousPeriodKey().equals(latestPeriodKey)
                ? intValue(latestRecord, "continuousCount", 0) + 1
                : 1;
        int cycleDay = cycleDay(continuousCount);
        int rewardPoints = rewardPoints(cycleDay);

        int inserted = userSignInMapper.insertSignRecordIgnore(
                userId,
                currentPeriod.signPeriodKey(),
                currentPeriod.signDate(),
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

    private SignPeriod currentPeriod() {
        ZoneId zoneId = properties.resolvedZoneId();
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        return switch (properties.resolvedPeriodUnit()) {
            case SECOND -> secondPeriod(now);
            case DAY -> dayPeriod(now);
        };
    }

    private SignPeriod dayPeriod(ZonedDateTime now) {
        OffsetDateTime signDate = now.toLocalDate().atStartOfDay(now.getZone()).toOffsetDateTime();
        String periodDate = signDate.toLocalDate().toString();
        return new SignPeriod(
                "DAY:" + periodDate,
                "DAY:" + signDate.toLocalDate().minusDays(1),
                signDate
        );
    }

    private SignPeriod secondPeriod(ZonedDateTime now) {
        LocalDateTime currentSecond = now.toLocalDateTime().truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime previousSecond = currentSecond.minusSeconds(1);
        return new SignPeriod(
                "SECOND:" + currentSecond.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "SECOND:" + previousSecond.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                currentSecond.atZone(now.getZone()).toOffsetDateTime()
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

    private String stringValue(Map<String, Object> row, String key) {
        if (row == null || row.isEmpty()) {
            return "";
        }
        Object value = row.get(key);
        return value == null ? "" : String.valueOf(value);
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

    private record SignPeriod(String signPeriodKey,
                              String previousPeriodKey,
                              OffsetDateTime signDate) {
    }

    private record Milestone(int cycleDay,
                             int periodsToNext,
                             int rewardPoints) {
        private static Milestone empty() {
            return new Milestone(0, 0, 0);
        }
    }
}
