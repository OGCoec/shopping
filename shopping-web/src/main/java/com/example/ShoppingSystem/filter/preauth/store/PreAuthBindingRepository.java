package com.example.ShoppingSystem.filter.preauth.store;

import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.filter.preauth.model.PreAuthBinding;
import com.example.ShoppingSystem.filter.preauth.support.PreAuthProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Redis repository for pre-auth bindings.
 */
@Component
public class PreAuthBindingRepository {

    private static final String FIELD_FP_HASH = "fpHash";
    private static final String FIELD_UA_HASH = "uaHash";
    private static final String FIELD_CURRENT_IP = "currentIp";
    private static final String FIELD_RECENT_IPS = "recentIps";
    private static final String FIELD_CHANGE_COUNT = "changeCount";
    private static final String FIELD_IP_SCORE = "ipScore";
    private static final String FIELD_DEVICE_SCORE = "deviceScore";
    private static final String FIELD_RISK_LEVEL = "riskLevel";
    private static final String FIELD_SCORE = "score";
    private static final String FIELD_CURRENT_IP_SEEN_AT = "currentIpSeenAt";
    private static final String FIELD_CURRENT_COUNTRY = "currentCountry";
    private static final String FIELD_CURRENT_REGION = "currentRegion";
    private static final String FIELD_CURRENT_CITY = "currentCity";
    private static final String FIELD_CURRENT_LATITUDE = "currentLatitude";
    private static final String FIELD_CURRENT_LONGITUDE = "currentLongitude";
    private static final String FIELD_SAME_CITY_IP_CHANGE_COUNT = "sameCityIpChangeCount";
    private static final String FIELD_LAST_PENALIZED_IP_TRANSITION = "lastPenalizedIpTransition";
    private static final String FIELD_LAST_PENALTY_AT = "lastPenaltyAt";
    private static final String FIELD_LAST_PENALTY_SCORE = "lastPenaltyScore";
    private static final String FIELD_LAST_PENALTY_REASON = "lastPenaltyReason";
    private static final String LEGACY_FIELD_LAST_SEEN = "lastSeen";
    private static final String LEGACY_FIELD_EXPIRES_AT = "expiresAt";
    private static final int DEFAULT_SCORE_WHEN_UNAVAILABLE = 6000;
    private static final int DEFAULT_SCORE_WHEN_MISSING = -1;

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final PreAuthProperties properties;

