package com.example.ShoppingSystem.filter.preauth.domain;

import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.filter.preauth.model.PreAuthBinding;
import com.example.ShoppingSystem.filter.preauth.model.PreAuthRiskProfile;
import com.example.ShoppingSystem.filter.preauth.support.PreAuthProperties;
import com.example.ShoppingSystem.mapper.RegisterRiskProfileMapper;
import com.example.ShoppingSystem.quota.IpCountryQueryService;
import com.example.ShoppingSystem.quota.IpGeoSnapshot;
import com.example.ShoppingSystem.service.user.auth.risk.DeviceL6CountingBloomDecisionService;
import com.example.ShoppingSystem.service.user.auth.risk.DeviceRiskCacheInvalidator;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Applies short-term device-score penalties when a live pre-auth binding switches IP.
 */
@Service
public class PreAuthIpChangePenaltyService {

    private static final int SAME_CITY_TOLERANCE = 5;
    private static final int SAME_CITY_BATCH_PENALTY = 100;
    private static final int SAME_COUNTRY_CITY_CHANGED_PENALTY = 50;
    private static final int COUNTRY_CHANGED_PENALTY = 150;
    private static final double EARTH_RADIUS_KM = 6371.0088D;
    private static final long MIN_SPEED_WINDOW_MILLIS = 60_000L;

    private final RegisterRiskProfileMapper registerRiskProfileMapper;
    private final IpCountryQueryService ipCountryQueryService;
    private final PreAuthRiskService riskService;
    private final DeviceRiskCacheInvalidator cacheInvalidator;
    private final DeviceL6CountingBloomDecisionService deviceL6CountingBloomDecisionService;
    private final PreAuthProperties properties;

    public PreAuthIpChangePenaltyService(RegisterRiskProfileMapper registerRiskProfileMapper,
                                         IpCountryQueryService ipCountryQueryService,
                                         PreAuthRiskService riskService,
                                         DeviceRiskCacheInvalidator cacheInvalidator,
                                         DeviceL6CountingBloomDecisionService deviceL6CountingBloomDecisionService,
                                         PreAuthProperties properties) {
        this.registerRiskProfileMapper = registerRiskProfileMapper;
        this.ipCountryQueryService = ipCountryQueryService;
        this.riskService = riskService;
        this.cacheInvalidator = cacheInvalidator;
        this.deviceL6CountingBloomDecisionService = deviceL6CountingBloomDecisionService;
        this.properties = properties;
    }

    public PreAuthBinding applyShortTermPenalty(PreAuthBinding existing,
                                                String currentIp,
                                                String normalizedFingerprint) {
        if (existing == null || StrUtil.isBlank(currentIp) || StrUtil.equals(existing.currentIp(), currentIp)) {
            return existing;
        }

        long nowMillis = System.currentTimeMillis();
        OffsetDateTime now = OffsetDateTime.now();
        IpGeoSnapshot oldGeo = oldGeo(existing);
        if (oldGeo == null || !oldGeo.hasAnyGeo()) {
            oldGeo = resolveGeo(existing.currentIp());
        }
        IpGeoSnapshot currentGeo = resolveGeo(currentIp);
        PenaltyDecision decision = decidePenalty(existing, currentIp, oldGeo, currentGeo, nowMillis);

        String transition = transition(existing.currentIp(), currentIp);
        int appliedPenalty = decision.penaltyScore();
        if (appliedPenalty > 0 && StrUtil.equals(transition, existing.lastPenalizedIpTransition())) {
            appliedPenalty = 0;
        }

        int updatedRows = registerRiskProfileMapper.applyDeviceRiskIpChangePenalty(
                normalizedFingerprint,
                currentIp,
                now,
                appliedPenalty > 0 ? transition : "",
                appliedPenalty,
                appliedPenalty > 0 ? normalizeReason(decision.reason()) : ""
        );
        if (updatedRows <= 0) {
            appliedPenalty = 0;
        }
        if (appliedPenalty > 0) {
            cacheInvalidator.invalidateDeviceFingerprint(normalizedFingerprint);
            syncDeviceL6BloomFromDb(normalizedFingerprint);
        }

        PreAuthRiskProfile riskProfile = resolveRiskProfile(currentIp, normalizedFingerprint, existing, appliedPenalty);
        return new PreAuthBinding(
                existing.token(),
                existing.fpHash(),
                existing.uaHash(),
                currentIp,
                appendRecentIp(existing.recentIps(), currentIp),
                existing.changeCount() + 1,
                riskProfile.ipScore(),
                riskProfile.deviceScore(),
                riskProfile.score(),
                riskProfile.riskLevel(),
                nowMillis,
                currentGeo == null ? null : currentGeo.country(),
                currentGeo == null ? null : currentGeo.region(),
                currentGeo == null ? null : currentGeo.city(),
                currentGeo == null ? null : currentGeo.latitude(),
                currentGeo == null ? null : currentGeo.longitude(),
                decision.sameCityIpChangeCount(),
                appliedPenalty > 0 ? transition : existing.lastPenalizedIpTransition(),
                appliedPenalty > 0 ? nowMillis : existing.lastPenaltyAtEpochMillis(),
                appliedPenalty > 0 ? appliedPenalty : existing.lastPenaltyScore(),
                appliedPenalty > 0 ? normalizeReason(decision.reason()) : existing.lastPenaltyReason()
        );
    }

