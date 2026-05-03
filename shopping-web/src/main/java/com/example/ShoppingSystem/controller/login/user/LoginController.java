package com.example.ShoppingSystem.controller.login.user;

import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.controller.login.user.dto.LoginEmailCodeVerifyRequest;
import com.example.ShoppingSystem.controller.login.user.dto.LoginFlowResponse;
import com.example.ShoppingSystem.controller.login.user.dto.LoginFlowStartRequest;
import com.example.ShoppingSystem.controller.login.user.dto.LoginPasswordVerifyRequest;
import com.example.ShoppingSystem.controller.login.user.dto.LoginPhoneBindRequest;
import com.example.ShoppingSystem.controller.login.user.dto.LoginTotpVerifyRequest;
import com.example.ShoppingSystem.controller.login.user.dto.RegisterCaptchaResponse;
import com.example.ShoppingSystem.controller.login.user.dto.TianaiRotateCaptchaResponse;
import com.example.ShoppingSystem.controller.login.user.dto.TianaiRotateCheckRequest;
import com.example.ShoppingSystem.controller.login.user.dto.TianaiSimpleCheckResponse;
import com.example.ShoppingSystem.filter.preauth.PreAuthBindingService;
import com.example.ShoppingSystem.loginflow.LoginFlowCookieFactory;
import com.example.ShoppingSystem.loginflow.LoginFlowWebSupport;
import com.example.ShoppingSystem.service.captcha.hutool.HutoolCaptchaService;
import com.example.ShoppingSystem.service.captcha.hutool.model.HutoolCaptchaResult;
import com.example.ShoppingSystem.service.captcha.tianai.TianaiCaptchaService;
import com.example.ShoppingSystem.service.user.auth.login.LoginFlowSessionService;
import com.example.ShoppingSystem.service.user.auth.login.UserPasswordLoginService;
import com.example.ShoppingSystem.service.user.auth.login.model.LoginFlowSession;
import com.example.ShoppingSystem.service.user.auth.login.model.LoginFlowValidationResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "login", description = "login flow, step guard, and factor verification")
@RestController
@RequestMapping("/shopping/user/login")
public class LoginController {

    private static final String AUTH_USER_ID_SESSION_ATTRIBUTE = "AUTH_USER_ID";
    private static final String CHALLENGE_HUTOOL_SHEAR = "HUTOOL_SHEAR_CAPTCHA";
    private static final String CHALLENGE_TIANAI = "TIANAI_CAPTCHA";
    private static final String TIANAI_SUBTYPE_SLIDER = "SLIDER";
    private static final String TIANAI_SUBTYPE_ROTATE = "ROTATE";
    private static final String TIANAI_SUBTYPE_CONCAT = "CONCAT";
    private static final String TIANAI_SUBTYPE_WORD_IMAGE_CLICK = "WORD_IMAGE_CLICK";
    private static final String ERROR_INVALID_STATE = "INVALID_STATE";

    private final UserPasswordLoginService userPasswordLoginService;
    private final LoginFlowSessionService loginFlowSessionService;
    private final LoginFlowCookieFactory loginFlowCookieFactory;
    private final PreAuthBindingService preAuthBindingService;
    private final HutoolCaptchaService hutoolCaptchaService;
    private final TianaiCaptchaService tianaiCaptchaService;

    public LoginController(UserPasswordLoginService userPasswordLoginService,
                           LoginFlowSessionService loginFlowSessionService,
                           LoginFlowCookieFactory loginFlowCookieFactory,
                           PreAuthBindingService preAuthBindingService,
                           HutoolCaptchaService hutoolCaptchaService,
                           TianaiCaptchaService tianaiCaptchaService) {
        this.userPasswordLoginService = userPasswordLoginService;
        this.loginFlowSessionService = loginFlowSessionService;
        this.loginFlowCookieFactory = loginFlowCookieFactory;
        this.preAuthBindingService = preAuthBindingService;
        this.hutoolCaptchaService = hutoolCaptchaService;
        this.tianaiCaptchaService = tianaiCaptchaService;
    }

