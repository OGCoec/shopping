package com.example.ShoppingSystem.admin.controller.config;

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

@RestController
@RequestMapping("/shopping/admin/api/card-secrets/crypto-config")
public class AdminCardSecretCryptoConfigController {

    private final AdminCardSecretCryptoConfigService adminCardSecretCryptoConfigService;

    public AdminCardSecretCryptoConfigController(AdminCardSecretCryptoConfigService adminCardSecretCryptoConfigService) {
        this.adminCardSecretCryptoConfigService = adminCardSecretCryptoConfigService;
    }

    @GetMapping
    public AdminApiResponse<AdminCardSecretCryptoConfigResponse> config() {
        return AdminApiResponse.ok(adminCardSecretCryptoConfigService.config());
    }

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
