package com.example.ShoppingSystem.admin.service.config;
import com.example.ShoppingSystem.admin.dto.AdminSmtpConfigUpdateRequest;
import com.example.ShoppingSystem.admin.dto.AdminSmtpProviderConfigResponse;
import com.example.ShoppingSystem.admin.dto.AdminSmtpProvidersResponse;
public interface AdminSmtpConfigService {
    public AdminSmtpProvidersResponse providers();

    public AdminSmtpProviderConfigResponse providerConfig(String provider);

    public AdminSmtpProviderConfigResponse updateConfig(String provider,
                                                        AdminSmtpConfigUpdateRequest request);
}
