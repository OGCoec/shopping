package com.example.ShoppingSystem.admin.service.auth;
import com.example.ShoppingSystem.admin.dto.AdminSessionMeResponse;
import com.example.ShoppingSystem.admin.model.AdminAccount;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
public interface AdminSessionService {
    public void authenticate(HttpServletRequest request,
                             HttpServletResponse response,
                             AdminAccount account);

    public boolean isAuthenticated(HttpServletRequest request);

    public AdminSessionMeResponse current(HttpServletRequest request);

    public void logout(HttpServletRequest request, HttpServletResponse response);

    public String resolveClientIp(HttpServletRequest request);

    public boolean isCurrentIpAllowed(HttpServletRequest request, String currentIp);

    public void refreshCurrentIp(HttpServletRequest request, String currentIp);

    public void updateWebRtcRisk(String token,
                                 String webRtcIps,
                                 String webRtcStatus,
                                 long seenAtEpochMillis,
                                 int mismatchIncrement,
                                 String riskLevel);

    public String resolveSessionToken(HttpServletRequest request);
}
