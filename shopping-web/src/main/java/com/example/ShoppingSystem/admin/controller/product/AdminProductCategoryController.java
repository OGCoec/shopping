package com.example.ShoppingSystem.admin.controller.product;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.ShoppingSystem.admin.dto.AdminApiResponse;
import com.example.ShoppingSystem.admin.dto.AdminProductCategoryBatchDisableRequest;
import com.example.ShoppingSystem.admin.dto.AdminProductCategoryBatchDisableResponse;
import com.example.ShoppingSystem.admin.dto.AdminProductCategoryCreateRequest;
import com.example.ShoppingSystem.admin.dto.AdminProductCategoryStatusRequest;
import com.example.ShoppingSystem.admin.dto.AdminProductCategoryTreeNodeResponse;
import com.example.ShoppingSystem.admin.dto.AdminProductCategoryUpdateRequest;
import com.example.ShoppingSystem.admin.service.product.AdminProductCategoryService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "后台商品分类管理", description = "后台商品分类管理接口")
@RestController
@RequestMapping("/shopping/admin/api/product-categories")
public class AdminProductCategoryController {

    private final AdminProductCategoryService adminProductCategoryService;

    public AdminProductCategoryController(AdminProductCategoryService adminProductCategoryService) {
        this.adminProductCategoryService = adminProductCategoryService;
    }

    @Operation(summary = "查询商品分类树")
    @GetMapping("/tree")
    public AdminApiResponse<List<AdminProductCategoryTreeNodeResponse>> tree(
            @RequestParam(value = "keyword", required = false) String keyword) {
        return AdminApiResponse.ok(adminProductCategoryService.tree(keyword));
    }

    @Operation(summary = "创建商品分类")
    @PostMapping
    public AdminApiResponse<AdminProductCategoryTreeNodeResponse> create(
            @RequestBody AdminProductCategoryCreateRequest request) {
        return AdminApiResponse.ok(adminProductCategoryService.create(request));
    }

    @Operation(summary = "更新商品分类")
    @PutMapping("/{id}")
    public AdminApiResponse<AdminProductCategoryTreeNodeResponse> update(
            @PathVariable Long id,
            @RequestBody AdminProductCategoryUpdateRequest request) {
        return AdminApiResponse.ok(adminProductCategoryService.update(id, request));
    }

    @Operation(summary = "修改商品分类状态")
    @PatchMapping("/{id}/status")
    public AdminApiResponse<AdminProductCategoryTreeNodeResponse> changeStatus(
            @PathVariable Long id,
            @RequestBody AdminProductCategoryStatusRequest request) {
        return AdminApiResponse.ok(adminProductCategoryService.changeStatus(id, request));
    }

    @Operation(summary = "批量禁用商品分类")
    @PostMapping("/batch-disable")
    public AdminApiResponse<AdminProductCategoryBatchDisableResponse> batchDisable(
            @RequestBody AdminProductCategoryBatchDisableRequest request) {
        return AdminApiResponse.ok(adminProductCategoryService.batchDisable(request));
    }

    @Operation(summary = "删除商品分类")
    @DeleteMapping("/{id}")
    public AdminApiResponse<Void> delete(@PathVariable Long id) {
        adminProductCategoryService.delete(id);
        return AdminApiResponse.ok(null);
    }
}
