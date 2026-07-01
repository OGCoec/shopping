package com.example.ShoppingSystem.admin.service.product;
import com.example.ShoppingSystem.admin.dto.AdminCardSecretQueryDtos.AdminCardSecretDeliveryPageResponse;
import com.example.ShoppingSystem.admin.dto.AdminCardSecretQueryDtos.AdminCardSecretInventoryPageResponse;
import com.example.ShoppingSystem.admin.dto.AdminCardSecretQueryDtos.AdminCardSecretRevealResponse;
import com.example.ShoppingSystem.admin.dto.AdminSessionMeResponse;
public interface AdminCardSecretQueryService {
    public AdminCardSecretInventoryPageResponse inventoryPage(Integer rawPage,
                                                              Integer rawPageSize,
                                                              Long spuId,
                                                              String skuId,
                                                              String batchNo,
                                                              String inventoryStatus,
                                                              String deliveryStatus,
                                                              String orderNo,
                                                              Long userId,
                                                              String orderStatus,
                                                              Boolean createdByMe,
                                                              String createdByAdminUsername,
                                                              String importSource,
                                                              AdminSessionMeResponse currentAdmin);

    public AdminCardSecretDeliveryPageResponse deliveryPage(Integer rawPage,
                                                            Integer rawPageSize,
                                                            Long spuId,
                                                            String skuId,
                                                            String orderNo,
                                                            Long userId,
                                                            String deliveryStatus,
                                                            String orderStatus,
                                                            Boolean createdByMe,
                                                            String createdByAdminUsername,
                                                            AdminSessionMeResponse currentAdmin);

    public AdminCardSecretRevealResponse reveal(String cardSecretId, AdminSessionMeResponse currentAdmin);
}
