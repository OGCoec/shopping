package com.example.ShoppingSystem.controller.login.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.controller.login.user.dto.PasswordResetEmailRequest;
import com.example.ShoppingSystem.controller.login.user.dto.PasswordResetResponse;
import com.example.ShoppingSystem.controller.login.user.dto.PasswordResetSubmitRequest;
import com.example.ShoppingSystem.controller.login.user.dto.RegisterCaptchaResponse;
import com.example.ShoppingSystem.controller.login.user.dto.TianaiRotateCaptchaResponse;
import com.example.ShoppingSystem.service.captcha.strategy.CaptchaGenerateRequest;
import com.example.ShoppingSystem.service.captcha.strategy.CaptchaGenerateResult;
import com.example.ShoppingSystem.service.captcha.strategy.CaptchaStrategyRegistry;
import com.example.ShoppingSystem.service.user.auth.passwordreset.model.PasswordResetResult;
import com.example.ShoppingSystem.service.user.auth.passwordreset.PasswordResetEmailCaptchaRiskGateService;
import com.example.ShoppingSystem.service.user.auth.passwordreset.PasswordResetService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.CHALLENGE_HUTOOL_SHEAR;
import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.CHALLENGE_TIANAI;
import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.SUBTYPE_TIANAI_CONCAT;
import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.SUBTYPE_TIANAI_ROTATE;
import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.SUBTYPE_TIANAI_SLIDER;
import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.SUBTYPE_TIANAI_WORD_IMAGE_CLICK;

@Tag(name = "找回密码", description = "用户找回密码和重置密码接口")
@RestController
@RequestMapping("/shopping/user/forgot-password")
public class PasswordResetController {

    private static final String PREAUTH_TOKEN_ATTRIBUTE = "preAuthToken";
    private static final String PREAUTH_RISK_LEVEL_ATTRIBUTE = "preAuthRiskLevel";
    private static final String PREAUTH_TOKEN_COOKIE = "PREAUTH_TOKEN";
    private static final String DEVICE_FINGERPRINT_HEADER = "X-Device-Fingerprint";
    private static final String PASSWORD_RESET_WAF_RESUME_HEADER = "X-Password-Reset-Waf-Resume";
    public static final String PASSWORD_RESET_CODE_TOKEN_COOKIE = "PASSWORD_RESET_CODE_TOKEN";
    private static final String PASSWORD_RESET_COOKIE_PATH = "/shopping/user";
    private static final Duration PASSWORD_RESET_CODE_COOKIE_TTL = Duration.ofMinutes(5);

    private final PasswordResetService passwordResetService;
    private final PasswordResetEmailCaptchaRiskGateService passwordResetEmailCaptchaRiskGateService;
    private final CaptchaStrategyRegistry captchaStrategyRegistry;

    public PasswordResetController(PasswordResetService passwordResetService,
                                   PasswordResetEmailCaptchaRiskGateService passwordResetEmailCaptchaRiskGateService,
                                   CaptchaStrategyRegistry captchaStrategyRegistry) {
        this.passwordResetService = passwordResetService;
        this.passwordResetEmailCaptchaRiskGateService = passwordResetEmailCaptchaRiskGateService;
        this.captchaStrategyRegistry = captchaStrategyRegistry;
    }

    @Operation(summary = "获取密码重置加密公钥")
    @PostMapping("/crypto-key")
    public ResponseEntity<PasswordResetResponse> issueCryptoKey() {
        return ResponseEntity.ok(PasswordResetResponse.from(passwordResetService.issueCryptoKey()));
    }

    @Operation(summary = "发送密码重置链接")
    @PostMapping("/reset-link")
    public ResponseEntity<PasswordResetResponse> sendResetLink(@RequestBody PasswordResetEmailRequest body,
                                                               HttpServletRequest request) {
        return ResponseEntity.ok(PasswordResetResponse.from(passwordResetService.sendResetLink(
                body.email(),
                resolvePreAuthToken(request),
                resolveDeviceFingerprint(request),
                resolveRiskLevel(request),
                isPasswordResetWafResumeRequest(request),
                resolveBaseUrl(request))));
    }

    @Operation(summary = "发送密码重置邮箱验证码")
    @PostMapping("/email-code")
    public ResponseEntity<PasswordResetResponse> sendEmailCode(@RequestBody PasswordResetEmailRequest body,
                                                               HttpServletRequest request) {
        return ResponseEntity.ok(PasswordResetResponse.from(passwordResetService.sendEmailCode(
                body.email(),
                resolvePreAuthToken(request),
                resolveDeviceFingerprint(request),
                resolveClientIp(request),
                resolveRiskLevel(request),
                isPasswordResetWafResumeRequest(request),
                resolveBaseUrl(request),
                body.captchaUuid(),
                body.captchaCode())));
    }