    public PreAuthBindingRepository(StringRedisTemplate stringRedisTemplate,
                                    ObjectMapper objectMapper,
                                    PreAuthProperties properties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public PreAuthBinding load(String token) {
        if (StrUtil.isBlank(token)) {
            return null;
        }
        try {
            Map<Object, Object> raw = stringRedisTemplate.opsForHash().entries(redisKey(token.trim()));
            if (raw == null || raw.isEmpty()) {
                return null;
            }
            return new PreAuthBinding(
                    token.trim(),
                    toStringValue(raw.get(FIELD_FP_HASH)),
                    toStringValue(raw.get(FIELD_UA_HASH)),
                    toStringValue(raw.get(FIELD_CURRENT_IP)),
                    parseRecentIps(toStringValue(raw.get(FIELD_RECENT_IPS))),
                    parseInt(toStringValue(raw.get(FIELD_CHANGE_COUNT)), 0),
                    parseInt(toStringValue(raw.get(FIELD_IP_SCORE)), DEFAULT_SCORE_WHEN_MISSING),
                    parseInt(toStringValue(raw.get(FIELD_DEVICE_SCORE)), DEFAULT_SCORE_WHEN_MISSING),
                    parseInt(toStringValue(raw.get(FIELD_SCORE)), DEFAULT_SCORE_WHEN_UNAVAILABLE),
                    toStringValue(raw.get(FIELD_RISK_LEVEL)),
                    parseLong(toStringValue(raw.get(FIELD_CURRENT_IP_SEEN_AT)), 0L),
                    toStringValue(raw.get(FIELD_CURRENT_COUNTRY)),
                    toStringValue(raw.get(FIELD_CURRENT_REGION)),
                    toStringValue(raw.get(FIELD_CURRENT_CITY)),
                    parseDecimal(toStringValue(raw.get(FIELD_CURRENT_LATITUDE))),
                    parseDecimal(toStringValue(raw.get(FIELD_CURRENT_LONGITUDE))),
                    parseInt(toStringValue(raw.get(FIELD_SAME_CITY_IP_CHANGE_COUNT)), 0),
                    toStringValue(raw.get(FIELD_LAST_PENALIZED_IP_TRANSITION)),
                    parseLong(toStringValue(raw.get(FIELD_LAST_PENALTY_AT)), 0L),
                    parseInt(toStringValue(raw.get(FIELD_LAST_PENALTY_SCORE)), 0),
                    toStringValue(raw.get(FIELD_LAST_PENALTY_REASON))
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    public void save(PreAuthBinding binding) {
        Map<String, String> hash = new LinkedHashMap<>();
        hash.put(FIELD_FP_HASH, binding.fpHash());
        hash.put(FIELD_UA_HASH, binding.uaHash());
        hash.put(FIELD_CURRENT_IP, binding.currentIp());
        hash.put(FIELD_RECENT_IPS, stringifyRecentIps(binding.recentIps()));
        hash.put(FIELD_CHANGE_COUNT, String.valueOf(binding.changeCount()));
        hash.put(FIELD_IP_SCORE, String.valueOf(binding.ipScore()));
        hash.put(FIELD_DEVICE_SCORE, String.valueOf(binding.deviceScore()));
        hash.put(FIELD_SCORE, String.valueOf(binding.score()));
        hash.put(FIELD_RISK_LEVEL, StrUtil.blankToDefault(binding.riskLevel(), "L3"));
        hash.put(FIELD_CURRENT_IP_SEEN_AT, String.valueOf(Math.max(0L, binding.currentIpSeenAtEpochMillis())));
        hash.put(FIELD_CURRENT_COUNTRY, normalizeNullable(binding.currentCountry()));
        hash.put(FIELD_CURRENT_REGION, normalizeNullable(binding.currentRegion()));
        hash.put(FIELD_CURRENT_CITY, normalizeNullable(binding.currentCity()));
        hash.put(FIELD_CURRENT_LATITUDE, stringifyDecimal(binding.currentLatitude()));
        hash.put(FIELD_CURRENT_LONGITUDE, stringifyDecimal(binding.currentLongitude()));
        hash.put(FIELD_SAME_CITY_IP_CHANGE_COUNT, String.valueOf(Math.max(0, binding.sameCityIpChangeCount())));
        hash.put(FIELD_LAST_PENALIZED_IP_TRANSITION, normalizeNullable(binding.lastPenalizedIpTransition()));
        hash.put(FIELD_LAST_PENALTY_AT, String.valueOf(Math.max(0L, binding.lastPenaltyAtEpochMillis())));
        hash.put(FIELD_LAST_PENALTY_SCORE, String.valueOf(Math.max(0, binding.lastPenaltyScore())));
        hash.put(FIELD_LAST_PENALTY_REASON, normalizeNullable(binding.lastPenaltyReason()));

        String key = redisKey(binding.token());
        stringRedisTemplate.opsForHash().putAll(key, hash);
        stringRedisTemplate.opsForHash().delete(key, LEGACY_FIELD_LAST_SEEN, LEGACY_FIELD_EXPIRES_AT);
        stringRedisTemplate.expire(key, bindingTtl());
    }

    public void delete(String token) {
        if (StrUtil.isBlank(token)) {
            return;
        }
        stringRedisTemplate.delete(redisKey(token.trim()));
    }

    private String redisKey(String token) {
        return properties.getRedisKeyPrefix() + token;
    }

    private Duration bindingTtl() {
        return Duration.ofMinutes(Math.max(1, properties.getTtlMinutes()));
    }

    private List<String> parseRecentIps(String value) {
        if (StrUtil.isBlank(value)) {
            return new ArrayList<>();
        }
        try {
            List<String> parsed = objectMapper.readValue(value, new TypeReference<>() {
            });
            return parsed == null ? new ArrayList<>() : new ArrayList<>(parsed);
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    private String stringifyRecentIps(List<String> recentIps) {
        try {
            return objectMapper.writeValueAsString(recentIps == null ? List.of() : recentIps);
        } catch (Exception ignored) {
            return "[]";
        }
    }

    private int parseInt(String value, int defaultValue) {
        if (StrUtil.isBlank(value)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private long parseLong(String value, long defaultValue) {
        if (StrUtil.isBlank(value)) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private BigDecimal parseDecimal(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String stringifyDecimal(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }

    private String normalizeNullable(String value) {
        return StrUtil.blankToDefault(value, "").trim();
    }

    private String toStringValue(Object value) {
        return value == null ? "" : value.toString();
    }
}
