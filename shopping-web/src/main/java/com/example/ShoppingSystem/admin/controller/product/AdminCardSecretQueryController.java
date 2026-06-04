package com.example.ShoppingSystem.admin.controller.product;

import com.example.ShoppingSystem.admin.dto.AdminApiResponse;
import com.example.ShoppingSystem.admin.dto.AdminCardSecretQueryDtos.AdminCardSecretDeliveryPageResponse;
import com.example.ShoppingSystem.admin.dto.AdminCardSecretQueryDtos.AdminCardSecretInventoryPageResponse;
import com.example.ShoppingSystem.admin.dto.AdminCardSecretQueryDtos.AdminCardSecretRevealResponse;
import com.example.ShoppingSystem.admin.service.auth.AdminSessionService;
import com.example.ShoppingSystem.admin.service.product.AdminCardSecretQueryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/shopping/admin/api/card-secrets")
public class AdminCardSecretQueryController {

    private final AdminCardSecretQueryService adminCardSecretQueryService;
    private final AdminSessionService adminSessionService;

    public AdminCardSecretQueryController(AdminCardSecretQueryService adminCardSecretQueryService,
                                          AdminSessionService adminSessionService) {
        this.adminCardSecretQueryService = adminCardSecretQueryService;
        this.adminSessionService = adminSessionService;
    }

    @GetMapping("/inventory")
    public AdminApiResponse<AdminCardSecretInventoryPageResponse> inventory(
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "spuId", required = false) Long spuId,
            @RequestParam(value = "skuId", required = false) String skuId,
            @RequestParam(value = "batchNo", required = false) String batchNo,
            @RequestParam(value = "inventoryStatus", required = false) String inventoryStatus,
            @RequestParam(value = "deliveryStatus", required = false) String deliveryStatus,
            @RequestParam(value = "orderNo", required = false) String orderNo,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "orderStatus", required = false) String orderStatus,
            @RequestParam(value = "createdByMe", required = false) Boolean createdByMe,
            @RequestParam(value = "createdByAdminUsername", required = false) String createdByAdminUsername,
            @RequestParam(value = "importSource", required = false) String importSource,
            HttpServletRequest request) {
        return new AdminApiResponse<>(
                true,
                "ADMIN_CARD_SECRET_INVENTORY_PAGE_OK",
                "ok",
                adminCardSecretQueryService.inventoryPage(
                        page,
                        pageSize,
                        spuId,
                        skuId,
                        batchNo,
                        inventoryStatus,
                        deliveryStatus,
                        orderNo,
                        userId,
                        orderStatus,
                        createdByMe,
                        createdByAdminUsername,
                        importSource,
                        adminSessionService.current(request)
                )
        );
    }

    @GetMapping("/deliveries")
    public AdminApiResponse<AdminCardSecretDeliveryPageResponse> deliveries(
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "spuId", required = false) Long spuId,
            @RequestParam(value = "skuId", required = false) String skuId,
            @RequestParam(value = "orderNo", required = false) String orderNo,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "deliveryStatus", required = false) String deliveryStatus,
            @RequestParam(value = "orderStatus", required = false) String orderStatus,
            @RequestParam(value = "createdByMe", required = false) Boolean createdByMe,
            @RequestParam(value = "createdByAdminUsername", required = false) String createdByAdminUsername,
            HttpServletRequest request) {
        return new AdminApiResponse<>(
                true,
                "ADMIN_CARD_SECRET_DELIVERY_PAGE_OK",
                "ok",
                adminCardSecretQueryService.deliveryPage(
                        page,
                        pageSize,
                        spuId,
                        skuId,
                        orderNo,
                        userId,
                        deliveryStatus,
                        orderStatus,
                        createdByMe,
                        createdByAdminUsername,
                        adminSessionService.current(request)
                )
        );
    }

    @GetMapping("/{cardSecretId}/reveal")
    public AdminApiResponse<AdminCardSecretRevealResponse> reveal(@PathVariable String cardSecretId,
                                                                  HttpServletRequest request,
                                                                  HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, max-age=0");
        return new AdminApiResponse<>(
                true,
                "ADMIN_CARD_SECRET_REVEAL_OK",
                "ok",
                adminCardSecretQueryService.reveal(cardSecretId, adminSessionService.current(request))
        );
    }
}
