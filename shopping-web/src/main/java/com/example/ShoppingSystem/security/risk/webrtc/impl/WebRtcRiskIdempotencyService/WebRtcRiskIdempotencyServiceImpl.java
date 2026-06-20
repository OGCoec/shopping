package com.example.ShoppingSystem.security.risk.webrtc.impl.WebRtcRiskIdempotencyService;

import cn.hutool.core.util.StrUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

import com.example.ShoppingSystem.security.risk.webrtc.WebRtcRiskIdempotencyService;
import com.example.ShoppingSystem.security.risk.webrtc.WebRtcRiskProperties;
import com.example.ShoppingSystem.security.risk.webrtc.WebRtcRiskScope;
@Service
public class WebRtcRiskIdempotencyServiceImpl implements WebRtcRiskIdempotencyService {

    private final StringRedisTemplate stringRedisTemplate;
    private final WebRtcRiskProperties properties;

    public WebRtcRiskIdempotencyServiceImpl(StringRedisTemplate stringRedisTemplate,
                                        WebRtcRiskProperties properties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.properties = properties;
    }

    public boolean markReport(WebRtcRiskScope scope,
                              String subjectRef,
                              String httpIp,
                              String webRtcStatus,
                              List<String> webRtcIps) {
        if (scope == null || StrUtil.isBlank(subjectRef)) {
            return false;
        }
        String key = properties.getReportDedupKeyPrefix()
                + scope.name()
                + ":"
                + subjectRef
                + ":"
                + StrUtil.blankToDefault(httpIp, "")
                + ":"
                + StrUtil.blankToDefault(webRtcStatus, "")
                + ":"
                + String.join(",", webRtcIps == null ? List.of() : webRtcIps);
        Boolean ok = stringRedisTemplate.opsForValue().setIfAbsent(
                key,
                "1",
                Duration.ofSeconds(Math.max(1, properties.getReportDedupTtlSeconds()))
        );
        return Boolean.TRUE.equals(ok);
    }

    public boolean markProcessing(String eventId) {
        if (StrUtil.isBlank(eventId)) {
            return true;
        }
        Boolean ok = stringRedisTemplate.opsForValue().setIfAbsent(
                properties.getIdempotencyKeyPrefix() + eventId,
                "1",
                Duration.ofMinutes(Math.max(1, properties.getIdempotencyTtlMinutes()))
        );
        return Boolean.TRUE.equals(ok);
    }

    public void clearProcessing(String eventId) {
        if (StrUtil.isBlank(eventId)) {
            return;
        }
        stringRedisTemplate.delete(properties.getIdempotencyKeyPrefix() + eventId);
    }
}
