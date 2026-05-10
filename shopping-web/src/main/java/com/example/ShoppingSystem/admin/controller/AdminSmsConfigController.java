package com.example.ShoppingSystem.admin.controller;

import com.example.ShoppingSystem.admin.dto.AdminApiResponse;
import com.example.ShoppingSystem.admin.dto.AdminSmsConfigUpdateRequest;
import com.example.ShoppingSystem.admin.dto.AdminSmsProviderConfigResponse;
import com.example.ShoppingSystem.admin.service.AdminSmsConfigService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/shopping/admin/api/sms")
public class AdminSmsConfigController {

    private final AdminSmsConfigService adminSmsConfigService;

    public AdminSmsConfigController(AdminSmsConfigService adminSmsConfigService) {
        this.adminSmsConfigService = adminSmsConfigService;
    }

    @GetMapping("/aliyun/config")
    public AdminApiResponse<AdminSmsProviderConfigResponse> aliyunConfig() {
        return AdminApiResponse.ok(adminSmsConfigService.aliyunConfig());
    }

    @PostMapping("/aliyun/config")
    public AdminApiResponse<AdminSmsProviderConfigResponse> updateAliyunConfig(@RequestBody AdminSmsConfigUpdateRequest request) {
        return AdminApiResponse.ok(adminSmsConfigService.updateAliyunConfig(request));
    }
}
