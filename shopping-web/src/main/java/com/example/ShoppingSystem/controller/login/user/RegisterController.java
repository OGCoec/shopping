package com.example.ShoppingSystem.controller.login.user;

import cn.hutool.core.lang.Validator;
import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.controller.login.user.dto.LoginPhoneBindRequest;
import com.example.ShoppingSystem.controller.login.user.dto.RegisterCaptchaResponse;
import com.example.ShoppingSystem.controller.login.user.dto.RegisterFlowStartRequest;
import com.example.ShoppingSystem.controller.login.user.dto.RegisterFlowStartResponse;
import com.example.ShoppingSystem.controller.login.user.dto.RegisterFlowStateResponse;
import com.example.ShoppingSystem.controller.login.user.dto.RegisterPhoneBindingResponse;
import com.example.ShoppingSystem.controller.login.user.dto.RegisterSendEmailCodeRequest;
import com.example.ShoppingSystem.controller.login.user.dto.RegisterSendEmailCodeResponse;
import com.example.ShoppingSystem.controller.login.user.dto.RegisterVerifyEmailCodeRequest;
import com.example.ShoppingSystem.controller.login.user.dto.RegisterVerifyEmailCodeResponse;
import com.example.ShoppingSystem.controller.login.user.dto.TianaiRotateCaptchaResponse;
import com.example.ShoppingSystem.controller.login.user.dto.TianaiRotateCheckRequest;
import com.example.ShoppingSystem.controller.login.user.dto.TianaiSimpleCheckResponse;
import com.example.ShoppingSystem.filter.preauth.PreAuthBindingService;
import com.example.ShoppingSystem.registerflow.RegisterFlowCookieFactory;
import com.example.ShoppingSystem.registerflow.RegisterFlowErrorResponse;
import com.example.ShoppingSystem.registerflow.RegisterFlowWebSupport;
import com.example.ShoppingSystem.security.RegisterPasswordCryptoService;
import com.example.ShoppingSystem.service.captcha.hutool.HutoolCaptchaService;
import com.example.ShoppingSystem.service.captcha.hutool.model.HutoolCaptchaResult;
import com.example.ShoppingSystem.service.captcha.tianai.TianaiCaptchaService;
import com.example.ShoppingSystem.service.user.auth.register.RegisterCompletionService;
import com.example.ShoppingSystem.service.user.auth.register.RegisterFlowSessionService;
import com.example.ShoppingSystem.service.user.auth.register.RegisterPhoneBindingService;
import com.example.ShoppingSystem.service.user.auth.register.RegisterPrecheckService;
import com.example.ShoppingSystem.service.user.auth.register.model.RegisterCompletionResult;
import com.example.ShoppingSystem.service.user.auth.register.model.RegisterFlowSession;
import com.example.ShoppingSystem.service.user.auth.register.model.RegisterFlowStep;
import com.example.ShoppingSystem.service.user.auth.register.model.RegisterFlowValidationResult;
import com.example.ShoppingSystem.service.user.auth.register.model.RegisterPhoneBindingResult;
import com.example.ShoppingSystem.service.user.auth.register.model.RegisterPrecheckResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Set;

@Tag(name = "register", description = "register flow precheck, step guard, and email verification")
@RestController
@RequestMapping("/shopping/user/register")
public class RegisterController {

    private static final String CHALLENGE_HUTOOL_SHEAR = "HUTOOL_SHEAR_CAPTCHA";
    private static final String CHALLENGE_TIANAI = "TIANAI_CAPTCHA";
    private static final String TIANAI_SUBTYPE_SLIDER = "SLIDER";
    private static final String TIANAI_SUBTYPE_ROTATE = "ROTATE";
    private static final String TIANAI_SUBTYPE_CONCAT = "CONCAT";
    private static final String TIANAI_SUBTYPE_WORD_IMAGE_CLICK = "WORD_IMAGE_CLICK";

