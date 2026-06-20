package com.example.ShoppingSystem.admin.service.product;

import com.example.ShoppingSystem.mapper.product.ProductSpuMapper;
import com.example.ShoppingSystem.redisfilter.CountingBloomFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import java.util.List;

public interface AdminProductSpuBloomInitializerService {
    public void rebuildOnStartup();
}
