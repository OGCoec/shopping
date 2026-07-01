package com.example.ShoppingSystem.admin.service.config;
import com.example.ShoppingSystem.admin.dto.AdminCaptchaConfigUpdateRequest;
import com.example.ShoppingSystem.admin.dto.AdminCaptchaProviderConfigResponse;
public interface AdminCaptchaConfigService {
    public AdminCaptchaProviderConfigResponse turnstileConfig();

    public AdminCaptchaProviderConfigResponse hcaptchaConfig();

    public AdminCaptchaProviderConfigResponse recaptchaConfig();

    public AdminCaptchaProviderConfigResponse updateConfig(String provider,
                                                           AdminCaptchaConfigUpdateRequest request);
}
