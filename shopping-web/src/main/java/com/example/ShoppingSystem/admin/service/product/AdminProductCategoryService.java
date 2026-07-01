package com.example.ShoppingSystem.admin.service.product;
import com.example.ShoppingSystem.admin.dto.AdminProductCategoryBatchDisableRequest;
import com.example.ShoppingSystem.admin.dto.AdminProductCategoryBatchDisableResponse;
import com.example.ShoppingSystem.admin.dto.AdminProductCategoryCreateRequest;
import com.example.ShoppingSystem.admin.dto.AdminProductCategoryStatusRequest;
import com.example.ShoppingSystem.admin.dto.AdminProductCategoryTreeNodeResponse;
import com.example.ShoppingSystem.admin.dto.AdminProductCategoryUpdateRequest;
import java.util.List;
public interface AdminProductCategoryService {
    public List<AdminProductCategoryTreeNodeResponse> tree();

    public List<AdminProductCategoryTreeNodeResponse> tree(String keyword);

    public AdminProductCategoryTreeNodeResponse create(AdminProductCategoryCreateRequest request);

    public AdminProductCategoryTreeNodeResponse update(Long id, AdminProductCategoryUpdateRequest request);

    public AdminProductCategoryTreeNodeResponse changeStatus(Long id, AdminProductCategoryStatusRequest request);

    public AdminProductCategoryBatchDisableResponse batchDisable(AdminProductCategoryBatchDisableRequest request);

    public void delete(Long id);
}
