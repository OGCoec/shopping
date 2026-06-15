package com.example.ShoppingSystem.security.risk.webrtc;

public record WebRtcRiskReportResponse(boolean success,
                                       boolean queued,
                                       String message) {

    public static WebRtcRiskReportResponse accepted() {
        return new WebRtcRiskReportResponse(true, true, "queued");
    }

    public static WebRtcRiskReportResponse skipped(String message) {
        return new WebRtcRiskReportResponse(true, false, message);
    }
}
