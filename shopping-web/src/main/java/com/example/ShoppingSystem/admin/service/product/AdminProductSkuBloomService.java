package com.example.ShoppingSystem.admin.service.product;
import java.util.Collection;
public interface AdminProductSkuBloomService {
    public boolean mightSkuExist(String skuId);

    public void addSkuIdsAfterCommit(Collection<String> skuIds);

    public void removeSkuIdsAfterCommit(Collection<String> skuIds);
}
