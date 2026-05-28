package com.example.ShoppingSystem.admin.controller.product;

import com.example.ShoppingSystem.admin.dto.AdminApiResponse;
import com.example.ShoppingSystem.admin.dto.AdminProductImageCancelRequest;
import com.example.ShoppingSystem.admin.dto.AdminProductImagePreuploadResponse;
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
import com.example.ShoppingSystem.admin.service.product.AdminProductSpuService;
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

@RestController
@RequestMapping("/shopping/admin/api/products")
public class AdminProductSpuController {

    private final AdminProductSpuService adminProductSpuService;

    public AdminProductSpuController(AdminProductSpuService adminProductSpuService) {
        this.adminProductSpuService = adminProductSpuService;
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
