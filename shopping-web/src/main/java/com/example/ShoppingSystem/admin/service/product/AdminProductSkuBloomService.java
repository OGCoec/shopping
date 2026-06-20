package com.example.ShoppingSystem.admin.service.product;

import com.example.ShoppingSystem.Utils.ProductSkuIdCodec;
import com.example.ShoppingSystem.redisfilter.CountingBloomFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.Collection;
import java.util.List;

public interface AdminProductSkuBloomService {
    public boolean mightSkuExist(String skuId);

    public void addSkuIdsAfterCommit(Collection<String> skuIds);

    public void removeSkuIdsAfterCommit(Collection<String> skuIds);
}
