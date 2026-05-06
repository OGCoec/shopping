package com.example.ShoppingSystem.service.user.auth.risk;

import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.Utils.HybridSemaphoreIdWorker;
import com.example.ShoppingSystem.mapper.IpReputationProfileMapper;
import com.example.ShoppingSystem.mapper.RegisterRiskProfileMapper;
import com.example.ShoppingSystem.service.user.auth.register.impl.ChallengePolicy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class DeviceRiskProfileWriteService {

    private static final int FIXED_DEVICE_SCORE = 6666;
    private static final int LONG_IP_RISK_L4_PENALTY = 50;
    private static final int LONG_IP_RISK_L5_PENALTY = 150;
    private static final int LONG_IP_RISK_L6_PENALTY = 150;
    private static final double EARTH_RADIUS_KM = 6371.0088D;
    private static final long MIN_SPEED_WINDOW_MILLIS = 60_000L;

    private final RegisterRiskProfileMapper registerRiskProfileMapper;
    private final IpReputationProfileMapper ipReputationProfileMapper;
    private final HybridSemaphoreIdWorker hybridSemaphoreIdWorker;
    private final ChallengePolicy challengePolicy;
    private final ObjectProvider<DeviceIpGeoLookupService> geoLookupProvider;
    private final ObjectProvider<DeviceRiskCacheInvalidator> cacheInvalidatorProvider;

    public DeviceRiskProfileWriteService(RegisterRiskProfileMapper registerRiskProfileMapper,
                                         IpReputationProfileMapper ipReputationProfileMapper,
                                         HybridSemaphoreIdWorker hybridSemaphoreIdWorker,
                                         ChallengePolicy challengePolicy,
                                         ObjectProvider<DeviceIpGeoLookupService> geoLookupProvider,
                                         ObjectProvider<DeviceRiskCacheInvalidator> cacheInvalidatorProvider) {
        this.registerRiskProfileMapper = registerRiskProfileMapper;
        this.ipReputationProfileMapper = ipReputationProfileMapper;
        this.hybridSemaphoreIdWorker = hybridSemaphoreIdWorker;
        this.challengePolicy = challengePolicy;
        this.geoLookupProvider = geoLookupProvider;
        this.cacheInvalidatorProvider = cacheInvalidatorProvider;
    }

    public void recordSuccess(Long userId, String deviceFingerprint, String clientIp, String scene) {
        record(userId, deviceFingerprint, clientIp, true);
    }

    public void recordFailure(Long userId, String deviceFingerprint, String clientIp, String scene) {
        record(userId, deviceFingerprint, clientIp, false);
    }

    public int ensureProfileExists(String deviceFingerprint, String clientIp) {
        String normalizedFingerprint = normalizeText(deviceFingerprint);
        if (StrUtil.isBlank(normalizedFingerprint)) {
            return FIXED_DEVICE_SCORE;
        }
        upsertDeviceProfile(normalizedFingerprint, normalizeText(clientIp), OffsetDateTime.now(), 0, "");
        return FIXED_DEVICE_SCORE;
    }

    private void record(Long userId, String deviceFingerprint, String clientIp, boolean success) {
        String normalizedFingerprint = normalizeText(deviceFingerprint);
        if (StrUtil.isBlank(normalizedFingerprint)) {
            return;
        }

        String normalizedClientIp = normalizeText(clientIp);
        OffsetDateTime now = OffsetDateTime.now();
        IpChangePenalty ipChangePenalty = success
                ? resolveLongTermIpChangePenalty(normalizedFingerprint, normalizedClientIp, now)
                : IpChangePenalty.none();

        String deviceIdHex = upsertDeviceProfile(
                normalizedFingerprint,
                normalizedClientIp,
                now,
                ipChangePenalty.score(),
                ipChangePenalty.reason()
        );
        if (ipChangePenalty.score() > 0) {
            invalidateDeviceRiskCache(normalizedFingerprint);
        }
        if (StrUtil.isBlank(deviceIdHex) || userId == null) {
            return;
        }

        if (success) {
            registerRiskProfileMapper.upsertDeviceUserRelationSuccess(
                    generateHybridIdHex(),
                    deviceIdHex,
                    userId,
                    now
            );
        } else {
            registerRiskProfileMapper.upsertDeviceUserRelationFailure(
                    generateHybridIdHex(),
                    deviceIdHex,
                    userId,
                    now
            );
        }
        registerRiskProfileMapper.refreshDeviceLinkedUserCount(deviceIdHex, now);
    }

    private IpChangePenalty resolveLongTermIpChangePenalty(String deviceFingerprint,
                                                           String currentIp,
                                                           OffsetDateTime now) {
        if (StrUtil.isBlank(deviceFingerprint) || StrUtil.isBlank(currentIp)) {
            return IpChangePenalty.none();
        }

        Map<String, Object> state = registerRiskProfileMapper.findDeviceRiskStateByFingerprint(deviceFingerprint);
        if (state == null || state.isEmpty()) {
            return IpChangePenalty.none();
        }

        String lastLoginIp = readString(state, "lastLoginIp", "last_login_ip");
        if (StrUtil.isBlank(lastLoginIp) || StrUtil.equals(lastLoginIp, currentIp)) {
            return IpChangePenalty.none();
        }

        String transition = lastLoginIp.trim() + "->" + currentIp.trim();
        String lastPenalizedTransition = readString(
                state,
                "lastPenalizedIpTransition",
                "last_penalized_ip_transition"
        );
        if (StrUtil.equals(transition, lastPenalizedTransition)) {
            return IpChangePenalty.none();
        }

        List<String> reasons = new ArrayList<>();
        int penalty = longIpRiskPenalty(currentIp, reasons);

        OffsetDateTime lastIpSeenAt = readOffsetDateTime(state, "lastIpSeenAt", "last_ip_seen_at");
        int speedPenalty = longSpeedPenalty(resolveGeo(lastLoginIp), resolveGeo(currentIp), lastIpSeenAt, now);
        if (speedPenalty > 0) {
            penalty += speedPenalty;
            reasons.add("IMPOSSIBLE_TRAVEL_LONG");
        }

        if (penalty <= 0) {
            return IpChangePenalty.none();
        }
        return new IpChangePenalty(penalty, normalizePenaltyReason(String.join("+", reasons)));
    }

    private int longIpRiskPenalty(String currentIp, List<String> reasons) {
        String currentIpRiskLevel = resolveIpRiskLevel(currentIp);
        return switch (currentIpRiskLevel) {
            case "L4" -> {
                reasons.add("LONG_CURRENT_IP_L4");
                yield LONG_IP_RISK_L4_PENALTY;
            }
            case "L5" -> {
                reasons.add("LONG_CURRENT_IP_L5");
                yield LONG_IP_RISK_L5_PENALTY;
            }
            case "L6" -> {
                reasons.add("LONG_CURRENT_IP_L6");
                yield LONG_IP_RISK_L6_PENALTY;
            }
            default -> 0;
        };
    }

    private String resolveIpRiskLevel(String ip) {
        Map<String, Object> row = findIpRiskRow(ip);
        Integer currentScore = readInteger(row, "current_score", "currentScore");
        if (currentScore == null) {
            return "";
        }
        return challengePolicy.resolveRiskLevel(currentScore);
    }

    private int longSpeedPenalty(DeviceIpGeoSnapshot oldGeo,
                                 DeviceIpGeoSnapshot currentGeo,
                                 OffsetDateTime oldSeenAt,
                                 OffsetDateTime currentSeenAt) {
        Double speed = speedKmh(oldGeo, currentGeo, oldSeenAt, currentSeenAt);
        if (speed == null || speed < 900D || oldSeenAt == null || currentSeenAt == null) {
            return 0;
        }

        double elapsedHours = elapsedHours(oldSeenAt, currentSeenAt);
        if (elapsedHours > 72D) {
            return 0;
        }

        int basePenalty;
        if (speed < 1500D) {
            basePenalty = 100;
        } else if (speed < 3000D) {
            basePenalty = 250;
        } else {
            basePenalty = 450;
        }

        double multiplier;
        if (elapsedHours <= 6D) {
            multiplier = 1D;
        } else if (elapsedHours <= 24D) {
            multiplier = 0.7D;
        } else {
            multiplier = 0.4D;
        }
        return Math.max(1, (int) Math.round(basePenalty * multiplier));
    }

    private Double speedKmh(DeviceIpGeoSnapshot oldGeo,
                            DeviceIpGeoSnapshot currentGeo,
                            OffsetDateTime oldSeenAt,
                            OffsetDateTime currentSeenAt) {
        if (oldGeo == null || currentGeo == null
                || !oldGeo.hasCoordinate() || !currentGeo.hasCoordinate()
                || oldSeenAt == null || currentSeenAt == null) {
            return null;
        }
        return distanceKm(oldGeo.latitude(), oldGeo.longitude(), currentGeo.latitude(), currentGeo.longitude())
                / elapsedHours(oldSeenAt, currentSeenAt);
    }

    private double elapsedHours(OffsetDateTime oldSeenAt, OffsetDateTime currentSeenAt) {
        long elapsedMillis = Duration.between(oldSeenAt, currentSeenAt).toMillis();
        return Math.max(MIN_SPEED_WINDOW_MILLIS, elapsedMillis) / 3_600_000D;
    }

    private double distanceKm(BigDecimal oldLatitude,
                              BigDecimal oldLongitude,
                              BigDecimal currentLatitude,
                              BigDecimal currentLongitude) {
        double lat1 = Math.toRadians(oldLatitude.doubleValue());
        double lat2 = Math.toRadians(currentLatitude.doubleValue());
        double deltaLat = Math.toRadians(currentLatitude.doubleValue() - oldLatitude.doubleValue());
        double deltaLon = Math.toRadians(currentLongitude.doubleValue() - oldLongitude.doubleValue());
        double a = Math.sin(deltaLat / 2D) * Math.sin(deltaLat / 2D)
                + Math.cos(lat1) * Math.cos(lat2)
                * Math.sin(deltaLon / 2D) * Math.sin(deltaLon / 2D);
        double c = 2D * Math.atan2(Math.sqrt(a), Math.sqrt(1D - a));
        return EARTH_RADIUS_KM * c;
    }

    private DeviceIpGeoSnapshot resolveGeo(String ip) {
        if (StrUtil.isBlank(ip)) {
            return null;
        }

        DeviceIpGeoLookupService lookupService = geoLookupProvider.getIfAvailable();
        if (lookupService != null) {
            DeviceIpGeoSnapshot geo = lookupService.queryGeo(ip);
            if (geo != null && geo.hasAnyGeo()) {
                return geo;
            }
        }

        return geoFromIpRiskRow(findIpRiskRow(ip));
    }

    private DeviceIpGeoSnapshot geoFromIpRiskRow(Map<String, Object> row) {
        if (row == null || row.isEmpty()) {
            return null;
        }
        DeviceIpGeoSnapshot geo = new DeviceIpGeoSnapshot(
                normalizeCountry(readString(row, "country")),
                normalizeNullableText(readString(row, "region")),
                normalizeNullableText(readString(row, "city")),
                readBigDecimal(row, "latitude"),
                readBigDecimal(row, "longitude")
        );
        return geo.hasAnyGeo() ? geo : null;
    }

    private Map<String, Object> findIpRiskRow(String ip) {
        if (StrUtil.isBlank(ip)) {
            return Map.of();
        }
        try {
            return ip.contains(":")
                    ? ipReputationProfileMapper.findIpv6RiskCacheByIp(ip.trim())
                    : ipReputationProfileMapper.findIpv4RiskCacheByIp(ip.trim());
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private String generateHybridIdHex() {
        return HexFormat.of().formatHex(hybridSemaphoreIdWorker.nextId());
    }

    private String upsertDeviceProfile(String normalizedFingerprint,
                                       String clientIp,
                                       OffsetDateTime now,
                                       int penaltyScore,
                                       String penaltyReason) {
        int safePenalty = Math.max(0, penaltyScore);
        int initialScore = Math.max(0, FIXED_DEVICE_SCORE - safePenalty);
        return registerRiskProfileMapper.upsertDeviceRiskProfile(
                generateHybridIdHex(),
                normalizedFingerprint,
                initialScore,
                challengePolicy.resolveRiskLevel(initialScore),
                now,
                now,
                clientIp,
                now,
                safePenalty > 0 ? now : null,
                safePenalty,
                normalizePenaltyReason(penaltyReason),
                now
        );
    }

    private void invalidateDeviceRiskCache(String normalizedFingerprint) {
        DeviceRiskCacheInvalidator invalidator = cacheInvalidatorProvider.getIfAvailable();
        if (invalidator != null) {
            invalidator.invalidateDeviceFingerprint(normalizedFingerprint);
        }
    }

    private String normalizePenaltyReason(String reason) {
        String normalized = normalizeText(reason);
        if (StrUtil.isBlank(normalized)) {
            return "";
        }
        return normalized.length() <= 128 ? normalized : normalized.substring(0, 128);
    }

    private Integer readInteger(Map<String, Object> row, String... keys) {
        Object value = readValue(row, keys);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null || StrUtil.isBlank(value.toString())) {
            return null;
        }
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private BigDecimal readBigDecimal(Map<String, Object> row, String... keys) {
        Object value = readValue(row, keys);
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (value == null || StrUtil.isBlank(value.toString())) {
            return null;
        }
        try {
            return new BigDecimal(value.toString().trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private OffsetDateTime readOffsetDateTime(Map<String, Object> row, String... keys) {
        Object value = readValue(row, keys);
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime;
        }
        if (value instanceof Instant instant) {
            return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
        }
        if (value instanceof Timestamp timestamp) {
            return OffsetDateTime.ofInstant(timestamp.toInstant(), ZoneOffset.UTC);
        }
        if (value instanceof Date date) {
            return OffsetDateTime.ofInstant(date.toInstant(), ZoneOffset.UTC);
        }
        if (value == null || StrUtil.isBlank(value.toString())) {
            return null;
        }
        String text = value.toString().trim();
        try {
            return OffsetDateTime.parse(text);
        } catch (Exception ignored) {
        }
        try {
            return OffsetDateTime.ofInstant(Instant.parse(text), ZoneOffset.UTC);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String readString(Map<String, Object> row, String... keys) {
        Object value = readValue(row, keys);
        return value == null ? "" : value.toString().trim();
    }

    private Object readValue(Map<String, Object> row, String... keys) {
        if (row == null || row.isEmpty() || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (key != null && row.containsKey(key)) {
                return row.get(key);
            }
        }
        for (String key : keys) {
            if (key == null) {
                continue;
            }
            String snake = camelToSnake(key);
            if (row.containsKey(snake)) {
                return row.get(snake);
            }
        }
        return null;
    }

    private String camelToSnake(String value) {
        if (StrUtil.isBlank(value)) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < value.length(); index += 1) {
            char current = value.charAt(index);
            if (Character.isUpperCase(current)) {
                builder.append('_').append(Character.toLowerCase(current));
            } else {
                builder.append(current);
            }
        }
        return builder.toString();
    }

    private String normalizeCountry(String country) {
        String normalized = normalizeNullableText(country);
        if (StrUtil.isBlank(normalized)) {
            return null;
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        return normalized.length() == 2 ? normalized : null;
    }

    private String normalizeNullableText(String value) {
        String normalized = normalizeText(value);
        return StrUtil.isBlank(normalized) || "-".equals(normalized) ? null : normalized;
    }

    private String normalizeText(String value) {
        return StrUtil.blankToDefault(value, "").trim();
    }

    private record IpChangePenalty(int score, String reason) {

        static IpChangePenalty none() {
            return new IpChangePenalty(0, "");
        }
    }
}
