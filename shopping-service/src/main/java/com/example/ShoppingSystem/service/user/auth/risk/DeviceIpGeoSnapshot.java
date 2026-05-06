package com.example.ShoppingSystem.service.user.auth.risk;

import java.math.BigDecimal;

/**
 * Device-risk layer view of an IP geo snapshot.
 */
public record DeviceIpGeoSnapshot(String country,
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