    @Operation(summary = "Start login flow after identifier entry and risk challenge resolution.")
    @PostMapping("/flow/start")
    public ResponseEntity<LoginFlowResponse> startLoginFlow(@RequestBody LoginFlowStartRequest body,
                                                            HttpServletRequest request,
                                                            HttpServletResponse response) {
        clearFlowCookie(response, request);
        String preAuthToken = resolvePreAuthToken(request);
        if (StrUtil.isBlank(preAuthToken)) {
            return invalidFlowResponse(HttpStatus.UNAUTHORIZED,
                    "Preauth session is missing, please refresh and try again.",
                    request,
                    response);
        }

        LoginFlowResponse responseBody = LoginFlowResponse.fromStart(
                userPasswordLoginService.startFlow(
                        body.email(),
                        body.deviceFingerprint(),
                        preAuthToken,
                        resolveRiskLevel(request),
                        resolveClientIp(request),
                        body.captchaUuid(),
                        body.captchaCode()
                )
        );
        if (responseBody.success() && StrUtil.isNotBlank(responseBody.flowId())) {
            response.addHeader("Set-Cookie", loginFlowCookieFactory.buildFlowCookie(responseBody.flowId(), request).toString());
        }
        return ResponseEntity.ok(responseBody);
    }

    @Operation(summary = "Get current login flow state.")
    @GetMapping("/flow/current")
    public ResponseEntity<LoginFlowResponse> currentLoginFlow(HttpServletRequest request,
                                                              HttpServletResponse response) {
        LoginFlowSession session = requireFlowSession(request, response);
        if (session == null) {
            return invalidFlowResponse(HttpStatus.GONE,
                    "Login session expired, please restart.",
                    request,
                    response);
        }
        return ResponseEntity.ok(LoginFlowResponse.fromSession(session));
    }

    @Operation(summary = "Generate login Hutool captcha.")
    @GetMapping("/hutoolcaptcha")
    public ResponseEntity<?> getLoginCaptcha(@RequestParam(required = false) String uuid,
                                             @RequestParam(required = false) String email,
                                             @RequestParam(required = false) String deviceFingerprint) throws Exception {
        ensureLoginChallengeAlive(email, deviceFingerprint, CHALLENGE_HUTOOL_SHEAR, null);
        HutoolCaptchaResult result = hutoolCaptchaService.generateCaptcha("login", uuid);
        return ResponseEntity.ok(RegisterCaptchaResponse.builder()
                .uuid(result.getUuid())
                .image(result.getImage())
                .build());
    }

    @Operation(summary = "Get login Tianai rotate captcha.")
    @GetMapping("/tianai/rotate")
    public ResponseEntity<?> getLoginTianaiRotateCaptcha(@RequestParam(required = false) String captchaId,
                                                         @RequestParam(required = false) String email,
                                                         @RequestParam(required = false) String deviceFingerprint) {
        ensureLoginChallengeAlive(email, deviceFingerprint, CHALLENGE_TIANAI, TIANAI_SUBTYPE_ROTATE);
        return ResponseEntity.ok(TianaiRotateCaptchaResponse.from(tianaiCaptchaService.generateRotateCaptcha(captchaId)));
    }

    @Operation(summary = "Get login Tianai slider captcha.")
    @GetMapping("/tianai/slider")
    public ResponseEntity<?> getLoginTianaiSliderCaptcha(@RequestParam(required = false) String captchaId,
                                                         @RequestParam(required = false) String email,
                                                         @RequestParam(required = false) String deviceFingerprint) {
        ensureLoginChallengeAlive(email, deviceFingerprint, CHALLENGE_TIANAI, TIANAI_SUBTYPE_SLIDER);
        return ResponseEntity.ok(TianaiRotateCaptchaResponse.from(tianaiCaptchaService.generateSliderCaptcha(captchaId)));
    }

