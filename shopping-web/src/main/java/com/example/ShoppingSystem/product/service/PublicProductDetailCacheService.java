package com.example.ShoppingSystem.product.service;
import com.example.ShoppingSystem.product.dto.PublicProductDetailResponse;
import java.util.Collection;
import java.util.function.Supplier;

public interface PublicProductDetailCacheService {
    public PublicProductDetailResponse getDetail(Long spuId, Supplier<PublicProductDetailResponse> loader);

    public void invalidateAfterCommit(Collection<Long> spuIds);

    public void invalidateByCategoryIdsAfterCommit(Collection<Long> categoryIds);
}
