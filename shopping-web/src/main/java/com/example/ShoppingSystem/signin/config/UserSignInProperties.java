package com.example.ShoppingSystem.signin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.ZoneId;

@Component
@ConfigurationProperties(prefix = "shopping.sign-in")
public class UserSignInProperties {

    private static final ZoneId DEFAULT_ZONE_ID = ZoneId.of("Asia/Shanghai");

    private String zoneId = DEFAULT_ZONE_ID.getId();

    private PeriodUnit periodUnit = PeriodUnit.SECOND;

    public String getZoneId() {
        return zoneId;
    }

    public void setZoneId(String zoneId) {
        this.zoneId = zoneId;
    }

    public PeriodUnit getPeriodUnit() {
        return periodUnit;
    }

    public void setPeriodUnit(PeriodUnit periodUnit) {
        this.periodUnit = periodUnit;
    }

    public ZoneId resolvedZoneId() {
        if (zoneId == null || zoneId.isBlank()) {
            return DEFAULT_ZONE_ID;
        }
        try {
            return ZoneId.of(zoneId.trim());
        } catch (Exception ignored) {
            return DEFAULT_ZONE_ID;
        }
    }

    public PeriodUnit resolvedPeriodUnit() {
        return periodUnit == null ? PeriodUnit.SECOND : periodUnit;
    }

    public enum PeriodUnit {
        DAY,
        SECOND
    }
}