    @Operation(summary = "Get login Tianai concat captcha.")
    @GetMapping("/tianai/concat")
    public ResponseEntity<?> getLoginTianaiConcatCaptcha(@RequestParam(required = false) String captchaId,
                                                         @RequestParam(required = false) String email,
                                                         @RequestParam(required = false) String deviceFingerprint) {
        ensureLoginChallengeAlive(email, deviceFingerprint, CHALLENGE_TIANAI, TIANAI_SUBTYPE_CONCAT);
        return ResponseEntity.ok(TianaiRotateCaptchaResponse.from(tianaiCaptchaService.generateConcatCaptcha(captchaId)));
    }

    @Operation(summary = "Get login Tianai word click captcha.")
    @GetMapping("/tianai/word-click")
    public ResponseEntity<?> getLoginTianaiWordClickCaptcha(@RequestParam(required = false) String captchaId,
                                                            @RequestParam(required = false) String email,
                                                            @RequestParam(required = false) String deviceFingerprint) {
        ensureLoginChallengeAlive(email, deviceFingerprint, CHALLENGE_TIANAI, TIANAI_SUBTYPE_WORD_IMAGE_CLICK);
        return ResponseEntity.ok(TianaiRotateCaptchaResponse.from(tianaiCaptchaService.generateWordClickCaptcha(captchaId)));
    }

    @Operation(summary = "Verify Tianai rotate captcha.")
    @PostMapping("/tianai/rotate/check")
    public TianaiSimpleCheckResponse checkLoginTianaiRotateCaptcha(@RequestBody TianaiRotateCheckRequest body) {
        return new TianaiSimpleCheckResponse(tianaiCaptchaService.validateRotateCaptcha(body.getCaptchaId(), body.getAngle()));
    }

    @Operation(summary = "Verify password factor.")
    @PostMapping("/password")
    public ResponseEntity<LoginFlowResponse> verifyPassword(@RequestBody LoginPasswordVerifyRequest body,
                                                            HttpServletRequest request,
                                                            HttpServletResponse response) {
        LoginFlowSession session = requireFlowSession(request, response);
        if (session == null) {
            return invalidFlowResponse(HttpStatus.GONE, "Login session expired, please restart.", request, response);
        }
        LoginFlowResponse responseBody = LoginFlowResponse.fromVerify(
                userPasswordLoginService.verifyPassword(session.getFlowId(), resolvePreAuthToken(request), body.password())
        );
        clearFlowCookieOnInvalidState(responseBody, request, response);
        onAuthenticationCompleted(responseBody, request, response);
        return ResponseEntity.status(responseBody.success() ? HttpStatus.OK : HttpStatus.BAD_REQUEST).body(responseBody);
    }

    @Operation(summary = "Send login email code.")
    @PostMapping("/email-code")
    public ResponseEntity<LoginFlowResponse> sendEmailCode(HttpServletRequest request,
                                                           HttpServletResponse response) {
        LoginFlowSession session = requireFlowSession(request, response);
        if (session == null) {
            return invalidFlowResponse(HttpStatus.GONE, "Login session expired, please restart.", request, response);
        }
        LoginFlowResponse responseBody = LoginFlowResponse.fromVerify(
                userPasswordLoginService.sendEmailCode(session.getFlowId(), resolvePreAuthToken(request))
        );
        clearFlowCookieOnInvalidState(responseBody, request, response);
        return ResponseEntity.status(responseBody.success() ? HttpStatus.OK : HttpStatus.BAD_REQUEST).body(responseBody);
    }

    @Operation(summary = "Verify login email code factor.")
    @PostMapping("/email-code/verify")
    public ResponseEntity<LoginFlowResponse> verifyEmailCode(@RequestBody LoginEmailCodeVerifyRequest body,
                                                             HttpServletRequest request,
                                                             HttpServletResponse response) {
        LoginFlowSession session = requireFlowSession(request, response);
        if (session == null) {
            return invalidFlowResponse(HttpStatus.GONE, "Login session expired, please restart.", request, response);
        }
        LoginFlowResponse responseBody = LoginFlowResponse.fromVerify(
                userPasswordLoginService.verifyEmailCode(session.getFlowId(), resolvePreAuthToken(request), body.emailCode())
        );
        clearFlowCookieOnInvalidState(responseBody, request, response);
        onAuthenticationCompleted(responseBody, request, response);
        return ResponseEntity.status(responseBody.success() ? HttpStatus.OK : HttpStatus.BAD_REQUEST).body(responseBody);
    }

