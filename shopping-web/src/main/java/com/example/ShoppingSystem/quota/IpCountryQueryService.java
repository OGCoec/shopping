package com.example.ShoppingSystem.quota;

import com.example.ShoppingSystem.mapper.IpReputationProfileMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * IP geo query chain: local Caffeine -> Redis -> DB -> BIN.
 * <p>
 * The Redis key keeps the existing prefix {@code register:ip:country:} for compatibility,
 * but the value is now a geo JSON payload instead of only a country code.
 */
@Service
public class IpCountryQueryService {

    private static final Logger log = LoggerFactory.getLogger(IpCountryQueryService.class);

    private static final String SOURCE_CAFFEINE = "CAFFEINE";
    private static final String SOURCE_REDIS = "REDIS";
    private static final String SOURCE_DB = "DB";
    private static final String SOURCE_BIN = "BIN";
    private static final String SOURCE_NONE = "NONE";

    private final IpCountryLocalCacheStore localCacheStore;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final IpReputationProfileMapper ipReputationProfileMapper;
    private final Ip2LocationBinCountryService ip2LocationBinCountryService;

    @Value("${register.ip-country-cache.enabled:true}")
    private boolean enabled;

    @Value("${register.ip-country-cache.redis-key-prefix:register:ip:country:}")
    private String redisKeyPrefix;

    @Value("${register.ip-country-cache.redis-ttl-minutes:360}")
    private int redisTtlMinutes;

    @Value("${register.ip-country-cache.redis-ttl-jitter-minutes:1080}")
    private int redisTtlJitterMinutes;

    public IpCountryQueryService(IpCountryLocalCacheStore localCacheStore,
                                 StringRedisTemplate stringRedisTemplate,
                                 ObjectMapper objectMapper,
                                 IpReputationProfileMapper ipReputationProfileMapper,
                                 Ip2LocationBinCountryService ip2LocationBinCountryService) {
        this.localCacheStore = localCacheStore;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.ipReputationProfileMapper = ipReputationProfileMapper;
        this.ip2LocationBinCountryService = ip2LocationBinCountryService;
    }

    public CountryQueryResult queryCountry(String publicIp) {
        GeoQueryResult result = queryGeo(publicIp);
        if (result.success() && result.geo() != null && result.geo().hasCountry()) {
            return CountryQueryResult.success(result.geo().country(), result.source());
        }
        return CountryQueryResult.failed(result.source(), result.reason());
    }

    public GeoQueryResult queryGeo(String publicIp) {
        if (!enabled) {
            return GeoQueryResult.failed(SOURCE_NONE, "country_cache_disabled");
        }
        if (isBlank(publicIp)) {
            return GeoQueryResult.failed(SOURCE_NONE, "invalid_ip");
        }

        String ip = publicIp.trim();

        IpGeoSnapshot localGeo = localCacheStore.getGeo(ip);
        if (isUsableGeo(localGeo)) {
            return GeoQueryResult.success(localGeo, SOURCE_CAFFEINE);
        }

        IpGeoSnapshot redisGeo = readGeoFromRedis(ip);
        if (isUsableGeo(redisGeo)) {
            localCacheStore.putGeo(ip, redisGeo);
            return GeoQueryResult.success(redisGeo, SOURCE_REDIS);
        }

        IpGeoSnapshot dbGeo = readGeoFromDb(ip);
        if (isUsableGeo(dbGeo)) {
            writeGeoToCache(ip, dbGeo, SOURCE_DB);
            return GeoQueryResult.success(dbGeo, SOURCE_DB);
        }

        IpGeoSnapshot binGeo = ip2LocationBinCountryService.queryGeo(ip);
        if (isUsableGeo(binGeo)) {
            // BIN hits are cached locally and in Redis, but not written back to DB.
            writeGeoToCache(ip, binGeo, SOURCE_BIN);
            return GeoQueryResult.success(binGeo, SOURCE_BIN);
        }

        return GeoQueryResult.failed(SOURCE_BIN, "geo_not_found");
    }

