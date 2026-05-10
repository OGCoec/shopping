package com.example.ShoppingSystem.admin.controller;

import com.example.ShoppingSystem.admin.dto.AdminApiResponse;
import com.example.ShoppingSystem.admin.dto.AdminCaptchaConfigUpdateRequest;
import com.example.ShoppingSystem.admin.dto.AdminCaptchaProviderConfigResponse;
import com.example.ShoppingSystem.admin.service.AdminCaptchaConfigService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/shopping/admin/api/captcha")
public class AdminCaptchaConfigController {

    private final AdminCaptchaConfigService adminCaptchaConfigService;

    public AdminCaptchaConfigController(AdminCaptchaConfigService adminCaptchaConfigService) {
        this.adminCaptchaConfigService = adminCaptchaConfigService;
    }

    @GetMapping("/turnstile/config")
    public AdminApiResponse<AdminCaptchaProviderConfigResponse> turnstileConfig() {
        return AdminApiResponse.ok(adminCaptchaConfigService.turnstileConfig());
    }

    @GetMapping("/hcaptcha/config")
    public AdminApiResponse<AdminCaptchaProviderConfigResponse> hcaptchaConfig() {
        return AdminApiResponse.ok(adminCaptchaConfigService.hcaptchaConfig());
    }

    @GetMapping("/recaptcha/config")
    public AdminApiResponse<AdminCaptchaProviderConfigResponse> recaptchaConfig() {
        return AdminApiResponse.ok(adminCaptchaConfigService.recaptchaConfig());
    }

    @PostMapping("/{provider}/config")
    public AdminApiResponse<AdminCaptchaProviderConfigResponse> updateConfig(@PathVariable String provider,
                                                                             @RequestBody AdminCaptchaConfigUpdateRequest request) {
        return AdminApiResponse.ok(adminCaptchaConfigService.updateConfig(provider, request));
    }
}
