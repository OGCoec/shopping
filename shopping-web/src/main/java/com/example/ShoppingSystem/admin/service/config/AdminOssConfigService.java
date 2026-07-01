package com.example.ShoppingSystem.admin.service.config;
import com.example.ShoppingSystem.admin.dto.AdminOssConfigUpdateRequest;
import com.example.ShoppingSystem.admin.dto.AdminOssProviderConfigResponse;
public interface AdminOssConfigService {
    public AdminOssProviderConfigResponse aliyunConfig();

    public AdminOssProviderConfigResponse updateAliyunConfig(AdminOssConfigUpdateRequest request);
}
