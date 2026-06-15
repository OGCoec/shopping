package com.example.ShoppingSystem.security.risk.webrtc;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "risk.webrtc")
public class WebRtcRiskProperties {

    private boolean enabled = true;
    private int reportDedupTtlSeconds = 60;
    private int idempotencyTtlMinutes = 90;
    private int stateTtlMinutes = 30;
    private String reportDedupKeyPrefix = "risk:webrtc:report:dedup:";
    private String idempotencyKeyPrefix = "risk:webrtc:idempotency:";
    private String stateKeyPrefix = "risk:webrtc:state:";
}
