package com.example.ShoppingSystem.controller.login.user;

import com.example.ShoppingSystem.controller.login.user.dto.RegisterCaptchaResponse;
import com.example.ShoppingSystem.controller.login.user.dto.RegisterSendEmailCodeRequest;
import com.example.ShoppingSystem.controller.login.user.dto.RegisterSendEmailCodeResponse;
import com.example.ShoppingSystem.controller.login.user.dto.TianaiRotateCaptchaResponse;
import com.example.ShoppingSystem.controller.login.user.dto.TianaiRotateCheckRequest;
import com.example.ShoppingSystem.controller.login.user.dto.TianaiSimpleCheckResponse;
import com.example.ShoppingSystem.service.user.auth.register.RegisterPrecheckService;
import com.example.ShoppingSystem.service.user.auth.register.model.RegisterPrecheckResult;
import com.example.ShoppingSystem.service.captcha.hutool.HutoolCaptchaService;
import com.example.ShoppingSystem.service.captcha.hutool.model.HutoolCaptchaResult;
import com.example.ShoppingSystem.service.captcha.tianai.TianaiCaptchaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "注册入口", description = "注册前置验证与邮箱验证码发送")
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
    private final HutoolCaptchaService hutoolCaptchaService;
    private final TianaiCaptchaService tianaiCaptchaService;

    public RegisterController(RegisterPrecheckService registerPrecheckService,
                              HutoolCaptchaService hutoolCaptchaService,
                              TianaiCaptchaService tianaiCaptchaService) {
        this.registerPrecheckService = registerPrecheckService;
        this.hutoolCaptchaService = hutoolCaptchaService;
        this.tianaiCaptchaService = tianaiCaptchaService;
    }

    @Operation(summary = "获取注册图形验证码")
    @GetMapping("/hutoolcaptcha")
    public RegisterCaptchaResponse getRegisterCaptcha(@RequestParam(required = false) String uuid,
                                                      @RequestParam(required = false) String email,
                                                      @RequestParam(required = false) String deviceFingerprint) throws Exception {
        if (!registerPrecheckService.refreshPendingChallengeSelection(
                email,
                deviceFingerprint,
                CHALLENGE_HUTOOL_SHEAR,
                null)) {
            throw new IllegalArgumentException("Current register challenge has expired, please resubmit");
        }
        HutoolCaptchaResult result = hutoolCaptchaService.generateCaptcha("register", uuid);
        return RegisterCaptchaResponse.builder()
                .uuid(result.getUuid())
                .image(result.getImage())
                .build();
    }

    @Operation(summary = "获取天爱 Rotate 验证码")
    @GetMapping("/tianai/rotate")
    public TianaiRotateCaptchaResponse getTianaiRotateCaptcha(@RequestParam(required = false) String captchaId,
                                                              @RequestParam(required = false) String email,
                                                              @RequestParam(required = false) String deviceFingerprint) {
        ensureRegisterChallengeAlive(email, deviceFingerprint, CHALLENGE_TIANAI, TIANAI_SUBTYPE_ROTATE);
        return TianaiRotateCaptchaResponse.from(tianaiCaptchaService.generateRotateCaptcha(captchaId));
    }

    @Operation(summary = "获取天爱 Slider 验证码")
    @GetMapping("/tianai/slider")
    public TianaiRotateCaptchaResponse getTianaiSliderCaptcha(@RequestParam(required = false) String captchaId,
                                                              @RequestParam(required = false) String email,
                                                              @RequestParam(required = false) String deviceFingerprint) {
        ensureRegisterChallengeAlive(email, deviceFingerprint, CHALLENGE_TIANAI, TIANAI_SUBTYPE_SLIDER);
        return TianaiRotateCaptchaResponse.from(tianaiCaptchaService.generateSliderCaptcha(captchaId));
    }

    @Operation(summary = "获取天爱 Concat 验证码")
    @GetMapping("/tianai/concat")
    public TianaiRotateCaptchaResponse getTianaiConcatCaptcha(@RequestParam(required = false) String captchaId,
                                                              @RequestParam(required = false) String email,
                                                              @RequestParam(required = false) String deviceFingerprint) {
        ensureRegisterChallengeAlive(email, deviceFingerprint, CHALLENGE_TIANAI, TIANAI_SUBTYPE_CONCAT);
        return TianaiRotateCaptchaResponse.from(tianaiCaptchaService.generateConcatCaptcha(captchaId));
    }

    @Operation(summary = "获取天爱 Word Click 验证码")
    @GetMapping("/tianai/word-click")
    public TianaiRotateCaptchaResponse getTianaiWordClickCaptcha(@RequestParam(required = false) String captchaId,
                                                                 @RequestParam(required = false) String email,
                                                                 @RequestParam(required = false) String deviceFingerprint) {
        ensureRegisterChallengeAlive(email, deviceFingerprint, CHALLENGE_TIANAI, TIANAI_SUBTYPE_WORD_IMAGE_CLICK);
        return TianaiRotateCaptchaResponse.from(tianaiCaptchaService.generateWordClickCaptcha(captchaId));
    }

    @Operation(summary = "校验天爱 Rotate 验证码")
    @PostMapping("/tianai/rotate/check")
    public TianaiSimpleCheckResponse checkTianaiRotateCaptcha(@RequestBody TianaiRotateCheckRequest request) {
        return new TianaiSimpleCheckResponse(tianaiCaptchaService.validateRotateCaptcha(request.getCaptchaId(), request.getAngle()));
    }

    @Operation(summary = "解析注册风险并返回验证码类型")
    @PostMapping("/email-code-type")
    public RegisterSendEmailCodeResponse resolveRegisterEmailCodeChallenge(@RequestBody RegisterSendEmailCodeRequest request,
                                                                           HttpServletRequest httpServletRequest) {
        String clientIp = resolveClientIp(httpServletRequest);
        RegisterPrecheckResult result = registerPrecheckService.resolveRegisterEmailCodeChallenge(
                request.getEmail(),
                request.getUsername(),
                request.getPassword(),
                request.getDeviceFingerprint(),
                clientIp
        );
        return toResponse(result);
    }

    @Operation(summary = "发送注册邮箱验证码")
    @PostMapping("/email-code")
    public RegisterSendEmailCodeResponse sendRegisterEmailCode(@RequestBody RegisterSendEmailCodeRequest request,
                                                               HttpServletRequest httpServletRequest) {
        String clientIp = resolveClientIp(httpServletRequest);
        RegisterPrecheckResult result = registerPrecheckService.sendRegisterEmailCodeAfterCaptcha(
                request.getEmail(),
                request.getUsername(),
                request.getPassword(),
                request.getDeviceFingerprint(),
                clientIp,
                request.getCaptchaUuid(),
                request.getCaptchaCode()
        );
        return toResponse(result);
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
                .emailCodeSent(result.isEmailCodeSent())
                .requirePhoneBinding(result.isRequirePhoneBinding())
                .build();
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

    private void ensureRegisterChallengeAlive(String email,
                                              String deviceFingerprint,
                                              String expectedChallengeType,
                                              String expectedChallengeSubType) {
        if (!registerPrecheckService.refreshPendingChallengeSelection(
                email,
                deviceFingerprint,
                expectedChallengeType,
                expectedChallengeSubType)) {
            throw new IllegalArgumentException("Current register challenge has expired, please resubmit");
        }
    }
}