    private PenaltyDecision decidePenalty(PreAuthBinding existing,
                                          String currentIp,
                                          IpGeoSnapshot oldGeo,
                                          IpGeoSnapshot currentGeo,
                                          long nowMillis) {
        int sameCityCount = Math.max(0, existing.sameCityIpChangeCount());
        int penalty = 0;
        List<String> reasons = new ArrayList<>();

        if (sameCountry(oldGeo, currentGeo) && sameCity(oldGeo, currentGeo)) {
            sameCityCount += 1;
            if (sameCityCount % SAME_CITY_TOLERANCE == 0) {
                penalty += SAME_CITY_BATCH_PENALTY;
                reasons.add("SAME_CITY_IP_SWITCH_5");
            }
        } else if (sameCountry(oldGeo, currentGeo)) {
            penalty += SAME_COUNTRY_CITY_CHANGED_PENALTY;
            reasons.add("SAME_COUNTRY_CITY_CHANGED");
            int speedPenalty = shortSpeedPenalty(oldGeo, currentGeo, existing.currentIpSeenAtEpochMillis(), nowMillis);
            if (speedPenalty > 0) {
                penalty += speedPenalty;
                reasons.add("IMPOSSIBLE_TRAVEL_SHORT");
            }
        } else if (hasCountry(oldGeo) && hasCountry(currentGeo)) {
            penalty += COUNTRY_CHANGED_PENALTY;
            reasons.add("COUNTRY_CHANGED");
            int speedPenalty = shortSpeedPenalty(oldGeo, currentGeo, existing.currentIpSeenAtEpochMillis(), nowMillis);
            if (speedPenalty > 0) {
                penalty += speedPenalty;
                reasons.add("IMPOSSIBLE_TRAVEL_SHORT");
            }
        } else {
            penalty += SAME_COUNTRY_CITY_CHANGED_PENALTY;
            reasons.add("IP_CHANGED_GEO_INCOMPLETE");
            int speedPenalty = shortSpeedPenalty(oldGeo, currentGeo, existing.currentIpSeenAtEpochMillis(), nowMillis);
            if (speedPenalty > 0) {
                penalty += speedPenalty;
                reasons.add("IMPOSSIBLE_TRAVEL_SHORT");
            }
        }

        return new PenaltyDecision(penalty, sameCityCount, String.join("+", reasons));
    }

    private PreAuthRiskProfile resolveRiskProfile(String currentIp,
                                                  String normalizedFingerprint,
                                                  PreAuthBinding existing,
                                                  int appliedPenalty) {
        if (StrUtil.isNotBlank(normalizedFingerprint)) {
            return riskService.resolveRiskProfile(currentIp, normalizedFingerprint);
        }
        int fallbackDeviceScore = Math.max(0, existing.deviceScore() - Math.max(0, appliedPenalty));
        return riskService.resolveRiskProfile(currentIp, fallbackDeviceScore);
    }

