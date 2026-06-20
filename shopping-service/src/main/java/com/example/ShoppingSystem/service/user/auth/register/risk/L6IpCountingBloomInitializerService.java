package com.example.ShoppingSystem.service.user.auth.register.risk;

import com.example.ShoppingSystem.mapper.risk.IpReputationProfileMapper;
import com.example.ShoppingSystem.redisfilter.CountingBloomFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import java.util.List;

public interface L6IpCountingBloomInitializerService {
    public void rebuildL6FiltersOnStartup();
}
