package com.example.ShoppingSystem.product.service;
import java.util.Collection;
public interface ProductCategoryBloomService {
    public void rebuildOnStartup();

    public boolean mightActiveCategoryExist(Long categoryId);

    public void addActiveCategoryIdsAfterCommit(Collection<Long> categoryIds);

    public void removeActiveCategoryIdsAfterCommit(Collection<Long> categoryIds);
}
