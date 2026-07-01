package com.example.ShoppingSystem.quota;

public interface IpCountryQueryService {
    public record CountryQueryResult(boolean success,
                                         String country,
                                         String source,
                                         String reason) {
            public static CountryQueryResult success(String country, String source) {
                return new CountryQueryResult(true, country, source, "ok");
            }

            public static CountryQueryResult failed(String source, String reason) {
                return new CountryQueryResult(false, null, source, reason);
            }
        }

    public record GeoQueryResult(boolean success,
                                     IpGeoSnapshot geo,
                                     String source,
                                     String reason) {
            public static GeoQueryResult success(IpGeoSnapshot geo, String source) {
                return new GeoQueryResult(true, geo, source, "ok");
            }

            public static GeoQueryResult failed(String source, String reason) {
                return new GeoQueryResult(false, null, source, reason);
            }
        }

    public CountryQueryResult queryCountry(String publicIp);

    public GeoQueryResult queryGeo(String publicIp);
}
