package com.example.ShoppingSystem.admin.service.config;
import com.example.ShoppingSystem.admin.dto.AdminIp2LocationQuotaBatchAddRequest;
import com.example.ShoppingSystem.admin.dto.AdminIp2LocationQuotaBatchDeleteRequest;
import com.example.ShoppingSystem.admin.dto.AdminIp2LocationQuotaBatchResult;
import com.example.ShoppingSystem.admin.dto.AdminIp2LocationQuotaKeysResponse;
import com.example.ShoppingSystem.admin.dto.AdminRiskApiConfigUpdateRequest;
import com.example.ShoppingSystem.admin.dto.AdminRiskApiProviderConfigResponse;
public interface AdminRiskApiConfigService {
    public AdminRiskApiProviderConfigResponse providerConfig(String provider);

    public AdminRiskApiProviderConfigResponse updateConfig(String provider, AdminRiskApiConfigUpdateRequest request);

    public AdminIp2LocationQuotaKeysResponse ip2LocationQuotaKeys();

    public AdminIp2LocationQuotaBatchResult batchAddIp2LocationQuotaKeys(AdminIp2LocationQuotaBatchAddRequest request);

    public AdminIp2LocationQuotaBatchResult batchDeleteIp2LocationQuotaKeys(AdminIp2LocationQuotaBatchDeleteRequest request);
}
