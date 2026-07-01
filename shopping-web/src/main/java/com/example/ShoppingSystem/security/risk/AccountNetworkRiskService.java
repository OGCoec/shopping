package com.example.ShoppingSystem.security.risk;
import com.example.ShoppingSystem.security.risk.webrtc.WebRtcRiskDecision;
import jakarta.servlet.http.HttpServletRequest;
public interface AccountNetworkRiskService {
    public record AccountNetworkRiskDecision(boolean allowed,
                                                 boolean terminationRequired,
                                                 Long retryAfterMs,
                                                 String status,
                                                 String reason,
                                                 String message) {

            public static AccountNetworkRiskDecision allow() {
                return new AccountNetworkRiskDecision(true, false, null, "", "", "");
            }

            public static AccountNetworkRiskDecision blocked(boolean terminationRequired,
                                                             Long retryAfterMs,
                                                             String status,
                                                             String reason,
                                                             String message) {
                return new AccountNetworkRiskDecision(false, terminationRequired, retryAfterMs, status, reason, message);
            }
        }

    public AccountNetworkRiskDecision evaluate(HttpServletRequest request);

    public void recordAsyncWebRtcRisk(Long userId,
                                      String currentIp,
                                      String deviceFingerprintHash,
                                      WebRtcRiskDecision decision,
                                      long observedAtEpochMillis);
}
