package com.example.ShoppingSystem.controller.user.security;

import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.controller.login.user.dto.LoginPhoneBindRequest;
import com.example.ShoppingSystem.filter.preauth.PreAuthBindingService;
import com.example.ShoppingSystem.filter.preauth.PreAuthHeaders;
import com.example.ShoppingSystem.security.token.AuthTokenService;
import com.example.ShoppingSystem.security.token.AuthUserContext;
import com.example.ShoppingSystem.security.token.AuthUserContextHolder;
import com.example.ShoppingSystem.service.user.auth.phone.AuthenticatedPhoneBindingService;
import com.example.ShoppingSystem.service.user.auth.phone.model.AuthenticatedPhoneBindingResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/shopping/user/security/phone")
public class UserSecurityPhoneController {

    private final AuthenticatedPhoneBindingService authenticatedPhoneBindingService;
    private final PreAuthBindingService preAuthBindingService;
    private final AuthTokenService authTokenService;

    public UserSecurityPhoneController(AuthenticatedPhoneBindingService authenticatedPhoneBindingService,
                                       PreAuthBindingService preAuthBindingService,
                                       AuthTokenService authTokenService) {
        this.authenticatedPhoneBindingService = authenticatedPhoneBindingService;
        this.preAuthBindingService = preAuthBindingService;
        this.authTokenService = authTokenService;
    }

    @PostMapping("/code")
    public ResponseEntity<SecurityPhoneBindingResponse> sendPhoneBindCode(@RequestBody LoginPhoneBindRequest body,
                                                                          Authentication authentication,
                                                                          HttpServletRequest request) {
        Long userId = requireCurrentUserId(authentication, request);
        AuthenticatedPhoneBindingResult result = authenticatedPhoneBindingService.sendPhoneBindCode(
                userId,
                resolvePreAuthToken(request),
                body.dialCode(),
                body.phoneNumber(),
                preAuthBindingService.resolveClientIp(request),
                resolveRiskLevel(request),
                resolveDeviceFingerprint(request),
                body.captchaUuid(),
                body.captchaCode()
        );
        return phoneBindingResponse(result);
    }

    @PostMapping("/bind")
    public ResponseEntity<SecurityPhoneBindingResponse> bindPhone(@RequestBody LoginPhoneBindRequest body,
                                                                  Authentication authentication,
                                                                  HttpServletRequest request) {
        Long userId = requireCurrentUserId(authentication, request);
        AuthenticatedPhoneBindingResult result = authenticatedPhoneBindingService.bindVerifiedPhone(
                userId,
                body.dialCode(),
                body.phoneNumber(),
                body.smsCode()
        );
        if (result != null && result.isSuccess()) {
            authTokenService.evictUserContext(userId);
        }
        return phoneBindingResponse(result);
    }

    private ResponseEntity<SecurityPhoneBindingResponse> phoneBindingResponse(AuthenticatedPhoneBindingResult result) {
        SecurityPhoneBindingResponse body = SecurityPhoneBindingResponse.from(result);
        return ResponseEntity.status(body.success() ? HttpStatus.OK : HttpStatus.BAD_REQUEST)
                .body(body);
    }

    private Long requireCurrentUserId(Authentication authentication, HttpServletRequest request) {
        Object requestUserId = request == null ? null : request.getAttribute("authUserId");
        if (requestUserId instanceof Number number) {
            return number.longValue();
        }
        if (requestUserId instanceof String text) {
            Long parsed = parseLong(text);
            if (parsed != null) {
                return parsed;
            }
        }
        AuthUserContext holderContext = AuthUserContextHolder.get();
        if (holderContext != null && holderContext.userId() != null) {
            return holderContext.userId();
        }
        if (authentication != null && authentication.getPrincipal() instanceof AuthUserContext context) {
            return context.userId();
        }
        return null;
    }

    private String resolvePreAuthToken(HttpServletRequest request) {
        Object requestAttribute = request == null ? null : request.getAttribute("preAuthToken");
        if (requestAttribute instanceof String token && StrUtil.isNotBlank(token)) {
            return token.trim();
        }
        return preAuthBindingService.resolveIncomingToken(request);
    }

    private String resolveRiskLevel(HttpServletRequest request) {
        Object value = request == null ? null : request.getAttribute("preAuthRiskLevel");
        if (value instanceof String text && StrUtil.isNotBlank(text)) {
            return text.trim();
        }
        return "L1";
    }

    private String resolveDeviceFingerprint(HttpServletRequest request) {
        return request == null ? "" : StrUtil.blankToDefault(request.getHeader(PreAuthHeaders.HEADER_DEVICE_FINGERPRINT), "").trim();
    }

    private Long parseLong(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public record SecurityPhoneBindingResponse(boolean success,
                                               String error,
                                               String reasonCode,
                                               String message,
                                               String phoneType,
                                               String normalizedE164,
                                               Long userId,
                                               String riskLevel,
                                               String redirectPath,
                                               Boolean requirePhoneBinding,
                                               Boolean authenticated,
                                               String challengeType,
                                               String challengeSubType,
                                               String challengeSiteKey,
                                               Long retryAfterMs) {

        public static SecurityPhoneBindingResponse from(AuthenticatedPhoneBindingResult result) {
            if (result == null) {
                return new SecurityPhoneBindingResponse(false, null, null, "Phone binding request failed.",
                        null, null, null, null, "/shopping/user/security/phone", true, true,
                        null, null, null, null);
            }
            return new SecurityPhoneBindingResponse(
                    result.isSuccess(),
                    result.getError(),
                    result.getReasonCode(),
                    result.getMessage(),
                    result.getPhoneType(),
                    result.getNormalizedE164(),
                    result.getUserId(),
                    result.getRiskLevel(),
                    result.getRedirectPath(),
                    result.isRequirePhoneBinding(),
                    result.isAuthenticated(),
                    result.getChallengeType(),
                    result.getChallengeSubType(),
                    result.getChallengeSiteKey(),
                    result.getRetryAfterMs()
            );
        }
    }
}
