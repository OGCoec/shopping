package com.example.ShoppingSystem.admin.controller.auth;

import com.example.ShoppingSystem.admin.dto.AdminApiResponse;
import com.example.ShoppingSystem.admin.dto.AdminRedirectResponse;
import com.example.ShoppingSystem.admin.dto.AdminSessionMeResponse;
import com.example.ShoppingSystem.admin.service.auth.AdminSessionService;
import com.example.ShoppingSystem.security.risk.webrtc.WebRtcRiskReportRequest;
import com.example.ShoppingSystem.security.risk.webrtc.WebRtcRiskReportResponse;
import com.example.ShoppingSystem.security.risk.webrtc.WebRtcRiskReportService;
import com.example.ShoppingSystem.security.risk.webrtc.WebRtcRiskStateQueryService;
import com.example.ShoppingSystem.security.risk.webrtc.WebRtcRiskStateResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/shopping/admin")
public class AdminSessionController {

    private static final String ADMIN_LOGIN_PATH = "/shopping/admin/login";

    private final AdminSessionService adminSessionService;
    private final WebRtcRiskReportService webRtcRiskReportService;
    private final WebRtcRiskStateQueryService webRtcRiskStateQueryService;

    public AdminSessionController(AdminSessionService adminSessionService,
                                  WebRtcRiskReportService webRtcRiskReportService,
                                  WebRtcRiskStateQueryService webRtcRiskStateQueryService) {
        this.adminSessionService = adminSessionService;
        this.webRtcRiskReportService = webRtcRiskReportService;
        this.webRtcRiskStateQueryService = webRtcRiskStateQueryService;
    }

    @GetMapping("/session/me")
    public AdminApiResponse<AdminSessionMeResponse> current(HttpServletRequest request) {
        return AdminApiResponse.ok(adminSessionService.current(request));
    }

    @PostMapping("/logout")
    public AdminApiResponse<AdminRedirectResponse> logout(HttpServletRequest request,
                                                          HttpServletResponse response) {
        adminSessionService.logout(request, response);
        return AdminApiResponse.ok(new AdminRedirectResponse(ADMIN_LOGIN_PATH));
    }

    @PostMapping("/session/webrtc/report")
    public ResponseEntity<WebRtcRiskReportResponse> reportWebRtc(@RequestBody(required = false) WebRtcRiskReportRequest report,
                                                                 HttpServletRequest request) {
        return ResponseEntity.accepted().body(webRtcRiskReportService.reportAdmin(request, report));
    }

    @GetMapping("/session/webrtc/state")
    public WebRtcRiskStateResponse webRtcState(HttpServletRequest request) {
        return webRtcRiskStateQueryService.queryAdmin(request);
    }
}
