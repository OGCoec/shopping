package com.example.ShoppingSystem.controller.auth;

import com.example.ShoppingSystem.filter.preauth.PreAuthBindingService;
import com.example.ShoppingSystem.service.user.auth.login.impl.LoginChallengeSessionService;
import com.example.ShoppingSystem.service.user.auth.passwordreset.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.time.Duration;

@Controller
@RequestMapping("/shopping/auth/waf")
public class WafVerifyController {

    private static final Logger log = LoggerFactory.getLogger(WafVerifyController.class);
    private static final String DEFAULT_RETURN_PATH = "/";
    private static final String LOGIN_WAF_RESUME_COOKIE_NAME = "LOGIN_WAF_RESUME";
    private static final String PASSWORD_RESET_WAF_RESUME_COOKIE_NAME = "PASSWORD_RESET_WAF_RESUME";
    private static final Duration LOGIN_WAF_RESUME_TTL = Duration.ofSeconds(60);
    private static final Duration PASSWORD_RESET_WAF_RESUME_TTL = Duration.ofSeconds(60);

    private final PreAuthBindingService preAuthBindingService;
    private final LoginChallengeSessionService loginChallengeSessionService;
    private final PasswordResetService passwordResetService;

    public WafVerifyController(PreAuthBindingService preAuthBindingService,
                               LoginChallengeSessionService loginChallengeSessionService,
                               PasswordResetService passwordResetService) {
        this.preAuthBindingService = preAuthBindingService;
        this.loginChallengeSessionService = loginChallengeSessionService;
        this.passwordResetService = passwordResetService;
    }

    @GetMapping("/verify")
    public void verify(@RequestParam(value = "return", required = false) String returnPath,
                       HttpServletRequest request,
                       HttpServletResponse response) throws IOException {
        String token = preAuthBindingService.resolveIncomingToken(request);
        String sanitizedReturnPath = sanitizeReturnPath(returnPath);
        String resolvedIp = preAuthBindingService.resolveClientIp(request);
        log.info("WAF verify callback entered, uri={}, returnPath={}, tokenId={}, resolvedIp={}, xForwardedFor={}, xRealIp={}, remoteAddr={}",
                request.getRequestURI(),
                sanitizedReturnPath,
                shortToken(token),
                resolvedIp,
                header(request, "X-Forwarded-For"),
                header(request, "X-Real-IP"),
                request.getRemoteAddr());

        boolean loginWafReturn = sanitizedReturnPath.startsWith("/shopping/user/log-in");
        boolean passwordResetWafReturn = sanitizedReturnPath.startsWith("/shopping/user/forgot-password");
        if (token != null && !token.isBlank()) {
            preAuthBindingService.refreshBindingForCurrentIpAfterWaf(token, request);
            if (loginWafReturn) {
                loginChallengeSessionService.markWafVerified(token);
                response.addHeader("Set-Cookie", buildLoginWafResumeCookie(request).toString());
            }
            if (passwordResetWafReturn) {
                passwordResetService.markWafVerified(token);
                response.addHeader("Set-Cookie", buildPasswordResetWafResumeCookie(request).toString());
            }
        }

        response.addHeader("Set-Cookie", preAuthBindingService.buildClearWafRequiredCookie(request).toString());
        response.sendRedirect(buildRedirectReturnPath(sanitizedReturnPath, loginWafReturn || passwordResetWafReturn));
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

    private String buildRedirectReturnPath(String returnPath, boolean loginWafReturn) {
        if (loginWafReturn) {
            return stripWafVerifiedFlag(returnPath);
        }
        return appendWafVerifiedFlag(returnPath);
    }

    private String stripWafVerifiedFlag(String returnPath) {
        if (returnPath == null || returnPath.isBlank() || !returnPath.contains("waf_verified=")) {
            return returnPath;
        }
        try {
            return UriComponentsBuilder.fromUriString(returnPath)
                    .replaceQueryParam("waf_verified")
                    .build()
                    .toUriString();
        } catch (Exception ignored) {
            return returnPath;
        }
    }

    private ResponseCookie buildLoginWafResumeCookie(HttpServletRequest request) {
        return ResponseCookie.from(LOGIN_WAF_RESUME_COOKIE_NAME, "1")
                .httpOnly(false)
                .secure(isHttpsRequest(request))
                .path("/")
                .sameSite("Lax")
                .maxAge(LOGIN_WAF_RESUME_TTL)
                .build();
    }

    private ResponseCookie buildPasswordResetWafResumeCookie(HttpServletRequest request) {
        return ResponseCookie.from(PASSWORD_RESET_WAF_RESUME_COOKIE_NAME, "1")
                .httpOnly(false)
                .secure(isHttpsRequest(request))
                .path("/")
                .sameSite("Lax")
                .maxAge(PASSWORD_RESET_WAF_RESUME_TTL)
                .build();
    }

    private boolean isHttpsRequest(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        if (request.isSecure()) {
            return true;
        }
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        return "https".equalsIgnoreCase(forwardedProto == null ? "" : forwardedProto.trim());
    }

    private String header(HttpServletRequest request, String name) {
        if (request == null) {
            return "none";
        }
        String value = request.getHeader(name);
        if (value == null || value.isBlank()) {
            return "none";
        }
        String trimmed = value.trim();
        return trimmed.length() <= 180 ? trimmed : trimmed.substring(0, 180) + "...";
    }

    private String shortToken(String token) {
        if (token == null || token.isBlank()) {
            return "none";
        }
        String normalized = token.trim();
        int length = normalized.length();
        String tail = normalized.substring(Math.max(0, length - 8));
        return "len=" + length + ",tail=" + tail;
    }
}
