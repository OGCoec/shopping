package com.example.ShoppingSystem.admin.controller.config;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.ShoppingSystem.admin.dto.AdminApiResponse;
import com.example.ShoppingSystem.admin.dto.AdminSmsConfigUpdateRequest;
import com.example.ShoppingSystem.admin.dto.AdminSmsProviderConfigResponse;
import com.example.ShoppingSystem.admin.service.config.AdminSmsConfigService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "后台短信配置", description = "后台短信服务配置接口")
@RestController
@RequestMapping("/shopping/admin/api/sms")
public class AdminSmsConfigController {

    private final AdminSmsConfigService adminSmsConfigService;

    public AdminSmsConfigController(AdminSmsConfigService adminSmsConfigService) {
        this.adminSmsConfigService = adminSmsConfigService;
    }

    @Operation(summary = "查询阿里云短信配置")
    @GetMapping("/aliyun/config")
    public AdminApiResponse<AdminSmsProviderConfigResponse> aliyunConfig() {
        return AdminApiResponse.ok(adminSmsConfigService.aliyunConfig());
    }

    @Operation(summary = "更新阿里云短信配置")
    @PostMapping("/aliyun/config")
    public AdminApiResponse<AdminSmsProviderConfigResponse> updateAliyunConfig(@RequestBody AdminSmsConfigUpdateRequest request) {
        return AdminApiResponse.ok(adminSmsConfigService.updateAliyunConfig(request));
    }
}