    @Operation(summary = "Verify TOTP factor.")
    @PostMapping("/totp/verify")
    public ResponseEntity<LoginFlowResponse> verifyTotp(@RequestBody LoginTotpVerifyRequest body,
                                                        HttpServletRequest request,
                                                        HttpServletResponse response) {
        LoginFlowSession session = requireFlowSession(request, response);
        if (session == null) {
            return invalidFlowResponse(HttpStatus.GONE, "Login session expired, please restart.", request, response);
        }
        LoginFlowResponse responseBody = LoginFlowResponse.fromVerify(
                userPasswordLoginService.verifyTotp(session.getFlowId(), resolvePreAuthToken(request), body.code())
        );
        clearFlowCookieOnInvalidState(responseBody, request, response);
        onAuthenticationCompleted(responseBody, request, response);
        return ResponseEntity.status(responseBody.success() ? HttpStatus.OK : HttpStatus.BAD_REQUEST).body(responseBody);
    }

    @Operation(summary = "Check phone login candidate through counting bloom filter.")
    @PostMapping("/phone/check")
    public ResponseEntity<LoginFlowResponse> checkPhoneLoginCandidate(@RequestBody LoginPhoneBindRequest body) {
        LoginFlowResponse responseBody = LoginFlowResponse.fromVerify(
                userPasswordLoginService.checkPhoneLoginCandidate(body.dialCode(), body.phoneNumber())
        );
        return ResponseEntity.status(responseBody.success() ? HttpStatus.OK : HttpStatus.BAD_REQUEST).body(responseBody);
    }

    @Operation(summary = "Bind required phone after risky login.")
    @PostMapping("/phone/code")
    public ResponseEntity<LoginFlowResponse> sendPhoneBindCode(@RequestBody LoginPhoneBindRequest body,
                                                               HttpServletRequest request,
                                                               HttpServletResponse response) {
        LoginFlowSession session = requireFlowSession(request, response);
        if (session == null) {
            return invalidFlowResponse(HttpStatus.GONE, "Login session expired, please restart.", request, response);
        }
        LoginFlowResponse responseBody = LoginFlowResponse.fromVerify(
                userPasswordLoginService.sendPhoneBindCode(
                        session.getFlowId(),
                        resolvePreAuthToken(request),
                        body.dialCode(),
                        body.phoneNumber(),
                        resolveClientIp(request),
                        resolveRiskLevel(request),
                        resolveDeviceFingerprint(request),
                        body.captchaUuid(),
                        body.captchaCode()
                )
        );
        clearFlowCookieOnInvalidState(responseBody, request, response);
        return ResponseEntity.status(responseBody.success() ? HttpStatus.OK : HttpStatus.BAD_REQUEST).body(responseBody);
    }

    @Operation(summary = "Send SMS code for phone-first login after SMS risk challenge.")
    @PostMapping("/phone-login/code")
    public ResponseEntity<LoginFlowResponse> sendPhoneLoginCode(@RequestBody LoginPhoneBindRequest body,
                                                                HttpServletRequest request) {
        LoginFlowResponse responseBody = LoginFlowResponse.fromVerify(
                userPasswordLoginService.sendPhoneLoginCode(
                        resolvePreAuthToken(request),
                        body.dialCode(),
                        body.phoneNumber(),
                        resolveClientIp(request),
                        resolveRiskLevel(request),
                        resolveDeviceFingerprint(request),
                        body.captchaUuid(),
                        body.captchaCode()
                )
        );
        return ResponseEntity.status(responseBody.success() ? HttpStatus.OK : HttpStatus.BAD_REQUEST).body(responseBody);
    }

