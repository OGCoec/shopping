package com.example.ShoppingSystem.admin.controller;

import com.example.ShoppingSystem.admin.dto.AdminApiResponse;
import com.example.ShoppingSystem.admin.dto.AdminIp2LocationMailBatchRequest;
import com.example.ShoppingSystem.admin.dto.AdminIp2LocationQuotaBatchAddRequest;
import com.example.ShoppingSystem.admin.dto.AdminIp2LocationQuotaBatchDeleteRequest;
import com.example.ShoppingSystem.admin.dto.AdminIp2LocationQuotaBatchResult;
import com.example.ShoppingSystem.admin.dto.AdminIp2LocationQuotaKeysResponse;
import com.example.ShoppingSystem.admin.dto.AdminIp2LocationRegistrationCheckResponse;
import com.example.ShoppingSystem.admin.dto.AdminIp2LocationVerifyLinksResponse;
import com.example.ShoppingSystem.admin.dto.AdminRiskApiConfigUpdateRequest;
import com.example.ShoppingSystem.admin.dto.AdminRiskApiProviderConfigResponse;
import com.example.ShoppingSystem.admin.service.AdminIp2LocationMailToolService;
import com.example.ShoppingSystem.admin.service.AdminRiskApiConfigService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/shopping/admin/api/risk-api")
public class AdminRiskApiConfigController {

    private final AdminRiskApiConfigService adminRiskApiConfigService;
    private final AdminIp2LocationMailToolService adminIp2LocationMailToolService;

    public AdminRiskApiConfigController(AdminRiskApiConfigService adminRiskApiConfigService,
                                        AdminIp2LocationMailToolService adminIp2LocationMailToolService) {
        this.adminRiskApiConfigService = adminRiskApiConfigService;
        this.adminIp2LocationMailToolService = adminIp2LocationMailToolService;
    }

    @GetMapping("/{provider}/config")
    public AdminApiResponse<AdminRiskApiProviderConfigResponse> providerConfig(@PathVariable String provider) {
        return AdminApiResponse.ok(adminRiskApiConfigService.providerConfig(provider));
    }

    @PostMapping("/{provider}/config")
    public AdminApiResponse<AdminRiskApiProviderConfigResponse> updateConfig(@PathVariable String provider,
                                                                             @RequestBody AdminRiskApiConfigUpdateRequest request) {
        return AdminApiResponse.ok(adminRiskApiConfigService.updateConfig(provider, request));
    }

    @GetMapping("/ip2location/keys")
    public AdminApiResponse<AdminIp2LocationQuotaKeysResponse> ip2LocationQuotaKeys() {
        return AdminApiResponse.ok(adminRiskApiConfigService.ip2LocationQuotaKeys());
    }

    @PostMapping("/ip2location/keys/batch-add")
    public AdminApiResponse<AdminIp2LocationQuotaBatchResult> batchAddIp2LocationQuotaKeys(
            @RequestBody AdminIp2LocationQuotaBatchAddRequest request) {
        return AdminApiResponse.ok(adminRiskApiConfigService.batchAddIp2LocationQuotaKeys(request));
    }

    @PostMapping("/ip2location/keys/batch-delete")
    public AdminApiResponse<AdminIp2LocationQuotaBatchResult> batchDeleteIp2LocationQuotaKeys(
            @RequestBody AdminIp2LocationQuotaBatchDeleteRequest request) {
        return AdminApiResponse.ok(adminRiskApiConfigService.batchDeleteIp2LocationQuotaKeys(request));
    }

    @PostMapping("/ip2location/registration-check")
    public AdminApiResponse<AdminIp2LocationRegistrationCheckResponse> checkIp2LocationRegistration(
            @RequestBody AdminIp2LocationMailBatchRequest request) {
        return AdminApiResponse.ok(adminIp2LocationMailToolService.checkRegistration(request));
    }

    @PostMapping("/ip2location/verify-links")
    public AdminApiResponse<AdminIp2LocationVerifyLinksResponse> readIp2LocationVerifyLinks(
            @RequestBody AdminIp2LocationMailBatchRequest request) {
        return AdminApiResponse.ok(adminIp2LocationMailToolService.readVerifyLinks(request));
    }
}
