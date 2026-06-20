package com.example.ShoppingSystem.quota;

import com.example.ShoppingSystem.mapper.risk.IpReputationProfileMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

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