    private IpGeoSnapshot readGeoFromRedis(String ip) {
        try {
            String value = stringRedisTemplate.opsForValue().get(redisKey(ip));
            if (isBlank(value)) {
                return null;
            }
            String trimmed = value.trim();
            if (!trimmed.startsWith("{")) {
                return new IpGeoSnapshot(normalizeCountryCode(trimmed), null, null, null, null);
            }

            JsonNode root = objectMapper.readTree(trimmed);
            return new IpGeoSnapshot(
                    firstCountry(root),
                    firstText(
                            readTextNode(root, "region"),
                            readTextNode(root, "regionName"),
                            readTextNode(root, "region_name"),
                            readTextNode(root, "province"),
                            readTextNode(root, "state")
                    ),
                    firstText(
                            readTextNode(root, "city"),
                            readTextNode(root, "cityName"),
                            readTextNode(root, "city_name")
                    ),
                    firstDecimal(root, "latitude", "lat"),
                    firstDecimal(root, "longitude", "lon", "lng")
            );
        } catch (Exception e) {
            log.debug("IP geo Redis read failed, ip={}, reason={}", ip, e.getMessage());
            return null;
        }
    }

    private IpGeoSnapshot readGeoFromDb(String ip) {
        try {
            Map<String, Object> row = ip.contains(":")
                    ? ipReputationProfileMapper.findIpv6RiskCacheByIp(ip)
                    : ipReputationProfileMapper.findIpv4RiskCacheByIp(ip);
            if (row == null || row.isEmpty()) {
                return null;
            }

            JsonNode raw = parseRawJson(toStringValue(row.get("raw_json_text")));
            String country = firstText(
                    normalizeCountryCode(toStringValue(row.get("country"))),
                    firstCountry(raw)
            );
            String region = firstText(
                    normalizeNullableText(toStringValue(row.get("region"))),
                    readTextNode(raw, "region"),
                    readTextNode(raw, "regionName"),
                    readTextNode(raw, "region_name"),
                    readTextNode(raw, "province"),
                    readTextNode(raw, "state"),
                    nestedText(raw, "location", "region"),
                    nestedText(raw, "location", "province"),
                    nestedText(raw, "location", "state")
            );
            String city = firstText(
                    normalizeNullableText(toStringValue(row.get("city"))),
                    readTextNode(raw, "city"),
                    readTextNode(raw, "cityName"),
                    readTextNode(raw, "city_name"),
                    nestedText(raw, "location", "city")
            );
            BigDecimal latitude = firstDecimal(
                    toBigDecimal(row.get("latitude")),
                    firstDecimal(raw, "latitude", "lat"),
                    nestedDecimal(raw, "location", "latitude"),
                    nestedDecimal(raw, "location", "lat")
            );
            BigDecimal longitude = firstDecimal(
                    toBigDecimal(row.get("longitude")),
                    firstDecimal(raw, "longitude", "lon", "lng"),
                    nestedDecimal(raw, "location", "longitude"),
                    nestedDecimal(raw, "location", "lon"),
                    nestedDecimal(raw, "location", "lng")
            );
            return new IpGeoSnapshot(country, region, city, latitude, longitude);
        } catch (Exception e) {
            log.debug("IP geo DB read failed, ip={}, reason={}", ip, e.getMessage());
            return null;
        }
    }

    private void writeGeoToCache(String ip, IpGeoSnapshot geo, String source) {
        if (isBlank(ip) || !isUsableGeo(geo)) {
            return;
        }
        IpGeoSnapshot normalized = normalizeGeo(geo);
        if (normalized == null || !normalized.hasAnyGeo()) {
            return;
        }
        localCacheStore.putGeo(ip, normalized);

        try {
            String jsonValue = objectMapper.writeValueAsString(new RedisIpGeoCacheValue(
                    normalized.country(),
                    normalized.region(),
                    normalized.city(),
                    normalized.latitude(),
                    normalized.longitude(),
                    source,
                    System.currentTimeMillis()));
            stringRedisTemplate.opsForValue().set(redisKey(ip), jsonValue, Duration.ofMinutes(computeRedisTtlMinutes()));
        } catch (Exception e) {
            log.debug("IP geo Redis write failed, ip={}, country={}, reason={}", ip, normalized.country(), e.getMessage());
        }
    }

