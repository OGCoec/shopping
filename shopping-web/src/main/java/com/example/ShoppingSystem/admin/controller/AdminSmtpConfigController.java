package com.example.ShoppingSystem.admin.controller;

import com.example.ShoppingSystem.admin.dto.AdminApiResponse;
import com.example.ShoppingSystem.admin.dto.AdminSmtpConfigUpdateRequest;
import com.example.ShoppingSystem.admin.dto.AdminSmtpProviderConfigResponse;
import com.example.ShoppingSystem.admin.dto.AdminSmtpProvidersResponse;
import com.example.ShoppingSystem.admin.service.AdminSmtpConfigService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/shopping/admin/api/smtp")
public class AdminSmtpConfigController {

    private final AdminSmtpConfigService adminSmtpConfigService;

    public AdminSmtpConfigController(AdminSmtpConfigService adminSmtpConfigService) {
        this.adminSmtpConfigService = adminSmtpConfigService;
    }

    @GetMapping("/providers")
    public AdminApiResponse<AdminSmtpProvidersResponse> providers() {
        return AdminApiResponse.ok(adminSmtpConfigService.providers());
    }

    @GetMapping("/{provider}/config")
    public AdminApiResponse<AdminSmtpProviderConfigResponse> providerConfig(@PathVariable String provider) {
        return AdminApiResponse.ok(adminSmtpConfigService.providerConfig(provider));
    }

    @PostMapping("/{provider}/config")
    public AdminApiResponse<AdminSmtpProviderConfigResponse> updateConfig(@PathVariable String provider,
                                                                          @RequestBody AdminSmtpConfigUpdateRequest request) {
        return AdminApiResponse.ok(adminSmtpConfigService.updateConfig(provider, request));
    }
}
