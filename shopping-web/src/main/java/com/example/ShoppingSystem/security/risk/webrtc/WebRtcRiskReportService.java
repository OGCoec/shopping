package com.example.ShoppingSystem.security.risk.webrtc;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.admin.service.auth.AdminSessionService;
import com.example.ShoppingSystem.filter.preauth.PreAuthHeaders;
import com.example.ShoppingSystem.filter.preauth.support.PreAuthHashingService;
import com.example.ShoppingSystem.filter.preauth.support.PreAuthRequestResolver;
import com.example.ShoppingSystem.security.token.AuthTokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;

public interface WebRtcRiskReportService {
    public WebRtcRiskReportResponse reportPreAuthOrUser(HttpServletRequest request, WebRtcRiskReportRequest report);

    public WebRtcRiskReportResponse reportAdmin(HttpServletRequest request, WebRtcRiskReportRequest report);
}
