package com.example.ShoppingSystem.admin.controller.config;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import com.example.ShoppingSystem.admin.service.mail.AdminIp2LocationMailToolService;
import com.example.ShoppingSystem.admin.service.config.AdminRiskApiConfigService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "后台风控API配置", description = "后台风控第三方接口配置接口")
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

    @Operation(summary = "查询风控接口配置")
    @GetMapping("/{provider}/config")
    public AdminApiResponse<AdminRiskApiProviderConfigResponse> providerConfig(@PathVariable String provider) {
        return AdminApiResponse.ok(adminRiskApiConfigService.providerConfig(provider));
    }

    @Operation(summary = "更新风控接口配置")
    @PostMapping("/{provider}/config")
    public AdminApiResponse<AdminRiskApiProviderConfigResponse> updateConfig(@PathVariable String provider,
                                                                             @RequestBody AdminRiskApiConfigUpdateRequest request) {
        return AdminApiResponse.ok(adminRiskApiConfigService.updateConfig(provider, request));
    }

    @Operation(summary = "查询IP2Location额度密钥")
    @GetMapping("/ip2location/keys")
    public AdminApiResponse<AdminIp2LocationQuotaKeysResponse> ip2LocationQuotaKeys() {
        return AdminApiResponse.ok(adminRiskApiConfigService.ip2LocationQuotaKeys());
    }

    @Operation(summary = "批量新增IP2Location额度密钥")
    @PostMapping("/ip2location/keys/batch-add")
    public AdminApiResponse<AdminIp2LocationQuotaBatchResult> batchAddIp2LocationQuotaKeys(
            @RequestBody AdminIp2LocationQuotaBatchAddRequest request) {
        return AdminApiResponse.ok(adminRiskApiConfigService.batchAddIp2LocationQuotaKeys(request));
    }

    @Operation(summary = "批量删除IP2Location额度密钥")
    @PostMapping("/ip2location/keys/batch-delete")
    public AdminApiResponse<AdminIp2LocationQuotaBatchResult> batchDeleteIp2LocationQuotaKeys(
            @RequestBody AdminIp2LocationQuotaBatchDeleteRequest request) {
        return AdminApiResponse.ok(adminRiskApiConfigService.batchDeleteIp2LocationQuotaKeys(request));
    }

    @Operation(summary = "检查IP2Location注册状态")
    @PostMapping("/ip2location/registration-check")
    public AdminApiResponse<AdminIp2LocationRegistrationCheckResponse> checkIp2LocationRegistration(
            @RequestBody AdminIp2LocationMailBatchRequest request) {
        return AdminApiResponse.ok(adminIp2LocationMailToolService.checkRegistration(request));
    }

    @Operation(summary = "读取IP2Location验证链接")
    @PostMapping("/ip2location/verify-links")
    public AdminApiResponse<AdminIp2LocationVerifyLinksResponse> readIp2LocationVerifyLinks(
            @RequestBody AdminIp2LocationMailBatchRequest request) {
        return AdminApiResponse.ok(adminIp2LocationMailToolService.readVerifyLinks(request));
    }
}
