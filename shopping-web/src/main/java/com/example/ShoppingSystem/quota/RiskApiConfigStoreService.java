package com.example.ShoppingSystem.quota;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class RiskApiConfigStoreService {

    public static final String REDIS_HASH_KEY = "admin:risk-api:config";
    public static final String STORE_TYPE = "redis-hash";

    private static final Pattern CONFIG_NAME_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final StringRedisTemplate stringRedisTemplate;

    public RiskApiConfigStoreService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public String storeTarget() {
        return "Redis hash: " + REDIS_HASH_KEY;
    }

    public String storeType() {
        return STORE_TYPE;
    }

    public Optional<String> readValue(String configName) {
        if (!isValidConfigName(configName)) {
            return Optional.empty();
        }
        Object hashValue = stringRedisTemplate.opsForHash().get(REDIS_HASH_KEY, configName);
        String hashText = toText(hashValue);
        if (StringUtils.hasText(hashText)) {
            return Optional.of(hashText);
        }
        return Optional.ofNullable(stringRedisTemplate.opsForValue().get(configName))
                .filter(StringUtils::hasText);
    }

    public Map<String, String> readValues(Collection<String> configNames) {
        Map<String, String> values = new LinkedHashMap<>();
        if (configNames == null || configNames.isEmpty()) {
            return values;
        }

        List<String> validNames = configNames.stream()
                .filter(RiskApiConfigStoreService::isValidConfigName)
                .distinct()
                .toList();
        if (validNames.isEmpty()) {
            return values;
        }

        List<Object> hashFields = new ArrayList<>(validNames);
        List<Object> hashValues = stringRedisTemplate.opsForHash().multiGet(REDIS_HASH_KEY, hashFields);
        List<String> directValues = stringRedisTemplate.opsForValue().multiGet(validNames);

        for (int index = 0; index < validNames.size(); index += 1) {
            String hashText = toText(readAt(hashValues, index));
            if (StringUtils.hasText(hashText)) {
                values.put(validNames.get(index), hashText);
                continue;
            }
            String directText = readAt(directValues, index);
            if (StringUtils.hasText(directText)) {
                values.put(validNames.get(index), directText);
            }
        }
        return values;
    }

    public void writeValues(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        values.forEach((name, value) -> {
            if (!isValidConfigName(name)) {
                throw new IllegalArgumentException("Risk API config name is invalid.");
            }
            if (value == null || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
                throw new IllegalArgumentException("Risk API config value must not contain line breaks.");
            }
            normalized.put(name, value);
        });
        if (!normalized.isEmpty()) {
            stringRedisTemplate.opsForHash().putAll(REDIS_HASH_KEY, normalized);
        }
    }

    private static boolean isValidConfigName(String configName) {
        return StringUtils.hasText(configName) && CONFIG_NAME_PATTERN.matcher(configName).matches();
    }

    private static String toText(Object value) {
        return value == null ? null : value.toString();
    }

    private static <T> T readAt(List<T> values, int index) {
        if (values == null || index < 0 || index >= values.size()) {
            return null;
        }
        return values.get(index);
    }
}
