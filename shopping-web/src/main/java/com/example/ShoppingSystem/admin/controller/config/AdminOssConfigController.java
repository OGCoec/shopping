package com.example.ShoppingSystem.admin.controller.config;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.ShoppingSystem.admin.dto.AdminApiResponse;
import com.example.ShoppingSystem.admin.dto.AdminOssConfigUpdateRequest;
import com.example.ShoppingSystem.admin.dto.AdminOssProviderConfigResponse;
import com.example.ShoppingSystem.admin.service.config.AdminOssConfigService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "后台OSS配置", description = "后台对象存储配置接口")
@RestController
@RequestMapping("/shopping/admin/api/oss")
public class AdminOssConfigController {

    private final AdminOssConfigService adminOssConfigService;

    public AdminOssConfigController(AdminOssConfigService adminOssConfigService) {
        this.adminOssConfigService = adminOssConfigService;
    }

    @Operation(summary = "查询阿里云OSS配置")
    @GetMapping("/aliyun/config")
    public AdminApiResponse<AdminOssProviderConfigResponse> aliyunConfig() {
        return AdminApiResponse.ok(adminOssConfigService.aliyunConfig());
    }

    @Operation(summary = "更新阿里云OSS配置")
    @PostMapping("/aliyun/config")
    public AdminApiResponse<AdminOssProviderConfigResponse> updateAliyunConfig(@RequestBody AdminOssConfigUpdateRequest request) {
        return AdminApiResponse.ok(adminOssConfigService.updateAliyunConfig(request));
    }
}
