package com.example.ShoppingSystem.controller.auth;

import com.example.ShoppingSystem.filter.preauth.PreAuthBindingService;
import com.example.ShoppingSystem.service.user.auth.login.impl.LoginChallengeSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;

@Controller
@RequestMapping("/shopping/auth/waf")
public class WafVerifyController {

    private static final Logger log = LoggerFactory.getLogger(WafVerifyController.class);
    private static final String DEFAULT_RETURN_PATH = "/";

    private final PreAuthBindingService preAuthBindingService;
    private final LoginChallengeSessionService loginChallengeSessionService;

    public WafVerifyController(PreAuthBindingService preAuthBindingService,
                               LoginChallengeSessionService loginChallengeSessionService) {
        this.preAuthBindingService = preAuthBindingService;
        this.loginChallengeSessionService = loginChallengeSessionService;
    }

    @GetMapping("/verify")
    public void verify(@RequestParam(value = "return", required = false) String returnPath,
                       HttpServletRequest request,
                       HttpServletResponse response) throws IOException {
        String token = preAuthBindingService.resolveIncomingToken(request);
        String sanitizedReturnPath = sanitizeReturnPath(returnPath);
        log.info("WAF verify callback entered, uri={}, returnPath={}, tokenPresent={}, remoteAddr={}",
                request.getRequestURI(),
                sanitizedReturnPath,
                token != null && !token.isBlank(),
                request.getRemoteAddr());

        if (token != null && !token.isBlank()) {
            preAuthBindingService.refreshBindingForCurrentIpAfterWaf(token, request);
            if (sanitizedReturnPath.startsWith("/shopping/user/log-in")) {
                loginChallengeSessionService.markWafVerified(token);
            }
        }

        response.addHeader("Set-Cookie", preAuthBindingService.buildClearWafRequiredCookie(request).toString());
        response.sendRedirect(appendWafVerifiedFlag(sanitizedReturnPath));
    }

    private String sanitizeReturnPath(String returnPath) {
        if (returnPath == null || returnPath.isBlank()) {
            return DEFAULT_RETURN_PATH;
        }
        String trimmed = returnPath.trim();
        if (!trimmed.startsWith("/") || trimmed.startsWith("//")) {
            return DEFAULT_RETURN_PATH;
        }
        return trimmed;
    }

    private String appendWafVerifiedFlag(String returnPath) {
        if (returnPath.contains("waf_verified=")) {
            return returnPath;
        }
        return returnPath + (returnPath.contains("?") ? "&" : "?") + "waf_verified=1";
    }
}
