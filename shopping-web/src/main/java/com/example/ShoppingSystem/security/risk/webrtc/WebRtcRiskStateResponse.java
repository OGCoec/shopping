package com.example.ShoppingSystem.security.risk.webrtc;

import java.util.List;

public record WebRtcRiskStateResponse(boolean success,
                                      boolean reusable,
                                      String scope,
                                      String httpIp,
                                      List<String> webRtcIps,
                                      String webRtcStatus,
                                      long ttlMillis,
                                      String reason) {

    public static WebRtcRiskStateResponse reusable(WebRtcRiskScope scope,
                                                   String httpIp,
                                                   List<String> webRtcIps,
                                                   String webRtcStatus,
                                                   long ttlMillis,
                                                   String reason) {
        return new WebRtcRiskStateResponse(
                true,
                true,
                scope == null ? "" : scope.name(),
                httpIp == null ? "" : httpIp,
                webRtcIps == null ? List.of() : webRtcIps,
                webRtcStatus == null ? "" : webRtcStatus,
                Math.max(0L, ttlMillis),
                reason == null ? "" : reason
        );
    }

    public static WebRtcRiskStateResponse notReusable(WebRtcRiskScope scope,
                                                      String httpIp,
                                                      long ttlMillis,
                                                      String reason) {
        return new WebRtcRiskStateResponse(
                true,
                false,
                scope == null ? "" : scope.name(),
                httpIp == null ? "" : httpIp,
                List.of(),
                "",
                Math.max(0L, ttlMillis),
                reason == null ? "" : reason
        );
    }
}
