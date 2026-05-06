package com.example.ShoppingSystem.filter.preauth.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Complete pre-auth binding stored in Redis.
 */
public record PreAuthBinding(String token,
                             String fpHash,
                             String uaHash,
                             String currentIp,
                             List<String> recentIps,
                             int changeCount,
                             int ipScore,
                             int deviceScore,
                             int score,
                             String riskLevel,
                             long currentIpSeenAtEpochMillis,
                             String currentCountry,
                             String currentRegion,
                             String currentCity,
                             BigDecimal currentLatitude,
                             BigDecimal currentLongitude,
                             int sameCityIpChangeCount,
                             String lastPenalizedIpTransition,
                             long lastPenaltyAtEpochMillis,
                             int lastPenaltyScore,
                             String lastPenaltyReason) {
}
