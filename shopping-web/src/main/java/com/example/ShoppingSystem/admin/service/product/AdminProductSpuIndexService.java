package com.example.ShoppingSystem.admin.service.product;
import java.util.Collection;
public interface AdminProductSpuIndexService {

    public static final String PRODUCT_SPU_INDEX_ALIAS = "shopping_product_spu";
    public void initializeOnStartup();

    public void syncProductsAfterCommit(Collection<Long> spuIds);

    public void syncProductsByCategoryIdsAfterCommit(Collection<Long> categoryIds);

    public void deleteProductsAfterCommit(Collection<Long> spuIds);
}
