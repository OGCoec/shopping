package com.example.ShoppingSystem.quota.writeback;

import com.example.ShoppingSystem.mapper.risk.IpReputationProfileMapper;
import com.example.ShoppingSystem.quota.IpRiskCachedPayload;
import com.example.ShoppingSystem.quota.IpRiskLocalCacheStore;
import com.example.ShoppingSystem.service.user.auth.register.risk.IpL6CountingBloomDecisionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public interface IpRiskWritebackExecutorService {
    public void executeActions(String ip, IpRiskCachedPayload payload, Set<IpRiskWritebackAction> actions);
}
