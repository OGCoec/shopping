package com.example.ShoppingSystem.security.risk.webrtc;

public record WebRtcRiskState(WebRtcRiskStatus status,
                              String reason,
                              String httpIp,
                              String webRtcIps,
                              String webRtcStatus,
                              long seenAtEpochMillis) {

    public boolean risky() {
        return status == WebRtcRiskStatus.BLOCKED || status == WebRtcRiskStatus.CHALLENGE_REQUIRED;
    }
}
