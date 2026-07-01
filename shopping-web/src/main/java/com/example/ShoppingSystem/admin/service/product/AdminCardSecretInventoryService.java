package com.example.ShoppingSystem.admin.service.product;
import com.example.ShoppingSystem.admin.dto.AdminSessionMeResponse;
import com.example.ShoppingSystem.admin.dto.AdminCardSecretImportResponse;
import org.springframework.web.multipart.MultipartFile;
public interface AdminCardSecretInventoryService {
    public AdminCardSecretImportResponse importSecrets(Long spuId,
                                                       String skuId,
                                                       String secretText,
                                                       MultipartFile file,
                                                       String batchNo,
                                                       String duplicatePolicy,
                                                       AdminSessionMeResponse adminSession);
}
