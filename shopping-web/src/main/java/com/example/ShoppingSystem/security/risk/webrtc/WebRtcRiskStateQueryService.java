package com.example.ShoppingSystem.security.risk.webrtc;
import jakarta.servlet.http.HttpServletRequest;
public interface WebRtcRiskStateQueryService {
    public WebRtcRiskStateResponse queryAdmin(HttpServletRequest request);

    public WebRtcRiskStateResponse queryPreAuthOrUser(HttpServletRequest request);
}
