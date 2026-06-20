package com.example.ShoppingSystem.product.service;

import com.example.ShoppingSystem.mapper.product.ProductCategoryMapper;
import com.example.ShoppingSystem.redisfilter.CountingBloomFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.Collection;
import java.util.List;

public interface ProductCategoryBloomService {
    public void rebuildOnStartup();

    public boolean mightActiveCategoryExist(Long categoryId);

    public void addActiveCategoryIdsAfterCommit(Collection<Long> categoryIds);

    public void removeActiveCategoryIdsAfterCommit(Collection<Long> categoryIds);
}