    @Operation(summary = "Bind required phone after SMS verification.")
    @PostMapping("/phone/bind")
    public ResponseEntity<LoginFlowResponse> bindPhone(@RequestBody LoginPhoneBindRequest body,
                                                       HttpServletRequest request,
                                                       HttpServletResponse response) {
        LoginFlowSession session = requireFlowSession(request, response);
        if (session == null) {
            return invalidFlowResponse(HttpStatus.GONE, "Login session expired, please restart.", request, response);
        }
        LoginFlowResponse responseBody = LoginFlowResponse.fromVerify(
                userPasswordLoginService.bindVerifiedPhone(
                        session.getFlowId(),
                        resolvePreAuthToken(request),
                        body.dialCode(),
                        body.phoneNumber(),
                        body.smsCode()
                )
        );
        clearFlowCookieOnInvalidState(responseBody, request, response);
        onAuthenticationCompleted(responseBody, request, response);
        return ResponseEntity.status(responseBody.success() ? HttpStatus.OK : HttpStatus.BAD_REQUEST).body(responseBody);
    }

    private LoginFlowSession requireFlowSession(HttpServletRequest request, HttpServletResponse response) {
        String flowId = loginFlowCookieFactory.resolveFlowId(request);
        String preAuthToken = resolvePreAuthToken(request);
        LoginFlowValidationResult validationResult = loginFlowSessionService.validate(flowId, preAuthToken);
        if (!validationResult.valid()) {
            clearFlowCookie(response, request);
            return null;
        }
        return validationResult.session();
    }

    private ResponseEntity<LoginFlowResponse> invalidFlowResponse(HttpStatus status,
                                                                  String message,
                                                                  HttpServletRequest request,
                                                                  HttpServletResponse response) {
        clearFlowCookie(response, request);
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(LoginFlowResponse.invalidSession(message, LoginFlowWebSupport.sessionEndedWithNotice()));
    }

    private void onAuthenticationCompleted(LoginFlowResponse responseBody,
                                           HttpServletRequest request,
                                           HttpServletResponse response) {
        if (!Boolean.TRUE.equals(responseBody.authenticated()) || responseBody.userId() == null) {
            return;
        }
        HttpSession session = request.getSession(true);
        session.setAttribute(AUTH_USER_ID_SESSION_ATTRIBUTE, responseBody.userId());
        clearFlowCookie(response, request);
    }

    private void clearFlowCookieOnInvalidState(LoginFlowResponse responseBody,
                                               HttpServletRequest request,
                                               HttpServletResponse response) {
        if (responseBody != null && ERROR_INVALID_STATE.equals(responseBody.error())) {
            clearFlowCookie(response, request);
        }
    }

    private void ensureLoginChallengeAlive(String email,
                                           String deviceFingerprint,
                                           String expectedChallengeType,
                                           String expectedChallengeSubType) {
        if (!userPasswordLoginService.refreshPendingChallengeSelection(
                email,
                deviceFingerprint,
                expectedChallengeType,
                expectedChallengeSubType)) {
            throw new IllegalArgumentException("Current login challenge has expired, please resubmit.");
        }
    }

    private void clearFlowCookie(HttpServletResponse response, HttpServletRequest request) {
        response.addHeader("Set-Cookie", loginFlowCookieFactory.buildExpiredFlowCookie(request).toString());
    }

    private String resolvePreAuthToken(HttpServletRequest request) {
        Object requestAttribute = request.getAttribute("preAuthToken");
        if (requestAttribute instanceof String token && StrUtil.isNotBlank(token)) {
            return token.trim();
        }
        return preAuthBindingService.resolveIncomingToken(request);
    }

    private String resolveRiskLevel(HttpServletRequest request) {
        Object requestAttribute = request.getAttribute("preAuthRiskLevel");
        if (requestAttribute instanceof String riskLevel && StrUtil.isNotBlank(riskLevel)) {
            return riskLevel.trim();
        }
        return "L1";
    }

    private String resolveDeviceFingerprint(HttpServletRequest request) {
        String fingerprint = request.getHeader("X-Device-Fingerprint");
        return StrUtil.blankToDefault(fingerprint, "").trim();
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
