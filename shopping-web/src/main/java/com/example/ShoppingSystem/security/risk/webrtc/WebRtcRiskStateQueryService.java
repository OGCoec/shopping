package com.example.ShoppingSystem.security.risk.webrtc;

import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.admin.service.auth.AdminSessionService;
import com.example.ShoppingSystem.filter.preauth.support.PreAuthRequestResolver;
import com.example.ShoppingSystem.security.token.AuthTokenService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public interface WebRtcRiskStateQueryService {
    public WebRtcRiskStateResponse queryAdmin(HttpServletRequest request);

    public WebRtcRiskStateResponse queryPreAuthOrUser(HttpServletRequest request);
}