    private final RegisterPrecheckService registerPrecheckService;
    private final RegisterCompletionService registerCompletionService;
    private final RegisterFlowSessionService registerFlowSessionService;
    private final RegisterPhoneBindingService registerPhoneBindingService;
    private final HutoolCaptchaService hutoolCaptchaService;
    private final TianaiCaptchaService tianaiCaptchaService;
    private final RegisterPasswordCryptoService registerPasswordCryptoService;
    private final RegisterFlowCookieFactory registerFlowCookieFactory;
    private final PreAuthBindingService preAuthBindingService;

    public RegisterController(RegisterPrecheckService registerPrecheckService,
                              RegisterCompletionService registerCompletionService,
                              RegisterFlowSessionService registerFlowSessionService,
                              RegisterPhoneBindingService registerPhoneBindingService,
                              HutoolCaptchaService hutoolCaptchaService,
                              TianaiCaptchaService tianaiCaptchaService,
                              RegisterPasswordCryptoService registerPasswordCryptoService,
                              RegisterFlowCookieFactory registerFlowCookieFactory,
                              PreAuthBindingService preAuthBindingService) {
        this.registerPrecheckService = registerPrecheckService;
        this.registerCompletionService = registerCompletionService;
        this.registerFlowSessionService = registerFlowSessionService;
        this.registerPhoneBindingService = registerPhoneBindingService;
        this.hutoolCaptchaService = hutoolCaptchaService;
        this.tianaiCaptchaService = tianaiCaptchaService;
        this.registerPasswordCryptoService = registerPasswordCryptoService;
        this.registerFlowCookieFactory = registerFlowCookieFactory;
        this.preAuthBindingService = preAuthBindingService;
    }

