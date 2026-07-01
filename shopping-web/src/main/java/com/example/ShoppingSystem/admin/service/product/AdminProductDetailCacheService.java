package com.example.ShoppingSystem.admin.service.product;

import com.example.ShoppingSystem.admin.dto.AdminProductSpuDetailResponse;
import java.util.Collection;
import java.util.function.Supplier;
public interface AdminProductDetailCacheService {
    public AdminProductSpuDetailResponse getDetail(Long spuId, Supplier<AdminProductSpuDetailResponse> loader);

    public void invalidateAfterCommit(Collection<Long> spuIds);

    public void syncCreatedProductAfterCommit(Long spuId);

    public void deleteProductBloomIdsAfterCommit(Collection<Long> spuIds);
}