    private int shortSpeedPenalty(IpGeoSnapshot oldGeo,
                                  IpGeoSnapshot currentGeo,
                                  long oldSeenAtMillis,
                                  long currentSeenAtMillis) {
        Double speed = speedKmh(oldGeo, currentGeo, oldSeenAtMillis, currentSeenAtMillis);
        if (speed == null || speed < 900D) {
            return 0;
        }
        if (speed < 1500D) {
            return 300;
        }
        if (speed < 3000D) {
            return 600;
        }
        return 900;
    }

    private Double speedKmh(IpGeoSnapshot oldGeo,
                            IpGeoSnapshot currentGeo,
                            long oldSeenAtMillis,
                            long currentSeenAtMillis) {
        if (oldGeo == null || currentGeo == null
                || !oldGeo.hasCoordinate() || !currentGeo.hasCoordinate()
                || oldSeenAtMillis <= 0L || currentSeenAtMillis <= 0L) {
            return null;
        }
        long elapsedMillis = Math.max(MIN_SPEED_WINDOW_MILLIS, currentSeenAtMillis - oldSeenAtMillis);
        double hours = elapsedMillis / 3_600_000D;
        return distanceKm(oldGeo.latitude(), oldGeo.longitude(), currentGeo.latitude(), currentGeo.longitude()) / hours;
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

    private boolean sameCountry(IpGeoSnapshot oldGeo, IpGeoSnapshot currentGeo) {
        return hasCountry(oldGeo)
                && hasCountry(currentGeo)
                && normalizeCountry(oldGeo.country()).equals(normalizeCountry(currentGeo.country()));
    }

    private boolean sameCity(IpGeoSnapshot oldGeo, IpGeoSnapshot currentGeo) {
        return oldGeo != null
                && currentGeo != null
                && StrUtil.isNotBlank(oldGeo.city())
                && StrUtil.isNotBlank(currentGeo.city())
                && oldGeo.city().trim().equalsIgnoreCase(currentGeo.city().trim());
    }

    private boolean hasCountry(IpGeoSnapshot geo) {
        return geo != null && StrUtil.isNotBlank(geo.country());
    }

    private String normalizeCountry(String country) {
        return StrUtil.blankToDefault(country, "").trim().toUpperCase(Locale.ROOT);
    }

    private IpGeoSnapshot oldGeo(PreAuthBinding existing) {
        return new IpGeoSnapshot(
                existing.currentCountry(),
                existing.currentRegion(),
                existing.currentCity(),
                existing.currentLatitude(),
                existing.currentLongitude()
        );
    }

    private IpGeoSnapshot resolveGeo(String ip) {
        if (StrUtil.isBlank(ip)) {
            return null;
        }
        try {
            IpCountryQueryService.GeoQueryResult result = ipCountryQueryService.queryGeo(ip);
            if (result != null && result.success() && result.geo() != null && result.geo().hasAnyGeo()) {
                return result.geo();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private List<String> appendRecentIp(List<String> recentIps, String ip) {
        List<String> updated = recentIps == null ? new ArrayList<>() : new ArrayList<>(recentIps);
        if (StrUtil.isBlank(ip)) {
            return updated;
        }
        updated.removeIf(existingIp -> StrUtil.equals(existingIp, ip));
        updated.add(0, ip);
        int safeLimit = Math.max(1, properties.getRecentIpLimit());
        while (updated.size() > safeLimit) {
            updated.remove(updated.size() - 1);
        }
        return updated;
    }

    private String transition(String oldIp, String currentIp) {
        return StrUtil.blankToDefault(oldIp, "").trim() + "->" + StrUtil.blankToDefault(currentIp, "").trim();
    }

    private String normalizeReason(String reason) {
        String normalized = StrUtil.blankToDefault(reason, "").trim();
        return normalized.length() <= 128 ? normalized : normalized.substring(0, 128);
    }

    private void syncDeviceL6BloomFromDb(String normalizedFingerprint) {
        try {
            Integer currentScore = registerRiskProfileMapper.findDeviceRiskScoreByFingerprint(normalizedFingerprint);
            if (currentScore != null) {
                deviceL6CountingBloomDecisionService.syncMembershipByScore(
                        normalizedFingerprint,
                        Math.max(0, Math.min(10000, currentScore)));
            }
        } catch (Exception ignored) {
        }
    }

    private record PenaltyDecision(int penaltyScore,
                                   int sameCityIpChangeCount,
                                   String reason) {
    }
}
