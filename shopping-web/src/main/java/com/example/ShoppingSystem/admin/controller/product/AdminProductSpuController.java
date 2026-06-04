package com.example.ShoppingSystem.admin.controller.product;

import com.example.ShoppingSystem.admin.dto.AdminApiResponse;
import com.example.ShoppingSystem.admin.dto.AdminProductHotSkuBatchEnableRequest;
import com.example.ShoppingSystem.admin.dto.AdminProductHotSkuBatchResponse;
import com.example.ShoppingSystem.admin.dto.AdminProductHotSkuResponse;
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
import com.example.ShoppingSystem.admin.dto.AdminCardSecretImportResponse;
import com.example.ShoppingSystem.admin.service.auth.AdminSessionService;
import com.example.ShoppingSystem.admin.service.product.AdminCardSecretInventoryService;
import com.example.ShoppingSystem.admin.service.product.AdminProductHotSkuService;
import com.example.ShoppingSystem.admin.service.product.AdminProductSpuService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/shopping/admin/api/products")
public class AdminProductSpuController {

    private final AdminProductSpuService adminProductSpuService;
    private final AdminProductHotSkuService adminProductHotSkuService;
    private final AdminCardSecretInventoryService adminCardSecretInventoryService;
    private final AdminSessionService adminSessionService;

    public AdminProductSpuController(AdminProductSpuService adminProductSpuService,
                                     AdminProductHotSkuService adminProductHotSkuService,
                                     AdminCardSecretInventoryService adminCardSecretInventoryService,
                                     AdminSessionService adminSessionService) {
        this.adminProductSpuService = adminProductSpuService;
        this.adminProductHotSkuService = adminProductHotSkuService;
        this.adminCardSecretInventoryService = adminCardSecretInventoryService;
        this.adminSessionService = adminSessionService;
    }

    @GetMapping("/spu/page")
    public AdminApiResponse<AdminProductSpuPageResponse> page(@RequestParam(value = "page", required = false) Integer page,
                                                              @RequestParam(value = "pageSize", required = false) Integer pageSize,
                                                              @RequestParam(value = "name", required = false) String name,
                                                              @RequestParam(value = "categoryId", required = false) Long categoryId,
                                                              @RequestParam(value = "status", required = false) String status) {
        return AdminApiResponse.ok(adminProductSpuService.page(page, pageSize, name, categoryId, status));
    }

    @PostMapping("/images/preupload")
    public AdminApiResponse<AdminProductImagePreuploadResponse> preuploadMainImage(
            @RequestParam("file") MultipartFile file) {
        return AdminApiResponse.ok(adminProductSpuService.preuploadMainImage(file));
    }

