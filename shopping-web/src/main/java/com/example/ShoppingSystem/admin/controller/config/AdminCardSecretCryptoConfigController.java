package com.example.ShoppingSystem.admin.controller.config;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.ShoppingSystem.admin.dto.AdminApiResponse;
import com.example.ShoppingSystem.admin.dto.AdminCardSecretCryptoConfigResponse;
import com.example.ShoppingSystem.admin.dto.AdminCardSecretCryptoConfigUpdateRequest;
import com.example.ShoppingSystem.admin.dto.AdminCardSecretCryptoGenerateRequest;
import com.example.ShoppingSystem.admin.service.config.AdminCardSecretCryptoConfigService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "后台卡密加密配置", description = "后台卡密加密参数配置接口")
@RestController
@RequestMapping("/shopping/admin/api/card-secrets/crypto-config")
public class AdminCardSecretCryptoConfigController {

    private final AdminCardSecretCryptoConfigService adminCardSecretCryptoConfigService;

    public AdminCardSecretCryptoConfigController(AdminCardSecretCryptoConfigService adminCardSecretCryptoConfigService) {
        this.adminCardSecretCryptoConfigService = adminCardSecretCryptoConfigService;
    }

    @Operation(summary = "查询卡密加密配置")
    @GetMapping
    public AdminApiResponse<AdminCardSecretCryptoConfigResponse> config() {
        return AdminApiResponse.ok(adminCardSecretCryptoConfigService.config());
    }

    @Operation(summary = "更新卡密加密配置")
    @PostMapping
    public AdminApiResponse<AdminCardSecretCryptoConfigResponse> updateConfig(
            @RequestBody AdminCardSecretCryptoConfigUpdateRequest request) {
        return new AdminApiResponse<>(
                true,
                "ADMIN_CARD_SECRET_CRYPTO_CONFIG_UPDATED",
                "ok",
                adminCardSecretCryptoConfigService.updateConfig(request)
        );
    }

    @Operation(summary = "生成卡密加密配置")
    @PostMapping("/generate")
    public AdminApiResponse<AdminCardSecretCryptoConfigResponse> generate(
            @RequestBody AdminCardSecretCryptoGenerateRequest request) {
        return new AdminApiResponse<>(
                true,
                "ADMIN_CARD_SECRET_CRYPTO_KEYS_GENERATED",
                "ok",
                adminCardSecretCryptoConfigService.generate(request)
        );
    }
}