    @Operation(summary = "Start or restart the server-side register flow after the email step.")
    @PostMapping("/flow/start")
    public ResponseEntity<?> startRegisterFlow(@RequestBody RegisterFlowStartRequest request,
                                               HttpServletRequest httpServletRequest,
                                               HttpServletResponse httpServletResponse) {
        String email = normalizeEmail(request.getEmail());
        if (!Validator.isEmail(email)) {
            return ResponseEntity.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new RegisterFlowStartResponse(false, "Please enter a valid email address.", null));
        }
        String deviceFingerprint = normalizeText(request.getDeviceFingerprint());
        if (StrUtil.isBlank(deviceFingerprint)) {
            return ResponseEntity.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new RegisterFlowStartResponse(false, "Device fingerprint is required.", null));
        }

        String preAuthToken = resolvePreAuthToken(httpServletRequest);
        if (StrUtil.isBlank(preAuthToken)) {
            return buildRegisterFlowError(
                    HttpStatus.UNAUTHORIZED,
                    "PREAUTH_MISSING",
                    "Preauth session is missing, please refresh and try again.",
                    httpServletRequest,
                    httpServletResponse,
                    RegisterFlowWebSupport.sessionEndedWithNotice(),
                    RegisterFlowWebSupport.NOTICE_FLOW_EXPIRED,
                    false
            );
        }

        RegisterFlowSession session = registerFlowSessionService.startFlow(email, deviceFingerprint, preAuthToken);
        httpServletResponse.addHeader("Set-Cookie", registerFlowCookieFactory.buildFlowCookie(session.getFlowId(), httpServletRequest).toString());
        return ResponseEntity.ok(new RegisterFlowStartResponse(
                true,
                "ok",
                RegisterFlowWebSupport.CREATE_ACCOUNT_PASSWORD_PATH
        ));
    }

    @Operation(summary = "Read current server-side register flow state.")
    @GetMapping("/flow/current")
    public ResponseEntity<?> getCurrentRegisterFlow(HttpServletRequest request,
                                                    HttpServletResponse response) {
        Object flowGuard = requireRegisterFlow(
                request,
                response,
                null,
                null
        );
        if (flowGuard instanceof ResponseEntity<?> errorResponse) {
            return errorResponse;
        }
        RegisterFlowSession session = (RegisterFlowSession) flowGuard;
        return ResponseEntity.ok(new RegisterFlowStateResponse(
                true,
                "ok",
                session.getEmail(),
                session.getStep() == null ? "" : session.getStep().name(),
                session.getRiskLevel(),
                session.isRequirePhoneBinding(),
                session.isCompleted()
        ));
    }

    @Operation(summary = "Get register captcha image")
    @GetMapping("/hutoolcaptcha")
    public ResponseEntity<?> getRegisterCaptcha(@RequestParam(required = false) String uuid,
                                                @RequestParam(required = false) String email,
                                                @RequestParam(required = false) String deviceFingerprint,
                                                HttpServletRequest request,
                                                HttpServletResponse response) throws Exception {
        Object flowGuard = requireRegisterFlow(
                request,
                response,
                email,
                deviceFingerprint,
                RegisterFlowStep.PASSWORD,
                RegisterFlowStep.EMAIL_VERIFICATION
        );
        if (flowGuard instanceof ResponseEntity<?> errorResponse) {
            return errorResponse;
        }

        if (!registerPrecheckService.refreshPendingChallengeSelection(
                email,
                deviceFingerprint,
                CHALLENGE_HUTOOL_SHEAR,
                null)) {
            throw new IllegalArgumentException("Current register challenge has expired, please resubmit.");
        }

        HutoolCaptchaResult result = hutoolCaptchaService.generateCaptcha("register", uuid);
        return ResponseEntity.ok(RegisterCaptchaResponse.builder()
                .uuid(result.getUuid())
                .image(result.getImage())
                .build());
    }

    @Operation(summary = "Get Tianai rotate captcha")
    @GetMapping("/tianai/rotate")
    public ResponseEntity<?> getTianaiRotateCaptcha(@RequestParam(required = false) String captchaId,
                                                    @RequestParam(required = false) String email,
                                                    @RequestParam(required = false) String deviceFingerprint,
                                                    HttpServletRequest request,
                                                    HttpServletResponse response) {
        Object flowGuard = requireRegisterFlow(
                request,
                response,
                email,
                deviceFingerprint,
                RegisterFlowStep.PASSWORD,
                RegisterFlowStep.EMAIL_VERIFICATION
        );
        if (flowGuard instanceof ResponseEntity<?> errorResponse) {
            return errorResponse;
        }
        ensureRegisterChallengeAlive(email, deviceFingerprint, CHALLENGE_TIANAI, TIANAI_SUBTYPE_ROTATE);
        return ResponseEntity.ok(TianaiRotateCaptchaResponse.from(tianaiCaptchaService.generateRotateCaptcha(captchaId)));
    }

    @Operation(summary = "Get Tianai slider captcha")
    @GetMapping("/tianai/slider")
    public ResponseEntity<?> getTianaiSliderCaptcha(@RequestParam(required = false) String captchaId,
                                                    @RequestParam(required = false) String email,
                                                    @RequestParam(required = false) String deviceFingerprint,
                                                    HttpServletRequest request,
                                                    HttpServletResponse response) {
        Object flowGuard = requireRegisterFlow(
                request,
                response,
                email,
                deviceFingerprint,
                RegisterFlowStep.PASSWORD,
                RegisterFlowStep.EMAIL_VERIFICATION
        );
        if (flowGuard instanceof ResponseEntity<?> errorResponse) {
            return errorResponse;
        }
        ensureRegisterChallengeAlive(email, deviceFingerprint, CHALLENGE_TIANAI, TIANAI_SUBTYPE_SLIDER);
        return ResponseEntity.ok(TianaiRotateCaptchaResponse.from(tianaiCaptchaService.generateSliderCaptcha(captchaId)));
    }

    @Operation(summary = "Get Tianai concat captcha")
    @GetMapping("/tianai/concat")
    public ResponseEntity<?> getTianaiConcatCaptcha(@RequestParam(required = false) String captchaId,
                                                    @RequestParam(required = false) String email,
                                                    @RequestParam(required = false) String deviceFingerprint,
                                                    HttpServletRequest request,
                                                    HttpServletResponse response) {
        Object flowGuard = requireRegisterFlow(
                request,
                response,
                email,
                deviceFingerprint,
                RegisterFlowStep.PASSWORD,
                RegisterFlowStep.EMAIL_VERIFICATION
        );
        if (flowGuard instanceof ResponseEntity<?> errorResponse) {
            return errorResponse;
        }
        ensureRegisterChallengeAlive(email, deviceFingerprint, CHALLENGE_TIANAI, TIANAI_SUBTYPE_CONCAT);
        return ResponseEntity.ok(TianaiRotateCaptchaResponse.from(tianaiCaptchaService.generateConcatCaptcha(captchaId)));
    }

    @Operation(summary = "Get Tianai word click captcha")
    @GetMapping("/tianai/word-click")
    public ResponseEntity<?> getTianaiWordClickCaptcha(@RequestParam(required = false) String captchaId,
                                                       @RequestParam(required = false) String email,
                                                       @RequestParam(required = false) String deviceFingerprint,
                                                       HttpServletRequest request,
                                                       HttpServletResponse response) {
        Object flowGuard = requireRegisterFlow(
                request,
                response,
                email,
                deviceFingerprint,
                RegisterFlowStep.PASSWORD,
                RegisterFlowStep.EMAIL_VERIFICATION
        );
        if (flowGuard instanceof ResponseEntity<?> errorResponse) {
            return errorResponse;
        }
        ensureRegisterChallengeAlive(email, deviceFingerprint, CHALLENGE_TIANAI, TIANAI_SUBTYPE_WORD_IMAGE_CLICK);
        return ResponseEntity.ok(TianaiRotateCaptchaResponse.from(tianaiCaptchaService.generateWordClickCaptcha(captchaId)));
    }

    @Operation(summary = "Verify Tianai rotate captcha")
    @PostMapping("/tianai/rotate/check")
    public TianaiSimpleCheckResponse checkTianaiRotateCaptcha(@RequestBody TianaiRotateCheckRequest request) {
        return new TianaiSimpleCheckResponse(tianaiCaptchaService.validateRotateCaptcha(request.getCaptchaId(), request.getAngle()));
    }

    @Operation(summary = "Resolve whether register needs a challenge before sending email code")
    @PostMapping("/email-code-type")
    public ResponseEntity<?> resolveRegisterEmailCodeChallenge(@RequestBody RegisterSendEmailCodeRequest request,
                                                               HttpServletRequest httpServletRequest,
                                                               HttpServletResponse httpServletResponse) {
        Object flowGuard = requireRegisterFlow(
                httpServletRequest,
                httpServletResponse,
                request.getEmail(),
                request.getDeviceFingerprint(),
                RegisterFlowStep.PASSWORD
        );
        if (flowGuard instanceof ResponseEntity<?> errorResponse) {
            return errorResponse;
        }
        RegisterFlowSession flowSession = (RegisterFlowSession) flowGuard;

        RegisterPasswordCryptoService.DecryptOutcome decryptOutcome = registerPasswordCryptoService.decryptPasswordCipher(
                request.getKid(),
                request.getPasswordCipher(),
                request.getNonce(),
                request.getTimestamp(),
                httpServletRequest
        );
        if (!decryptOutcome.success()) {
            return ResponseEntity.ok(buildCryptoFailedResponse(decryptOutcome.message()));
        }

        String clientIp = resolveClientIp(httpServletRequest);
        RegisterPrecheckResult result = registerPrecheckService.resolveRegisterEmailCodeChallenge(
                request.getEmail(),
                request.getUsername(),
                decryptOutcome.rawPassword(),
                request.getDeviceFingerprint(),
                clientIp
        );
        registerFlowSessionService.updateStep(
                flowSession.getFlowId(),
                RegisterFlowStep.PASSWORD,
                result.getRiskLevel(),
                result.isRequirePhoneBinding(),
                false
        );
        return ResponseEntity.ok(toResponse(result));
    }

    @Operation(summary = "Send register email code")
    @PostMapping("/email-code")
    public ResponseEntity<?> sendRegisterEmailCode(@RequestBody RegisterSendEmailCodeRequest request,
                                                   HttpServletRequest httpServletRequest,
                                                   HttpServletResponse httpServletResponse) {
        Object flowGuard = requireRegisterFlow(
                httpServletRequest,
                httpServletResponse,
                request.getEmail(),
                request.getDeviceFingerprint(),
                RegisterFlowStep.PASSWORD,
                RegisterFlowStep.EMAIL_VERIFICATION
        );
        if (flowGuard instanceof ResponseEntity<?> errorResponse) {
            return errorResponse;
        }
        RegisterFlowSession flowSession = (RegisterFlowSession) flowGuard;

        RegisterPasswordCryptoService.DecryptOutcome decryptOutcome = registerPasswordCryptoService.decryptPasswordCipher(
                request.getKid(),
                request.getPasswordCipher(),
                request.getNonce(),
                request.getTimestamp(),
                httpServletRequest
        );
        if (!decryptOutcome.success()) {
            return ResponseEntity.ok(buildCryptoFailedResponse(decryptOutcome.message()));
        }

        String clientIp = resolveClientIp(httpServletRequest);
        RegisterPrecheckResult result = registerPrecheckService.sendRegisterEmailCodeAfterCaptcha(
                flowSession.getFlowId(),
                flowSession.getStep() == RegisterFlowStep.EMAIL_VERIFICATION,
                request.getEmail(),
                request.getUsername(),
                decryptOutcome.rawPassword(),
                request.getDeviceFingerprint(),
                clientIp,
                request.getCaptchaUuid(),
                request.getCaptchaCode()
        );

        RegisterFlowStep nextStep = result.isSuccess() && result.isEmailCodeSent()
                ? RegisterFlowStep.EMAIL_VERIFICATION
                : flowSession.getStep();
        registerFlowSessionService.updateStep(
                flowSession.getFlowId(),
                nextStep,
                result.getRiskLevel(),
                result.isRequirePhoneBinding(),
                false
        );
        return ResponseEntity.ok(toResponse(result));
    }

    @Operation(summary = "Verify register email code and continue register flow")
    @PostMapping("/email-code/verify")
    public ResponseEntity<?> verifyRegisterEmailCode(@RequestBody RegisterVerifyEmailCodeRequest request,
                                                     HttpServletRequest httpServletRequest,
                                                     HttpServletResponse httpServletResponse) {
        Object flowGuard = requireRegisterFlow(
                httpServletRequest,
                httpServletResponse,
                request.getEmail(),
                request.getDeviceFingerprint(),
                RegisterFlowStep.EMAIL_VERIFICATION
        );
        if (flowGuard instanceof ResponseEntity<?> errorResponse) {
            return errorResponse;
        }
        RegisterFlowSession flowSession = (RegisterFlowSession) flowGuard;

        String clientIp = resolveClientIp(httpServletRequest);
        RegisterCompletionResult result = registerCompletionService.verifyEmailCodeAndRegister(
                request.getEmail(),
                request.getEmailCode(),
                request.getDeviceFingerprint(),
                clientIp
        );

        if (result.isSuccess()) {
            RegisterFlowStep nextStep = result.isRequirePhoneBinding()
                    ? RegisterFlowStep.ADD_PHONE
                    : RegisterFlowStep.DONE;
            registerFlowSessionService.updateStep(
                    flowSession.getFlowId(),
                    nextStep,
                    flowSession.getRiskLevel(),
                    result.isRequirePhoneBinding(),
                    !result.isRequirePhoneBinding()
            );
        }

        return ResponseEntity.ok(RegisterVerifyEmailCodeResponse.builder()
                .success(result.isSuccess())
                .message(result.getMessage())
                .userId(result.getUserId())
                .requirePhoneBinding(result.isRequirePhoneBinding())
                .build());
    }

    @Operation(summary = "Send SMS code for required phone binding during register flow.")
    @PostMapping("/phone/code")
    public ResponseEntity<RegisterPhoneBindingResponse> sendRegisterPhoneBindCode(@RequestBody LoginPhoneBindRequest body,
                                                                                  HttpServletRequest request) {
        String flowId = registerFlowCookieFactory.resolveFlowId(request);
        RegisterPhoneBindingResult result = registerPhoneBindingService.sendPhoneBindCode(
                flowId,
                resolvePreAuthToken(request),
                body.dialCode(),
                body.phoneNumber(),
                resolveClientIp(request),
                resolveRiskLevel(request),
                resolveDeviceFingerprint(request),
                body.captchaUuid(),
                body.captchaCode()
        );
        return registerPhoneResponse(result);
    }

    @Operation(summary = "Bind required phone and complete register flow.")
    @PostMapping("/phone/bind")
    public ResponseEntity<RegisterPhoneBindingResponse> bindRegisterPhone(@RequestBody LoginPhoneBindRequest body,
                                                                          HttpServletRequest request,
                                                                          HttpServletResponse response) {
        String flowId = registerFlowCookieFactory.resolveFlowId(request);
        RegisterPhoneBindingResult result = registerPhoneBindingService.bindVerifiedPhone(
                flowId,
                resolvePreAuthToken(request),
                body.dialCode(),
                body.phoneNumber(),
                body.smsCode()
        );
        if (result != null && result.isSuccess()) {
            response.addHeader("Set-Cookie", registerFlowCookieFactory.buildExpiredFlowCookie(request).toString());
        }
        return registerPhoneResponse(result);
    }

    private RegisterSendEmailCodeResponse buildCryptoFailedResponse(String message) {
        return RegisterSendEmailCodeResponse.builder()
                .success(false)
                .message(message)
                .emailCodeSent(false)
                .requirePhoneBinding(false)
                .build();
    }

    private RegisterSendEmailCodeResponse toResponse(RegisterPrecheckResult result) {
        return RegisterSendEmailCodeResponse.builder()
                .success(result.isSuccess())
                .message(result.getMessage())
                .totalScore(result.getTotalScore())
                .riskLevel(result.getRiskLevel())
                .challengeType(result.getChallengeType())
                .challengeSubType(result.getChallengeSubType())
                .challengeSiteKey(result.getChallengeSiteKey())
                .retryAfterMs(result.getRetryAfterMs())
                .waitUntilEpochMs(result.getWaitUntilEpochMs())
                .emailCodeRetryAfterMs(result.getEmailCodeRetryAfterMs())
                .passedAt(result.getPassedAt())
                .emailCodeSent(result.isEmailCodeSent())
                .requirePhoneBinding(result.isRequirePhoneBinding())
                .build();
    }

    private ResponseEntity<RegisterPhoneBindingResponse> registerPhoneResponse(RegisterPhoneBindingResult result) {
        RegisterPhoneBindingResponse body = RegisterPhoneBindingResponse.from(result);
        return ResponseEntity.status(Boolean.TRUE.equals(body.success()) ? HttpStatus.OK : HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    private Object requireRegisterFlow(HttpServletRequest request,
                                       HttpServletResponse response,
                                       String requestEmail,
                                       String requestDeviceFingerprint,
                                       RegisterFlowStep... allowedSteps) {
        String flowId = registerFlowCookieFactory.resolveFlowId(request);
        String preAuthToken = resolvePreAuthToken(request);
        boolean requireDeviceFingerprint = allowedSteps != null && allowedSteps.length > 0;
        RegisterFlowValidationResult validationResult = requireDeviceFingerprint
                ? registerFlowSessionService.validate(flowId, preAuthToken, requestDeviceFingerprint)
                : registerFlowSessionService.validate(flowId, preAuthToken);
        if (!validationResult.valid()) {
            return buildRegisterFlowError(
                    HttpStatus.GONE,
                    resolveValidationErrorCode(validationResult),
                    "Register session expired, please restart.",
                    request,
                    response,
                    RegisterFlowWebSupport.sessionEndedWithNotice(),
                    RegisterFlowWebSupport.NOTICE_FLOW_EXPIRED,
                    true
            );
        }

        RegisterFlowSession session = validationResult.session();
        if (session.isCompleted() || session.getStep() == RegisterFlowStep.DONE) {
            return buildRegisterFlowError(
                    HttpStatus.CONFLICT,
                    "REGISTER_ALREADY_COMPLETED",
                    "This account has already completed registration. Please sign in.",
                    request,
                    response,
                    RegisterFlowWebSupport.withNotice(
                            RegisterFlowWebSupport.LOGIN_PATH,
                            RegisterFlowWebSupport.NOTICE_REGISTER_COMPLETED
                    ),
                    RegisterFlowWebSupport.NOTICE_REGISTER_COMPLETED,
                    false
            );
        }

        String normalizedRequestEmail = normalizeEmail(requestEmail);
        if (StrUtil.isNotBlank(normalizedRequestEmail)
                && !normalizedRequestEmail.equalsIgnoreCase(normalizeEmail(session.getEmail()))) {
            return buildRegisterFlowError(
                    HttpStatus.CONFLICT,
                    "REGISTER_FLOW_EMAIL_MISMATCH",
                    "Register session does not match the current email. Please restart.",
                    request,
                    response,
                    RegisterFlowWebSupport.sessionEndedWithNotice(),
                    RegisterFlowWebSupport.NOTICE_FLOW_EXPIRED,
                    true
            );
        }

        if (allowedSteps != null && allowedSteps.length > 0) {
            Set<RegisterFlowStep> allowedStepSet = Set.copyOf(Arrays.asList(allowedSteps));
            if (!allowedStepSet.contains(session.getStep())) {
                return buildRegisterFlowError(
                        HttpStatus.CONFLICT,
                        "REGISTER_STEP_OUT_OF_ORDER",
                        "This register step is no longer available. Redirecting to the current step.",
                        request,
                        response,
                        RegisterFlowWebSupport.withNotice(
                                RegisterFlowWebSupport.routeForStep(session.getStep()),
                                RegisterFlowWebSupport.NOTICE_STEP_RESTORED
                        ),
                        RegisterFlowWebSupport.NOTICE_STEP_RESTORED,
                        false
                );
            }
        }

        return session;
    }

    private ResponseEntity<RegisterFlowErrorResponse> buildRegisterFlowError(HttpStatus status,
                                                                             String error,
                                                                             String message,
                                                                             HttpServletRequest request,
                                                                             HttpServletResponse response,
                                                                             String redirectPath,
                                                                             String notice,
                                                                             boolean clearFlowCookie) {
        if (clearFlowCookie) {
            response.addHeader("Set-Cookie", registerFlowCookieFactory.buildExpiredFlowCookie(request).toString());
        }
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RegisterFlowErrorResponse(
                        false,
                        status.value(),
                        error,
                        message,
                        request.getRequestURI(),
                        OffsetDateTime.now().toString(),
                        redirectPath,
                        notice
                ));
    }

    private String resolveValidationErrorCode(RegisterFlowValidationResult validationResult) {
        return switch (validationResult.error()) {
            case MISSING_FLOW_ID -> "REGISTER_FLOW_MISSING";
            case PREAUTH_MISMATCH -> "REGISTER_FLOW_PREAUTH_MISMATCH";
            case DEVICE_MISMATCH -> "REGISTER_FLOW_DEVICE_MISMATCH";
            case EXPIRED -> "REGISTER_FLOW_EXPIRED";
        };
    }

    private String resolvePreAuthToken(HttpServletRequest request) {
        Object requestAttribute = request.getAttribute("preAuthToken");
        if (requestAttribute instanceof String token && StrUtil.isNotBlank(token)) {
            return token.trim();
        }
        return preAuthBindingService.resolveIncomingToken(request);
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

    private void ensureRegisterChallengeAlive(String email,
                                              String deviceFingerprint,
                                              String expectedChallengeType,
                                              String expectedChallengeSubType) {
        if (!registerPrecheckService.refreshPendingChallengeSelection(
                email,
                deviceFingerprint,
                expectedChallengeType,
                expectedChallengeSubType)) {
            throw new IllegalArgumentException("Current register challenge has expired, please resubmit.");
        }
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            return "";
        }
        return email.trim().toLowerCase();
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
