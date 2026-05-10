package com.example.ShoppingSystem.admin.controller;

import com.example.ShoppingSystem.admin.dto.AdminApiResponse;
import com.example.ShoppingSystem.admin.dto.AdminOAuth2ProviderConfigResponse;
import com.example.ShoppingSystem.admin.dto.AdminOAuth2ConfigUpdateRequest;
import com.example.ShoppingSystem.admin.service.AdminOAuth2ConfigService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/shopping/admin/api/oauth2")
public class AdminOAuth2ConfigController {

    private final AdminOAuth2ConfigService adminOAuth2ConfigService;

    public AdminOAuth2ConfigController(AdminOAuth2ConfigService adminOAuth2ConfigService) {
        this.adminOAuth2ConfigService = adminOAuth2ConfigService;
    }

    @GetMapping("/github/config")
    public AdminApiResponse<AdminOAuth2ProviderConfigResponse> githubConfig() {
        return AdminApiResponse.ok(adminOAuth2ConfigService.githubConfig());
    }

    @GetMapping("/google/config")
    public AdminApiResponse<AdminOAuth2ProviderConfigResponse> googleConfig() {
        return AdminApiResponse.ok(adminOAuth2ConfigService.googleConfig());
    }

    @GetMapping("/microsoft/config")
    public AdminApiResponse<AdminOAuth2ProviderConfigResponse> microsoftConfig() {
        return AdminApiResponse.ok(adminOAuth2ConfigService.microsoftConfig());
    }

    @PostMapping("/{provider}/config")
    public AdminApiResponse<AdminOAuth2ProviderConfigResponse> updateConfig(@PathVariable String provider,
                                                                            @RequestBody AdminOAuth2ConfigUpdateRequest request) {
        return AdminApiResponse.ok(adminOAuth2ConfigService.updateConfig(provider, request));
    }
}
