package com.example.ShoppingSystem.admin.service.config;
import com.example.ShoppingSystem.admin.dto.AdminOAuth2ConfigUpdateRequest;
import com.example.ShoppingSystem.admin.dto.AdminOAuth2ProviderConfigResponse;
public interface AdminOAuth2ConfigService {
    public AdminOAuth2ProviderConfigResponse githubConfig();

    public AdminOAuth2ProviderConfigResponse googleConfig();

    public AdminOAuth2ProviderConfigResponse microsoftConfig();

    public AdminOAuth2ProviderConfigResponse updateConfig(String provider,
                                                          AdminOAuth2ConfigUpdateRequest request);
}
