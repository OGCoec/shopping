package com.example.ShoppingSystem.admin.service.product;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface AdminProductCategorySearchService {
    public List<Long> searchMatchedIds(String keyword);

    public Map<Long, String> searchNameHighlights(String keyword, Collection<Long> displayIds);
}