    @Operation(summary = "获取密码重置图形验证码")
    @GetMapping("/hutoolcaptcha")
    public ResponseEntity<?> getPasswordResetCaptcha(@RequestParam(required = false) String uuid,
                                                     @RequestParam(required = false) String email,
                                                     @RequestParam(required = false) String deviceFingerprint) throws Exception {
        ensurePasswordResetChallengeAlive(email, deviceFingerprint, CHALLENGE_HUTOOL_SHEAR, null);
        CaptchaGenerateResult result = captchaStrategyRegistry.generate(new CaptchaGenerateRequest(
                CHALLENGE_HUTOOL_SHEAR,
                null,
                PasswordResetEmailCaptchaRiskGateService.PASSWORD_RESET_CAPTCHA_TYPE,
                uuid
        ));
        return ResponseEntity.ok(RegisterCaptchaResponse.builder()
                .uuid(result.uuid())
                .image(result.image())
                .build());
    }

    @Operation(summary = "获取密码重置旋转验证码")
    @GetMapping("/tianai/rotate")
    public ResponseEntity<?> getPasswordResetTianaiRotateCaptcha(@RequestParam(required = false) String captchaId,
                                                                @RequestParam(required = false) String email,
                                                                @RequestParam(required = false) String deviceFingerprint) {
        ensurePasswordResetChallengeAlive(email, deviceFingerprint, CHALLENGE_TIANAI, SUBTYPE_TIANAI_ROTATE);
        return ResponseEntity.ok(TianaiRotateCaptchaResponse.from(generateTianaiCaptcha(SUBTYPE_TIANAI_ROTATE, captchaId)));
    }

    @Operation(summary = "获取密码重置滑块验证码")
    @GetMapping("/tianai/slider")
    public ResponseEntity<?> getPasswordResetTianaiSliderCaptcha(@RequestParam(required = false) String captchaId,
                                                                @RequestParam(required = false) String email,
                                                                @RequestParam(required = false) String deviceFingerprint) {
        ensurePasswordResetChallengeAlive(email, deviceFingerprint, CHALLENGE_TIANAI, SUBTYPE_TIANAI_SLIDER);
        return ResponseEntity.ok(TianaiRotateCaptchaResponse.from(generateTianaiCaptcha(SUBTYPE_TIANAI_SLIDER, captchaId)));
    }

    @Operation(summary = "获取密码重置拼图验证码")
    @GetMapping("/tianai/concat")
    public ResponseEntity<?> getPasswordResetTianaiConcatCaptcha(@RequestParam(required = false) String captchaId,
                                                                @RequestParam(required = false) String email,
                                                                @RequestParam(required = false) String deviceFingerprint) {
        ensurePasswordResetChallengeAlive(email, deviceFingerprint, CHALLENGE_TIANAI, SUBTYPE_TIANAI_CONCAT);
        return ResponseEntity.ok(TianaiRotateCaptchaResponse.from(generateTianaiCaptcha(SUBTYPE_TIANAI_CONCAT, captchaId)));
    }

    @Operation(summary = "获取密码重置文字点选验证码")
    @GetMapping("/tianai/word-click")
    public ResponseEntity<?> getPasswordResetTianaiWordClickCaptcha(@RequestParam(required = false) String captchaId,
                                                                   @RequestParam(required = false) String email,
                                                                   @RequestParam(required = false) String deviceFingerprint) {
        ensurePasswordResetChallengeAlive(email, deviceFingerprint, CHALLENGE_TIANAI, SUBTYPE_TIANAI_WORD_IMAGE_CLICK);
        return ResponseEntity.ok(TianaiRotateCaptchaResponse.from(generateTianaiCaptcha(SUBTYPE_TIANAI_WORD_IMAGE_CLICK, captchaId)));
    }

    private cloud.tianai.captcha.application.vo.ImageCaptchaVO generateTianaiCaptcha(String subType, String captchaId) {
        return captchaStrategyRegistry.generate(new CaptchaGenerateRequest(
                CHALLENGE_TIANAI,
                subType,
                PasswordResetEmailCaptchaRiskGateService.PASSWORD_RESET_CAPTCHA_TYPE,
                captchaId
        )).tianaiCaptcha();
    }

    @Operation(summary = "通过链接重置密码")
    @PostMapping("/reset-by-link")
    public ResponseEntity<PasswordResetResponse> resetByLink(@RequestBody PasswordResetSubmitRequest body,
                                                             HttpServletRequest request) {
        return ResponseEntity.ok(PasswordResetResponse.from(passwordResetService.resetByLink(
                body.token(),
                resolveDeviceFingerprint(request),
                resolveClientIp(request),
                body.kid(),
                body.payloadCipher(),
                body.nonce(),
                body.timestamp())));
    }

