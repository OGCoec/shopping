package com.example.ShoppingSystem.quota;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public interface RiskApiConfigStoreService {
    public static final String REDIS_HASH_KEY = "admin:risk-api:config";

    public static final String STORE_TYPE = "redis-hash";

    public String storeTarget();

    public String storeType();

    public Optional<String> readValue(String configName);

    public Map<String, String> readValues(Collection<String> configNames);

    public void writeValues(Map<String, String> values);
}
