package com.example.ShoppingSystem.quota;

import java.math.BigDecimal;

/**
 * Lightweight IP geo snapshot used by country/geo caches and risk checks.
 */
public record IpGeoSnapshot(String country,
                            String region,
                            String city,
                            BigDecimal latitude,
                            BigDecimal longitude) {

    public boolean hasCountry() {
        return country != null && !country.isBlank();
    }

    public boolean hasCoordinate() {
        return latitude != null && longitude != null;
    }

    public boolean hasAnyGeo() {
        return hasCountry() || hasCoordinate();
    }
}
