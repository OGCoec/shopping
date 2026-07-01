package com.example.ShoppingSystem.admin.service.config;
import com.example.ShoppingSystem.admin.dto.AdminSmsConfigUpdateRequest;
import com.example.ShoppingSystem.admin.dto.AdminSmsProviderConfigResponse;
public interface AdminSmsConfigService {
    public AdminSmsProviderConfigResponse aliyunConfig();

    public AdminSmsProviderConfigResponse updateAliyunConfig(AdminSmsConfigUpdateRequest request);
}