    @PostMapping("/spu/{spuId}/sku/{skuId}/card-secrets/import")
    public AdminApiResponse<AdminCardSecretImportResponse> importCardSecrets(
            @PathVariable Long spuId,
            @PathVariable String skuId,
            @RequestParam(value = "secretText", required = false) String secretText,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "batchNo", required = false) String batchNo,
            @RequestParam(value = "duplicatePolicy", required = false) String duplicatePolicy,
            HttpServletRequest request) {
        return new AdminApiResponse<>(
                true,
                "ADMIN_CARD_SECRET_IMPORT_OK",
                "ok",
                adminCardSecretInventoryService.importSecrets(
                        spuId,
                        skuId,
                        secretText,
                        file,
                        batchNo,
                        duplicatePolicy,
                        adminSessionService.current(request)
                )
        );
    }

    @DeleteMapping("/images/preupload")
    public AdminApiResponse<Void> cancelPreupload(@RequestBody(required = false) AdminProductImageCancelRequest request) {
        adminProductSpuService.cancelPreupload(request);
        return AdminApiResponse.ok(null);
    }

    @PostMapping("/spu")
    public AdminApiResponse<AdminProductSpuResponse> create(@RequestBody AdminProductSpuCreateRequest request) {
        return AdminApiResponse.ok(adminProductSpuService.create(request));
    }

    @GetMapping("/spu/{id}")
    public AdminApiResponse<AdminProductSpuDetailResponse> detail(@PathVariable Long id) {
        return AdminApiResponse.ok(adminProductSpuService.getDetail(id));
    }

    @GetMapping("/spu/{spuId}/sku/{skuId}")
    public AdminApiResponse<AdminProductSpuDetailSkuResponse> skuDetail(@PathVariable Long spuId,
                                                                        @PathVariable String skuId) {
        return AdminApiResponse.ok(adminProductSpuService.getSkuDetail(spuId, skuId));
    }

    @GetMapping("/spu/{spuId}/sku/hot")
    public AdminApiResponse<List<AdminProductHotSkuResponse>> hotSkus(@PathVariable Long spuId) {
        return AdminApiResponse.ok(adminProductHotSkuService.listHotSkus(spuId));
    }

    @GetMapping("/spu/{spuId}/sku/hot/{skuId}")
    public AdminApiResponse<AdminProductHotSkuResponse> hotSkuDetail(@PathVariable Long spuId,
                                                                     @PathVariable String skuId) {
        return AdminApiResponse.ok(adminProductHotSkuService.getHotSku(spuId, skuId));
    }

    @PostMapping("/spu/{spuId}/sku/hot/batch-enable")
    public AdminApiResponse<AdminProductHotSkuBatchResponse> batchEnableHotSkus(
            @PathVariable Long spuId,
            @RequestBody AdminProductHotSkuBatchEnableRequest request) {
        return AdminApiResponse.ok(adminProductHotSkuService.batchEnable(spuId, request));
    }

    @DeleteMapping("/spu/{spuId}/sku/hot/batch")
    public AdminApiResponse<AdminProductHotSkuBatchResponse> batchDeleteHotSkus(
            @PathVariable Long spuId,
            @RequestBody AdminProductSkuBatchIdsRequest request) {
        return AdminApiResponse.ok(adminProductHotSkuService.batchDelete(spuId, request));
    }

    @PostMapping("/spu/{spuId}/sku")
    public AdminApiResponse<AdminProductSpuDetailSkuResponse> createSku(@PathVariable Long spuId,
                                                                        @RequestBody AdminProductSkuCreateRequest request) {
        return AdminApiResponse.ok(adminProductSpuService.createSku(spuId, request));
    }

    @PutMapping("/spu/{spuId}/sku/{skuId}")
    public AdminApiResponse<AdminProductSpuDetailSkuResponse> updateSku(@PathVariable Long spuId,
                                                                        @PathVariable String skuId,
                                                                        @RequestBody AdminProductSkuUpdateRequest request) {
        return AdminApiResponse.ok(adminProductSpuService.updateSku(spuId, skuId, request));
    }

    @PatchMapping("/spu/{spuId}/sku/{skuId}/status")
    public AdminApiResponse<AdminProductSpuDetailSkuResponse> changeSkuStatus(@PathVariable Long spuId,
                                                                              @PathVariable String skuId,
                                                                              @RequestBody AdminProductSpuStatusRequest request) {
        return AdminApiResponse.ok(adminProductSpuService.changeSkuStatus(spuId, skuId, request));
    }

    @DeleteMapping("/spu/{spuId}/sku/{skuId}")
    public AdminApiResponse<AdminProductSkuDeleteResponse> deleteSku(@PathVariable Long spuId,
                                                                     @PathVariable String skuId) {
        return AdminApiResponse.ok(adminProductSpuService.deleteSku(spuId, skuId));
    }

    @PatchMapping("/spu/{spuId}/sku/batch-status")
    public AdminApiResponse<AdminProductSkuBatchResponse> batchChangeSkuStatus(@PathVariable Long spuId,
                                                                              @RequestBody AdminProductSkuBatchStatusRequest request) {
        return AdminApiResponse.ok(adminProductSpuService.batchChangeSkuStatus(spuId, request));
    }

    @DeleteMapping("/spu/{spuId}/sku/batch")
    public AdminApiResponse<AdminProductSkuBatchResponse> batchDeleteSku(@PathVariable Long spuId,
                                                                         @RequestBody AdminProductSkuBatchIdsRequest request) {
        return AdminApiResponse.ok(adminProductSpuService.batchDeleteSku(spuId, request));
    }

    @PutMapping("/spu/{id}")
    public AdminApiResponse<AdminProductSpuDetailResponse> updateDetail(@PathVariable Long id,
                                                                        @RequestBody AdminProductSpuDetailUpdateRequest request) {
        return AdminApiResponse.ok(adminProductSpuService.updateDetail(id, request));
    }

    @PatchMapping("/spu/{id}/status")
    public AdminApiResponse<AdminProductSpuResponse> changeStatus(@PathVariable Long id,
                                                                  @RequestBody AdminProductSpuStatusRequest request) {
        return AdminApiResponse.ok(adminProductSpuService.changeStatus(id, request));
    }

    @PostMapping("/spu/batch-disable")
    public AdminApiResponse<AdminProductSpuBatchDisableResponse> batchDisable(
            @RequestBody AdminProductSpuBatchIdsRequest request) {
        return AdminApiResponse.ok(adminProductSpuService.batchDisable(request));
    }

    @DeleteMapping("/spu/batch")
    public AdminApiResponse<AdminProductSpuBatchDeleteResponse> batchDelete(
            @RequestBody AdminProductSpuBatchIdsRequest request) {
        return AdminApiResponse.ok(adminProductSpuService.batchDelete(request));
    }

    @PostMapping("/spu/category/{categoryId}/batch-disable")
    public AdminApiResponse<AdminProductSpuBatchDisableResponse> batchDisableByLeafCategory(
            @PathVariable Long categoryId) {
        return AdminApiResponse.ok(adminProductSpuService.batchDisableByLeafCategory(categoryId));
    }

    @DeleteMapping("/spu/category/{categoryId}/batch")
    public AdminApiResponse<AdminProductSpuBatchDeleteResponse> batchDeleteByLeafCategory(
            @PathVariable Long categoryId) {
        return AdminApiResponse.ok(adminProductSpuService.batchDeleteByLeafCategory(categoryId));
    }
}
