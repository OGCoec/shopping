package com.example.ShoppingSystem.security.risk.webrtc;

import java.util.List;

public record WebRtcRiskDecision(WebRtcRiskStatus status,
                                 String reason,
                                 String httpIp,
                                 List<String> webRtcIps,
                                 String webRtcStatus,
                                 boolean mismatch) {
}
