package com.example.ShoppingSystem.admin.controller.config;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.ShoppingSystem.admin.dto.AdminApiResponse;
import com.example.ShoppingSystem.admin.dto.AdminCaptchaConfigUpdateRequest;
import com.example.ShoppingSystem.admin.dto.AdminCaptchaProviderConfigResponse;
import com.example.ShoppingSystem.admin.service.config.AdminCaptchaConfigService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "后台验证码配置", description = "后台验证码服务配置接口")
@RestController
@RequestMapping("/shopping/admin/api/captcha")
public class AdminCaptchaConfigController {

    private final AdminCaptchaConfigService adminCaptchaConfigService;

    public AdminCaptchaConfigController(AdminCaptchaConfigService adminCaptchaConfigService) {
        this.adminCaptchaConfigService = adminCaptchaConfigService;
    }

    @Operation(summary = "查询Turnstile验证码配置")
    @GetMapping("/turnstile/config")
    public AdminApiResponse<AdminCaptchaProviderConfigResponse> turnstileConfig() {
        return AdminApiResponse.ok(adminCaptchaConfigService.turnstileConfig());
    }

    @Operation(summary = "查询hCaptcha验证码配置")
    @GetMapping("/hcaptcha/config")
    public AdminApiResponse<AdminCaptchaProviderConfigResponse> hcaptchaConfig() {
        return AdminApiResponse.ok(adminCaptchaConfigService.hcaptchaConfig());
    }

    @Operation(summary = "查询reCAPTCHA验证码配置")
    @GetMapping("/recaptcha/config")
    public AdminApiResponse<AdminCaptchaProviderConfigResponse> recaptchaConfig() {
        return AdminApiResponse.ok(adminCaptchaConfigService.recaptchaConfig());
    }

    @Operation(summary = "更新验证码服务配置")
    @PostMapping("/{provider}/config")
    public AdminApiResponse<AdminCaptchaProviderConfigResponse> updateConfig(@PathVariable String provider,
                                                                             @RequestBody AdminCaptchaConfigUpdateRequest request) {
        return AdminApiResponse.ok(adminCaptchaConfigService.updateConfig(provider, request));
    }
}
