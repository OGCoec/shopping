package com.example.ShoppingSystem.admin.service.product;
import java.util.Collection;
public interface AdminProductCategoryIndexService {

    public static final String PRODUCT_CATEGORY_INDEX_ALIAS = "shopping_product_category";
    public void initializeOnStartup();

    public void syncCategoriesAfterCommit(Collection<Long> categoryIds);

    public void deleteCategoriesAfterCommit(Collection<Long> categoryIds);
}
