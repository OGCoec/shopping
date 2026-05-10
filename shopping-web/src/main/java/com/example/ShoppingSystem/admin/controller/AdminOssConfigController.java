package com.example.ShoppingSystem.admin.controller;

import com.example.ShoppingSystem.admin.dto.AdminApiResponse;
import com.example.ShoppingSystem.admin.dto.AdminOssConfigUpdateRequest;
import com.example.ShoppingSystem.admin.dto.AdminOssProviderConfigResponse;
import com.example.ShoppingSystem.admin.service.AdminOssConfigService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/shopping/admin/api/oss")
public class AdminOssConfigController {

    private final AdminOssConfigService adminOssConfigService;

    public AdminOssConfigController(AdminOssConfigService adminOssConfigService) {
        this.adminOssConfigService = adminOssConfigService;
    }

    @GetMapping("/aliyun/config")
    public AdminApiResponse<AdminOssProviderConfigResponse> aliyunConfig() {
        return AdminApiResponse.ok(adminOssConfigService.aliyunConfig());
    }

    @PostMapping("/aliyun/config")
    public AdminApiResponse<AdminOssProviderConfigResponse> updateAliyunConfig(@RequestBody AdminOssConfigUpdateRequest request) {
        return AdminApiResponse.ok(adminOssConfigService.updateAliyunConfig(request));
    }
}
