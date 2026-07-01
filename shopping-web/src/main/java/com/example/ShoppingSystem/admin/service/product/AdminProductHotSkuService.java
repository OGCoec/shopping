package com.example.ShoppingSystem.admin.service.product;
import com.example.ShoppingSystem.admin.dto.AdminProductHotSkuBatchEnableRequest;
import com.example.ShoppingSystem.admin.dto.AdminProductHotSkuBatchResponse;
import com.example.ShoppingSystem.admin.dto.AdminProductHotSkuResponse;
import com.example.ShoppingSystem.admin.dto.AdminProductSkuBatchIdsRequest;
import java.util.List;
public interface AdminProductHotSkuService {
    public List<AdminProductHotSkuResponse> listHotSkus(Long rawSpuId);

    public AdminProductHotSkuResponse getHotSku(Long rawSpuId, String rawSkuId);

    public AdminProductHotSkuBatchResponse batchEnable(Long rawSpuId, AdminProductHotSkuBatchEnableRequest request);

    public AdminProductHotSkuBatchResponse batchDelete(Long rawSpuId, AdminProductSkuBatchIdsRequest request);
}