    private int computeRedisTtlMinutes() {
        int min = Math.max(1, redisTtlMinutes);
        int jitter = Math.max(0, redisTtlJitterMinutes);
        if (jitter == 0) {
            return min;
        }
        return min + ThreadLocalRandom.current().nextInt(jitter + 1);
    }

    private String redisKey(String ip) {
        return redisKeyPrefix + ip;
    }

    private JsonNode parseRawJson(String rawJson) {
        if (isBlank(rawJson)) {
            return null;
        }
        try {
            return objectMapper.readTree(rawJson);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String firstCountry(JsonNode root) {
        if (root == null) {
            return null;
        }
        return firstText(
                normalizeCountryCode(readTextNode(root, "country")),
                normalizeCountryCode(readTextNode(root, "countryCode")),
                normalizeCountryCode(readTextNode(root, "country_code")),
                normalizeCountryCode(nestedText(root, "country", "code"))
        );
    }

    private BigDecimal firstDecimal(JsonNode root, String... fields) {
        if (root == null || fields == null) {
            return null;
        }
        for (String field : fields) {
            BigDecimal parsed = toBigDecimal(readTextNode(root, field));
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    private BigDecimal nestedDecimal(JsonNode root, String objectName, String fieldName) {
        return toBigDecimal(nestedText(root, objectName, fieldName));
    }

    private String readTextNode(JsonNode root, String field) {
        if (root == null || field == null) {
            return null;
        }
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        return node.asText();
    }

    private String nestedText(JsonNode root, String objectName, String fieldName) {
        if (root == null || objectName == null || fieldName == null) {
            return null;
        }
        JsonNode objectNode = root.get(objectName);
        if (objectNode == null || objectNode.isNull()) {
            return null;
        }
        JsonNode valueNode = objectNode.get(fieldName);
        if (valueNode == null || valueNode.isNull()) {
            return null;
        }
        return valueNode.asText();
    }

    private IpGeoSnapshot normalizeGeo(IpGeoSnapshot geo) {
        if (geo == null) {
            return null;
        }
        return new IpGeoSnapshot(
                normalizeCountryCode(geo.country()),
                normalizeNullableText(geo.region()),
                normalizeNullableText(geo.city()),
                geo.latitude(),
                geo.longitude()
        );
    }

    private String normalizeCountryCode(String countryCode) {
        if (isBlank(countryCode)) {
            return null;
        }
        String normalized = countryCode.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() != 2 || "-".equals(normalized)) {
            return null;
        }
        return normalized;
    }

    private String normalizeNullableText(String value) {
        if (isBlank(value)) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()
                || "-".equals(normalized)
                || "N/A".equalsIgnoreCase(normalized)
                || normalized.toLowerCase(Locale.ROOT).contains("not supported")) {
            return null;
        }
        return normalized;
    }

    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = normalizeNullableText(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private BigDecimal firstDecimal(BigDecimal... values) {
        if (values == null) {
            return null;
        }
        for (BigDecimal value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        String text = value.toString();
        if (isBlank(text) || "-".equals(text.trim())) {
            return null;
        }
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private boolean isUsableGeo(IpGeoSnapshot geo) {
        return geo != null && geo.hasAnyGeo();
    }

    private String toStringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

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

    private record RedisIpGeoCacheValue(String country,
                                        String region,
                                        String city,
                                        BigDecimal latitude,
                                        BigDecimal longitude,
                                        String source,
                                        long updatedAtEpochMillis) {
    }
}
