package com.example.ShoppingSystem.product.service;

import com.example.ShoppingSystem.config.datasource.ProductReadReplicaQueryExecutor;
import com.example.ShoppingSystem.mapper.product.ProductCategoryMapper;
import com.example.ShoppingSystem.product.dto.ProductCategoryRelationResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public interface PublicProductCategoryRelationService {
    public ProductCategoryRelationResponse getRelation(Long id);

    public void evictAfterCommit();
}
