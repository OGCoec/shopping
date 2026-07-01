package com.example.ShoppingSystem.product.service;
import com.example.ShoppingSystem.product.dto.PublicProductCategoryTreeNodeResponse;
import java.util.List;
public interface PublicProductCategoryBrowseService {
    public List<PublicProductCategoryTreeNodeResponse> tree();

    public List<PublicProductCategoryTreeNodeResponse> search(String keyword);
}