    @Operation(summary = "验证密码重置邮箱验证码")
    @PostMapping("/verify-code")
    public ResponseEntity<PasswordResetResponse> verifyCode(@RequestBody PasswordResetSubmitRequest body,
                                                            HttpServletRequest request) {
        PasswordResetResult result = passwordResetService.verifyEmailCode(
                body.email(),
                body.code(),
                resolvePreAuthToken(request),
                resolveDeviceFingerprint(request));
        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        if (result.success() && StrUtil.isNotBlank(result.internalToken())) {
            response.header(HttpHeaders.SET_COOKIE, buildCodeTokenCookie(result.internalToken()).toString());
        }
        return response.body(PasswordResetResponse.from(result));
    }

    @Operation(summary = "通过邮箱验证码重置密码")
    @PostMapping("/reset-by-code")
    public ResponseEntity<PasswordResetResponse> resetByCode(@RequestBody PasswordResetSubmitRequest body,
                                                             HttpServletRequest request) {
        PasswordResetResult result = passwordResetService.resetByVerifiedCode(
                resolveCookieValue(request, PASSWORD_RESET_CODE_TOKEN_COOKIE),
                resolvePreAuthToken(request),
                resolveDeviceFingerprint(request),
                resolveClientIp(request),
                body.kid(),
                body.payloadCipher(),
                body.nonce(),
                body.timestamp());
        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        if (result.success() || "PASSWORD_RESET_CODE_TOKEN_INVALID".equals(result.error())) {
            response.header(HttpHeaders.SET_COOKIE, clearCodeTokenCookie().toString());
        }
        return response.body(PasswordResetResponse.from(result));
    }

    private String resolvePreAuthToken(HttpServletRequest request) {
        Object value = request == null ? null : request.getAttribute(PREAUTH_TOKEN_ATTRIBUTE);
        if (value instanceof String token && StrUtil.isNotBlank(token)) {
            return token.trim();
        }
        return resolveCookieValue(request, PREAUTH_TOKEN_COOKIE);
    }

    private String resolveDeviceFingerprint(HttpServletRequest request) {
        return StrUtil.blankToDefault(request == null ? "" : request.getHeader(DEVICE_FINGERPRINT_HEADER), "").trim();
    }

    private String resolveRiskLevel(HttpServletRequest request) {
        Object value = request == null ? null : request.getAttribute(PREAUTH_RISK_LEVEL_ATTRIBUTE);
        return value instanceof String riskLevel && StrUtil.isNotBlank(riskLevel) ? riskLevel.trim() : "L1";
    }

    private boolean isPasswordResetWafResumeRequest(HttpServletRequest request) {
        return request != null
                && "1".equals(StrUtil.blankToDefault(request.getHeader(PASSWORD_RESET_WAF_RESUME_HEADER), "").trim());
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StrUtil.isNotBlank(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (StrUtil.isNotBlank(realIp)) {
            return realIp.trim();
        }
        return StrUtil.blankToDefault(request.getRemoteAddr(), "");
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

    private void ensurePasswordResetChallengeAlive(String email,
                                                   String deviceFingerprint,
                                                   String challengeType,
                                                   String challengeSubType) {
        if (!passwordResetEmailCaptchaRiskGateService.refreshPendingChallengeSelection(
                email,
                deviceFingerprint,
                challengeType,
                challengeSubType)) {
            throw new IllegalArgumentException("Current password reset challenge has expired, please resubmit.");
        }
    }

    private String resolveCookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request == null ? null : request.getCookies();
        if (cookies == null) {
            return "";
        }
        for (Cookie cookie : cookies) {
            if (cookie != null && name.equals(cookie.getName())) {
                return StrUtil.blankToDefault(cookie.getValue(), "").trim();
            }
        }
        return "";
    }

    private ResponseCookie buildCodeTokenCookie(String token) {
        return ResponseCookie.from(PASSWORD_RESET_CODE_TOKEN_COOKIE, token)
                .path(PASSWORD_RESET_COOKIE_PATH)
                .maxAge(PASSWORD_RESET_CODE_COOKIE_TTL)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .build();
    }

    private ResponseCookie clearCodeTokenCookie() {
        return ResponseCookie.from(PASSWORD_RESET_CODE_TOKEN_COOKIE, "")
                .path(PASSWORD_RESET_COOKIE_PATH)
                .maxAge(Duration.ZERO)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .build();
    }
}
