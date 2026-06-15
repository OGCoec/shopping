package com.example.ShoppingSystem.security.risk.webrtc;

import cn.hutool.core.util.StrUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class WebRtcRiskStateStore {

    private static final String FIELD_STATUS = "status";
    private static final String FIELD_REASON = "reason";
    private static final String FIELD_HTTP_IP = "httpIp";
    private static final String FIELD_WEBRTC_IPS = "webRtcIps";
    private static final String FIELD_WEBRTC_STATUS = "webRtcStatus";
    private static final String FIELD_SEEN_AT = "seenAt";

    private final StringRedisTemplate stringRedisTemplate;
    private final WebRtcRiskProperties properties;

    public WebRtcRiskStateStore(StringRedisTemplate stringRedisTemplate,
                                WebRtcRiskProperties properties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.properties = properties;
    }

    public void save(WebRtcRiskScope scope, String subjectRef, WebRtcRiskDecision decision, long seenAtEpochMillis) {
        if (scope == null || StrUtil.isBlank(subjectRef) || decision == null) {
            return;
        }
        Map<String, String> hash = new LinkedHashMap<>();
        hash.put(FIELD_STATUS, decision.status().name());
        hash.put(FIELD_REASON, StrUtil.blankToDefault(decision.reason(), ""));
        hash.put(FIELD_HTTP_IP, StrUtil.blankToDefault(decision.httpIp(), ""));
        hash.put(FIELD_WEBRTC_IPS, String.join(",", decision.webRtcIps()));
        hash.put(FIELD_WEBRTC_STATUS, StrUtil.blankToDefault(decision.webRtcStatus(), ""));
        hash.put(FIELD_SEEN_AT, String.valueOf(Math.max(0L, seenAtEpochMillis)));
        String key = stateKey(scope, subjectRef);
        stringRedisTemplate.opsForHash().putAll(key, hash);
        stringRedisTemplate.expire(key, Duration.ofMinutes(Math.max(1, properties.getStateTtlMinutes())));
    }

    public WebRtcRiskState load(WebRtcRiskScope scope, String subjectRef) {
        if (scope == null || StrUtil.isBlank(subjectRef)) {
            return null;
        }
        Map<Object, Object> raw = stringRedisTemplate.opsForHash().entries(stateKey(scope, subjectRef));
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        return new WebRtcRiskState(
                parseStatus(text(raw.get(FIELD_STATUS))),
                text(raw.get(FIELD_REASON)),
                text(raw.get(FIELD_HTTP_IP)),
                text(raw.get(FIELD_WEBRTC_IPS)),
                text(raw.get(FIELD_WEBRTC_STATUS)),
                parseLong(text(raw.get(FIELD_SEEN_AT)))
        );
    }

    public long ttlMillis(WebRtcRiskScope scope, String subjectRef) {
        if (scope == null || StrUtil.isBlank(subjectRef)) {
            return -2L;
        }
        Long ttl = stringRedisTemplate.getExpire(stateKey(scope, subjectRef), TimeUnit.MILLISECONDS);
        return ttl == null ? -2L : ttl;
    }

    public void delete(WebRtcRiskScope scope, String subjectRef) {
        if (scope == null || StrUtil.isBlank(subjectRef)) {
            return;
        }
        stringRedisTemplate.delete(stateKey(scope, subjectRef));
    }

    private WebRtcRiskStatus parseStatus(String value) {
        if (StrUtil.isBlank(value)) {
            return WebRtcRiskStatus.NORMAL;
        }
        try {
            return WebRtcRiskStatus.valueOf(value.trim().toUpperCase());
        } catch (Exception ignored) {
            return WebRtcRiskStatus.NORMAL;
        }
    }

    private long parseLong(String value) {
        if (StrUtil.isBlank(value)) {
            return 0L;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private String text(Object value) {
        return value == null ? "" : value.toString();
    }

    private String stateKey(WebRtcRiskScope scope, String subjectRef) {
        return properties.getStateKeyPrefix() + scope.name().toLowerCase() + ":" + subjectRef;
    }
}
