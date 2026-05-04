package com.example.ShoppingSystem.controller.login.user;

import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.controller.login.user.dto.PasswordResetEmailRequest;
import com.example.ShoppingSystem.controller.login.user.dto.PasswordResetResponse;
import com.example.ShoppingSystem.controller.login.user.dto.PasswordResetSubmitRequest;
import com.example.ShoppingSystem.service.user.auth.passwordreset.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/shopping/user/forgot-password")
public class PasswordResetController {

    private static final String PREAUTH_TOKEN_ATTRIBUTE = "preAuthToken";
    private static final String PREAUTH_RISK_LEVEL_ATTRIBUTE = "preAuthRiskLevel";
    private static final String PASSWORD_RESET_WAF_RESUME_HEADER = "X-Password-Reset-Waf-Resume";

    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/crypto-key")
    public ResponseEntity<PasswordResetResponse> issueCryptoKey() {
        return ResponseEntity.ok(PasswordResetResponse.from(passwordResetService.issueCryptoKey()));
    }

    @PostMapping("/reset-link")
    public ResponseEntity<PasswordResetResponse> sendResetLink(@RequestBody PasswordResetEmailRequest body,
                                                               HttpServletRequest request) {
        return ResponseEntity.ok(PasswordResetResponse.from(passwordResetService.sendResetLink(
                body.email(),
                resolvePreAuthToken(request),
                resolveRiskLevel(request),
                isPasswordResetWafResumeRequest(request),
                resolveBaseUrl(request))));
    }

    @PostMapping("/email-code")
    public ResponseEntity<PasswordResetResponse> sendEmailCode(@RequestBody PasswordResetEmailRequest body,
                                                               HttpServletRequest request) {
        return ResponseEntity.ok(PasswordResetResponse.from(passwordResetService.sendEmailCode(
                body.email(),
                resolvePreAuthToken(request),
                resolveRiskLevel(request),
                isPasswordResetWafResumeRequest(request),
                resolveBaseUrl(request))));
    }

    @PostMapping("/reset-by-link")
    public ResponseEntity<PasswordResetResponse> resetByLink(@RequestBody PasswordResetSubmitRequest body) {
        return ResponseEntity.ok(PasswordResetResponse.from(passwordResetService.resetByLink(
                body.token(),
                body.kid(),
                body.payloadCipher(),
                body.nonce(),
                body.timestamp())));
    }

    @PostMapping("/verify-code")
    public ResponseEntity<PasswordResetResponse> verifyCode(@RequestBody PasswordResetSubmitRequest body) {
        return ResponseEntity.ok(PasswordResetResponse.from(passwordResetService.verifyEmailCode(
                body.email(),
                body.code())));
    }

    @PostMapping("/reset-by-code")
    public ResponseEntity<PasswordResetResponse> resetByCode(@RequestBody PasswordResetSubmitRequest body) {
        return ResponseEntity.ok(PasswordResetResponse.from(passwordResetService.resetByVerifiedCode(
                body.token(),
                body.kid(),
                body.payloadCipher(),
                body.nonce(),
                body.timestamp())));
    }

    private String resolvePreAuthToken(HttpServletRequest request) {
        Object value = request == null ? null : request.getAttribute(PREAUTH_TOKEN_ATTRIBUTE);
        return value instanceof String token ? token : "";
    }

    private String resolveRiskLevel(HttpServletRequest request) {
        Object value = request == null ? null : request.getAttribute(PREAUTH_RISK_LEVEL_ATTRIBUTE);
        return value instanceof String riskLevel && StrUtil.isNotBlank(riskLevel) ? riskLevel.trim() : "L1";
    }

    private boolean isPasswordResetWafResumeRequest(HttpServletRequest request) {
        return request != null
                && "1".equals(StrUtil.blankToDefault(request.getHeader(PASSWORD_RESET_WAF_RESUME_HEADER), "").trim());
    }

    private String resolveBaseUrl(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String proto = StrUtil.blankToDefault(request.getHeader("X-Forwarded-Proto"), request.getScheme()).trim();
        String host = StrUtil.blankToDefault(request.getHeader("X-Forwarded-Host"), request.getHeader("Host")).trim();
        if (StrUtil.isBlank(host)) {
            host = request.getServerName();
            int port = request.getServerPort();
            if (port > 0 && port != 80 && port != 443) {
                host += ":" + port;
            }
        }
        return StrUtil.blankToDefault(proto, "https") + "://" + host;
    }
}
