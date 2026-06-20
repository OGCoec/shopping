package com.example.ShoppingSystem.security.risk.webrtc;

import cn.hutool.core.util.StrUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.time.Duration;
import java.util.List;

public interface WebRtcRiskIdempotencyService {
    public boolean markReport(WebRtcRiskScope scope,
                              String subjectRef,
                              String httpIp,
                              String webRtcStatus,
                              List<String> webRtcIps);

    public boolean markProcessing(String eventId);

    public void clearProcessing(String eventId);
}
