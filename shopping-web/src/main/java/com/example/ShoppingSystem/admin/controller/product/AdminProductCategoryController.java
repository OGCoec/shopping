package com.example.ShoppingSystem.admin.controller.product;

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

@RestController
@RequestMapping("/shopping/admin/api/product-categories")
public class AdminProductCategoryController {

    private final AdminProductCategoryService adminProductCategoryService;

    public AdminProductCategoryController(AdminProductCategoryService adminProductCategoryService) {
        this.adminProductCategoryService = adminProductCategoryService;
    }

    @GetMapping("/tree")
    public AdminApiResponse<List<AdminProductCategoryTreeNodeResponse>> tree(
            @RequestParam(value = "keyword", required = false) String keyword) {
        return AdminApiResponse.ok(adminProductCategoryService.tree(keyword));
    }

    @PostMapping
    public AdminApiResponse<AdminProductCategoryTreeNodeResponse> create(
            @RequestBody AdminProductCategoryCreateRequest request) {
        return AdminApiResponse.ok(adminProductCategoryService.create(request));
    }

    @PutMapping("/{id}")
    public AdminApiResponse<AdminProductCategoryTreeNodeResponse> update(
            @PathVariable Long id,
            @RequestBody AdminProductCategoryUpdateRequest request) {
        return AdminApiResponse.ok(adminProductCategoryService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    public AdminApiResponse<AdminProductCategoryTreeNodeResponse> changeStatus(
            @PathVariable Long id,
            @RequestBody AdminProductCategoryStatusRequest request) {
        return AdminApiResponse.ok(adminProductCategoryService.changeStatus(id, request));
    }

    @PostMapping("/batch-disable")
    public AdminApiResponse<AdminProductCategoryBatchDisableResponse> batchDisable(
            @RequestBody AdminProductCategoryBatchDisableRequest request) {
        return AdminApiResponse.ok(adminProductCategoryService.batchDisable(request));
    }

    @DeleteMapping("/{id}")
    public AdminApiResponse<Void> delete(@PathVariable Long id) {
        adminProductCategoryService.delete(id);
        return AdminApiResponse.ok(null);
    }
}
