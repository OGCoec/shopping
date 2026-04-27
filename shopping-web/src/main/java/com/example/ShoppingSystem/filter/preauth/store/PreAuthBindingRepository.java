package com.example.ShoppingSystem.filter.preauth.store;

import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.filter.preauth.model.PreAuthBinding;
import com.example.ShoppingSystem.filter.preauth.support.PreAuthProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 预登录绑定对象的 Redis 仓库。
 * <p>
 * 这个类只负责一件事：把 PreAuthBinding 和 Redis Hash 相互转换。
 * 上层流程不需要知道 Redis 字段名、JSON 序列化方式或 TTL 刷新细节。
 */
@Component
public class PreAuthBindingRepository {

    /** Redis Hash 字段：设备指纹 hash。 */
    private static final String FIELD_FP_HASH = "fpHash";
    /** Redis Hash 字段：User-Agent hash。 */
    private static final String FIELD_UA_HASH = "uaHash";
    /** Redis Hash 字段：当前绑定 IP。 */
    private static final String FIELD_CURRENT_IP = "currentIp";
    /** Redis Hash 字段：最近 IP 列表。 */
    private static final String FIELD_RECENT_IPS = "recentIps";
    /** Redis Hash 字段：IP 变化次数。 */
    private static final String FIELD_CHANGE_COUNT = "changeCount";
    /** Redis Hash 字段：最近访问时间。 */
    private static final String FIELD_LAST_SEEN = "lastSeen";
    /** Redis Hash 字段：逻辑过期时间。 */
    private static final String FIELD_EXPIRES_AT = "expiresAt";
    /** Redis Hash 字段：风险等级。 */
    private static final String FIELD_RISK_LEVEL = "riskLevel";
    /** Redis Hash 字段：风险分。 */
    private static final String FIELD_SCORE = "score";
    /** 风控查询不可用时的默认风险分。 */
    private static final int DEFAULT_SCORE_WHEN_UNAVAILABLE = 6000;

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

    /**
     * 根据 token 从 Redis 加载完整绑定对象。
     * <p>
     * 如果 Redis 中没有数据，或者解析异常，统一返回 null，
     * 由上层按“过期/不存在”处理。
     */
    public PreAuthBinding load(String token) {
        if (StrUtil.isBlank(token)) {
            return null;
        }
        try {
            // 先拿到整张 Hash，再逐字段组装为领域对象。
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
                    parseLong(toStringValue(raw.get(FIELD_LAST_SEEN)), 0L),
                    parseLong(toStringValue(raw.get(FIELD_EXPIRES_AT)), 0L),
                    parseInt(toStringValue(raw.get(FIELD_SCORE)), DEFAULT_SCORE_WHEN_UNAVAILABLE),
                    toStringValue(raw.get(FIELD_RISK_LEVEL))
            );
        } catch (Exception ignored) {
            // 解析失败时不抛出到主链路，统一按“绑定不可用”处理。
            return null;
        }
    }

    /**
     * 保存绑定对象并刷新 TTL。
     */
    public void save(PreAuthBinding binding) {
        Map<String, String> hash = new LinkedHashMap<>();

        // 把 record 中的每个字段平铺成 Redis Hash。
        hash.put(FIELD_FP_HASH, binding.fpHash());
        hash.put(FIELD_UA_HASH, binding.uaHash());
        hash.put(FIELD_CURRENT_IP, binding.currentIp());
        hash.put(FIELD_RECENT_IPS, stringifyRecentIps(binding.recentIps()));
        hash.put(FIELD_CHANGE_COUNT, String.valueOf(binding.changeCount()));
        hash.put(FIELD_LAST_SEEN, String.valueOf(binding.lastSeenEpochMillis()));
        hash.put(FIELD_EXPIRES_AT, String.valueOf(binding.expiresAtEpochMillis()));
        hash.put(FIELD_SCORE, String.valueOf(binding.score()));
        hash.put(FIELD_RISK_LEVEL, StrUtil.blankToDefault(binding.riskLevel(), "L3"));

        String key = redisKey(binding.token());
        stringRedisTemplate.opsForHash().putAll(key, hash);
        // 每次成功保存都顺带续期，保证活跃会话持续有效。
        stringRedisTemplate.expire(key, bindingTtl());
    }

    /**
     * 删除指定 token 对应的绑定。
     */
    public void delete(String token) {
        if (StrUtil.isBlank(token)) {
            return;
        }
        stringRedisTemplate.delete(redisKey(token.trim()));
    }

    /**
     * 构建绑定对象的 Redis 主键。
     */
    private String redisKey(String token) {
        return properties.getRedisKeyPrefix() + token;
    }

    /**
     * 计算绑定对象的统一 TTL。
     */
    private Duration bindingTtl() {
        return Duration.ofMinutes(Math.max(1, properties.getTtlMinutes()));
    }

    /**
     * 把 recentIps 的 JSON 字符串解析回列表。
     */
    private List<String> parseRecentIps(String value) {
        if (StrUtil.isBlank(value)) {
            return new ArrayList<>();
        }
        try {
            List<String> parsed = objectMapper.readValue(value, new TypeReference<>() {
            });
            return parsed == null ? new ArrayList<>() : new ArrayList<>(parsed);
        } catch (Exception ignored) {
            // JSON 异常时降级为空列表，避免污染主链路。
            return new ArrayList<>();
        }
    }

    /**
     * 把 recentIps 列表序列化为 JSON。
     */
    private String stringifyRecentIps(List<String> recentIps) {
        try {
            return objectMapper.writeValueAsString(recentIps == null ? List.of() : recentIps);
        } catch (Exception ignored) {
            // 极端情况下退化为空 JSON 数组。
            return "[]";
        }
    }

    /**
     * 安全解析 int。
     */
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

    /**
     * 安全解析 long。
     */
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

    /**
     * 统一把任意对象转换为字符串，null 转空串。
     */
    private String toStringValue(Object value) {
        return value == null ? "" : value.toString();
    }
}
