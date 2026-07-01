package com.example.ShoppingSystem.admin.service.product;
import com.example.ShoppingSystem.admin.dto.AdminProductImageCancelRequest;
import com.example.ShoppingSystem.admin.dto.AdminProductImagePreuploadResponse;
import com.example.ShoppingSystem.admin.dto.AdminProductSkuBatchIdsRequest;
import com.example.ShoppingSystem.admin.dto.AdminProductSkuBatchResponse;
import com.example.ShoppingSystem.admin.dto.AdminProductSkuBatchStatusRequest;
import com.example.ShoppingSystem.admin.dto.AdminProductSkuCreateRequest;
import com.example.ShoppingSystem.admin.dto.AdminProductSkuDeleteResponse;
import com.example.ShoppingSystem.admin.dto.AdminProductSkuUpdateRequest;
import com.example.ShoppingSystem.admin.dto.AdminProductSpuBatchDeleteResponse;
import com.example.ShoppingSystem.admin.dto.AdminProductSpuBatchDisableResponse;
import com.example.ShoppingSystem.admin.dto.AdminProductSpuBatchIdsRequest;
import com.example.ShoppingSystem.admin.dto.AdminProductSpuCreateRequest;
import com.example.ShoppingSystem.admin.dto.AdminProductSpuDetailResponse;
import com.example.ShoppingSystem.admin.dto.AdminProductSpuDetailSkuResponse;
import com.example.ShoppingSystem.admin.dto.AdminProductSpuDetailUpdateRequest;
import com.example.ShoppingSystem.admin.dto.AdminProductSpuPageResponse;
import com.example.ShoppingSystem.admin.dto.AdminProductSpuResponse;
import com.example.ShoppingSystem.admin.dto.AdminProductSpuStatusRequest;
import org.springframework.web.multipart.MultipartFile;
public interface AdminProductSpuService {
    public AdminProductSpuPageResponse page(Integer page, Integer pageSize, String name, Long categoryId, String status);

    public AdminProductImagePreuploadResponse preuploadMainImage(MultipartFile file);

    public void cancelPreupload(AdminProductImageCancelRequest request);

    public AdminProductSpuDetailResponse getDetail(Long id);

    public AdminProductSpuDetailSkuResponse getSkuDetail(Long id, String skuId);

    public AdminProductSpuDetailSkuResponse createSku(Long id, AdminProductSkuCreateRequest request);

    public AdminProductSpuDetailSkuResponse updateSku(Long id, String skuId, AdminProductSkuUpdateRequest request);

    public AdminProductSpuDetailSkuResponse changeSkuStatus(Long id, String skuId, AdminProductSpuStatusRequest request);

    public AdminProductSkuDeleteResponse deleteSku(Long id, String skuId);

    public AdminProductSkuBatchResponse batchChangeSkuStatus(Long id, AdminProductSkuBatchStatusRequest request);

    public AdminProductSkuBatchResponse batchDeleteSku(Long id, AdminProductSkuBatchIdsRequest request);

    public AdminProductSpuDetailResponse updateDetail(Long id, AdminProductSpuDetailUpdateRequest request);

    public AdminProductSpuResponse create(AdminProductSpuCreateRequest request);

    public AdminProductSpuResponse changeStatus(Long id, AdminProductSpuStatusRequest request);

    public AdminProductSpuBatchDisableResponse batchDisable(AdminProductSpuBatchIdsRequest request);

    public AdminProductSpuBatchDisableResponse batchDisableByLeafCategory(Long id);

    public AdminProductSpuBatchDeleteResponse batchDelete(AdminProductSpuBatchIdsRequest request);

    public AdminProductSpuBatchDeleteResponse batchDeleteByLeafCategory(Long id);
}
