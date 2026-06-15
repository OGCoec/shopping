package com.example.ShoppingSystem.security.risk.webrtc;

import java.util.List;

public record WebRtcRiskReportRequest(List<String> webRtcIps,
                                      String webRtcStatus,
                                      Long durationMillis,
                                      String diagnosticReason) {
}
