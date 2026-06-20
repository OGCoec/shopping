package com.example.ShoppingSystem.admin.service.product;

import com.example.ShoppingSystem.Utils.HybridIdCodec;
import com.example.ShoppingSystem.Utils.ProductSkuIdCodec;
import com.example.ShoppingSystem.admin.dto.AdminCardSecretQueryDtos.AdminCardSecretDeliveryItemResponse;
import com.example.ShoppingSystem.admin.dto.AdminCardSecretQueryDtos.AdminCardSecretDeliveryPageResponse;
import com.example.ShoppingSystem.admin.dto.AdminCardSecretQueryDtos.AdminCardSecretInventoryItemResponse;
import com.example.ShoppingSystem.admin.dto.AdminCardSecretQueryDtos.AdminCardSecretInventoryPageResponse;
import com.example.ShoppingSystem.admin.dto.AdminCardSecretQueryDtos.AdminCardSecretRevealResponse;
import com.example.ShoppingSystem.admin.dto.AdminSessionMeResponse;
import com.example.ShoppingSystem.admin.service.common.AdminPaginationValidator;
import com.example.ShoppingSystem.admin.service.common.AdminServiceException;
import com.example.ShoppingSystem.admin.service.config.AdminCardSecretCryptoConfigService;
import com.example.ShoppingSystem.admin.service.config.impl.AdminManagedEnvService.AdminManagedEnvServiceImpl;
import com.example.ShoppingSystem.mapper.product.CardSecretInventoryMapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
