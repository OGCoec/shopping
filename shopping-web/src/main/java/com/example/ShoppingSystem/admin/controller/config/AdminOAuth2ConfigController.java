package com.example.ShoppingSystem.admin.controller.config;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.ShoppingSystem.admin.dto.AdminApiResponse;
import com.example.ShoppingSystem.admin.dto.AdminOAuth2ProviderConfigResponse;
import com.example.ShoppingSystem.admin.dto.AdminOAuth2ConfigUpdateRequest;
import com.example.ShoppingSystem.admin.service.config.AdminOAuth2ConfigService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "后台OAuth2配置", description = "后台第三方登录配置接口")
@RestController
@RequestMapping("/shopping/admin/api/oauth2")
public class AdminOAuth2ConfigController {

    private final AdminOAuth2ConfigService adminOAuth2ConfigService;

    public AdminOAuth2ConfigController(AdminOAuth2ConfigService adminOAuth2ConfigService) {
        this.adminOAuth2ConfigService = adminOAuth2ConfigService;
    }

    @Operation(summary = "查询GitHub登录配置")
    @GetMapping("/github/config")
    public AdminApiResponse<AdminOAuth2ProviderConfigResponse> githubConfig() {
        return AdminApiResponse.ok(adminOAuth2ConfigService.githubConfig());
    }

    @Operation(summary = "查询Google登录配置")
    @GetMapping("/google/config")
    public AdminApiResponse<AdminOAuth2ProviderConfigResponse> googleConfig() {
        return AdminApiResponse.ok(adminOAuth2ConfigService.googleConfig());
    }

    @Operation(summary = "查询Microsoft登录配置")
    @GetMapping("/microsoft/config")
    public AdminApiResponse<AdminOAuth2ProviderConfigResponse> microsoftConfig() {
        return AdminApiResponse.ok(adminOAuth2ConfigService.microsoftConfig());
    }

    @Operation(summary = "更新OAuth2登录配置")
    @PostMapping("/{provider}/config")
    public AdminApiResponse<AdminOAuth2ProviderConfigResponse> updateConfig(@PathVariable String provider,
                                                                            @RequestBody AdminOAuth2ConfigUpdateRequest request) {
        return AdminApiResponse.ok(adminOAuth2ConfigService.updateConfig(provider, request));
    }
}
