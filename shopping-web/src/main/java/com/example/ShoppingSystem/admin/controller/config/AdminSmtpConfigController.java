package com.example.ShoppingSystem.admin.controller.config;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.ShoppingSystem.admin.dto.AdminApiResponse;
import com.example.ShoppingSystem.admin.dto.AdminSmtpConfigUpdateRequest;
import com.example.ShoppingSystem.admin.dto.AdminSmtpProviderConfigResponse;
import com.example.ShoppingSystem.admin.dto.AdminSmtpProvidersResponse;
import com.example.ShoppingSystem.admin.service.config.AdminSmtpConfigService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "后台SMTP配置", description = "后台邮件服务配置接口")
@RestController
@RequestMapping("/shopping/admin/api/smtp")
public class AdminSmtpConfigController {

    private final AdminSmtpConfigService adminSmtpConfigService;

    public AdminSmtpConfigController(AdminSmtpConfigService adminSmtpConfigService) {
        this.adminSmtpConfigService = adminSmtpConfigService;
    }

    @Operation(summary = "查询SMTP服务商列表")
    @GetMapping("/providers")
    public AdminApiResponse<AdminSmtpProvidersResponse> providers() {
        return AdminApiResponse.ok(adminSmtpConfigService.providers());
    }

    @Operation(summary = "查询SMTP服务商配置")
    @GetMapping("/{provider}/config")
    public AdminApiResponse<AdminSmtpProviderConfigResponse> providerConfig(@PathVariable String provider) {
        return AdminApiResponse.ok(adminSmtpConfigService.providerConfig(provider));
    }

    @Operation(summary = "更新SMTP服务商配置")
    @PostMapping("/{provider}/config")
    public AdminApiResponse<AdminSmtpProviderConfigResponse> updateConfig(@PathVariable String provider,
                                                                          @RequestBody AdminSmtpConfigUpdateRequest request) {
        return AdminApiResponse.ok(adminSmtpConfigService.updateConfig(provider, request));
    }
}
