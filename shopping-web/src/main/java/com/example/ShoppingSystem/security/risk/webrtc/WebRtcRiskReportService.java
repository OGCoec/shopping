package com.example.ShoppingSystem.security.risk.webrtc;
import jakarta.servlet.http.HttpServletRequest;
public interface WebRtcRiskReportService {
    public WebRtcRiskReportResponse reportPreAuthOrUser(HttpServletRequest request, WebRtcRiskReportRequest report);

    public WebRtcRiskReportResponse reportAdmin(HttpServletRequest request, WebRtcRiskReportRequest report);
}
