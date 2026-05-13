package com.example.ShoppingSystem.filter.preauth.domain;

import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.filter.preauth.model.PreAuthBinding;
import com.example.ShoppingSystem.filter.preauth.model.PreAuthRiskProfile;
import com.example.ShoppingSystem.filter.preauth.support.PreAuthProperties;
import com.example.ShoppingSystem.quota.IpCountryQueryService;
import com.example.ShoppingSystem.quota.IpGeoSnapshot;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Factory for creating and refreshing pre-auth bindings.
 */
@Component
public class PreAuthBindingFactory {

    private static final int DEFAULT_DEVICE_SCORE = 7000;

    private final PreAuthProperties properties;
    private final PreAuthRiskService riskService;
    private final IpCountryQueryService ipCountryQueryService;

    public PreAuthBindingFactory(PreAuthProperties properties,
                                 PreAuthRiskService riskService,
                                 IpCountryQueryService ipCountryQueryService) {
        this.properties = properties;
        this.riskService = riskService;
        this.ipCountryQueryService = ipCountryQueryService;
    }

    public PreAuthBinding createNewBinding(String token,
                                           String fpHash,
                                           String uaHash,
                                           String currentIp,
                                           String normalizedFingerprint) {
        PreAuthRiskProfile riskProfile = riskService.resolveRiskProfile(currentIp, normalizedFingerprint);
        IpGeoSnapshot geo = resolveGeo(currentIp);
        return new PreAuthBinding(
                token,
                fpHash,
                uaHash,
                currentIp,
                appendRecentIp(new ArrayList<>(), currentIp),
                0,
                riskProfile.ipScore(),
                riskProfile.deviceScore(),
                riskProfile.score(),
                riskProfile.riskLevel(),
                System.currentTimeMillis(),
                geo == null ? null : geo.country(),
                geo == null ? null : geo.region(),
                geo == null ? null : geo.city(),
                geo == null ? null : geo.latitude(),
                geo == null ? null : geo.longitude(),
                0,
                "",
                0L,
                0,
                ""
        );
    }

    public PreAuthBinding refreshExistingBinding(PreAuthBinding existing, String currentIp) {
        return refreshExistingBinding(existing, currentIp, null);
    }

    public PreAuthBinding refreshExistingBinding(PreAuthBinding existing,
                                                 String currentIp,
                                                 String normalizedFingerprint) {
        boolean ipChanged = !StrUtil.equals(existing.currentIp(), currentIp);
        int changeCount = existing.changeCount();
        List<String> recentIps = existing.recentIps() == null
                ? new ArrayList<>()
                : new ArrayList<>(existing.recentIps());
        int ipScore = existing.ipScore();
        int deviceScore = existing.deviceScore();
        int score = existing.score();
        String riskLevel = existing.riskLevel();
        long currentIpSeenAt = existing.currentIpSeenAtEpochMillis();
        IpGeoSnapshot currentGeo = new IpGeoSnapshot(
                existing.currentCountry(),
                existing.currentRegion(),
                existing.currentCity(),
                existing.currentLatitude(),
                existing.currentLongitude()
        );

        if (ipChanged) {
            recentIps = appendRecentIp(recentIps, currentIp);
            changeCount += 1;
            currentIpSeenAt = System.currentTimeMillis();
            currentGeo = resolveGeo(currentIp);
        } else {
            currentIpSeenAt = System.currentTimeMillis();
            if (!currentGeo.hasAnyGeo()) {
                IpGeoSnapshot refreshedGeo = resolveGeo(currentIp);
                if (refreshedGeo != null && refreshedGeo.hasAnyGeo()) {
                    currentGeo = refreshedGeo;
                }
            }
        }

        if (ipChanged || hasMissingCompositeRisk(existing)) {
            PreAuthRiskProfile riskProfile = StrUtil.isNotBlank(normalizedFingerprint)
                    ? riskService.resolveRiskProfile(currentIp, normalizedFingerprint)
                    : riskService.resolveRiskProfile(currentIp, fallbackDeviceScore(deviceScore));
            ipScore = riskProfile.ipScore();
            deviceScore = riskProfile.deviceScore();
            score = riskProfile.score();
            riskLevel = riskProfile.riskLevel();
        }

        return new PreAuthBinding(
                existing.token(),
                existing.fpHash(),
                existing.uaHash(),
                currentIp,
                recentIps,
                changeCount,
                ipScore,
                deviceScore,
                score,
                riskLevel,
                currentIpSeenAt,
                currentGeo == null ? null : currentGeo.country(),
                currentGeo == null ? null : currentGeo.region(),
                currentGeo == null ? null : currentGeo.city(),
                currentGeo == null ? null : currentGeo.latitude(),
                currentGeo == null ? null : currentGeo.longitude(),
                existing.sameCityIpChangeCount(),
                existing.lastPenalizedIpTransition(),
                existing.lastPenaltyAtEpochMillis(),
                existing.lastPenaltyScore(),
                existing.lastPenaltyReason(),
                existing.webRtcIp(),
                existing.webRtcStatus(),
                existing.webRtcSeenAtEpochMillis(),
                existing.webRtcMismatchCount()
        );
    }

    public Duration bindingTtl() {
        return Duration.ofMinutes(Math.max(1, properties.getTtlMinutes()));
    }

    private List<String> appendRecentIp(List<String> recentIps, String ip) {
        if (recentIps == null) {
            recentIps = new ArrayList<>();
        }
        if (StrUtil.isBlank(ip)) {
            return recentIps;
        }

        recentIps.removeIf(existing -> StrUtil.equals(existing, ip));
        recentIps.add(0, ip);
        int safeLimit = Math.max(1, properties.getRecentIpLimit());
        while (recentIps.size() > safeLimit) {
            recentIps.remove(recentIps.size() - 1);
        }
        return recentIps;
    }

    private boolean hasMissingCompositeRisk(PreAuthBinding binding) {
        return binding.ipScore() < 0
                || binding.deviceScore() < 0
                || binding.score() < 0
                || StrUtil.isBlank(binding.riskLevel());
    }

    private int fallbackDeviceScore(int existingDeviceScore) {
        return existingDeviceScore >= 0 ? existingDeviceScore : DEFAULT_DEVICE_SCORE;
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
}
