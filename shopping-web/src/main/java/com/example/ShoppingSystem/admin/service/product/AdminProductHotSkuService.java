package com.example.ShoppingSystem.admin.service.product;

import com.example.ShoppingSystem.Utils.HybridSemaphoreIdWorker;
import com.example.ShoppingSystem.Utils.ProductSkuIdCodec;
import com.example.ShoppingSystem.admin.dto.AdminProductHotSkuBatchEnableRequest;
import com.example.ShoppingSystem.admin.dto.AdminProductHotSkuBatchResponse;
import com.example.ShoppingSystem.admin.dto.AdminProductHotSkuEnableItem;
import com.example.ShoppingSystem.admin.dto.AdminProductHotSkuResponse;
import com.example.ShoppingSystem.admin.dto.AdminProductSkuBatchIdsRequest;
import com.example.ShoppingSystem.admin.service.common.AdminServiceException;
import com.example.ShoppingSystem.config.datasource.ProductReadReplicaQueryExecutor;
import com.example.ShoppingSystem.mapper.product.ProductHotSkuMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public interface AdminProductHotSkuService {
    public List<AdminProductHotSkuResponse> listHotSkus(Long rawSpuId);

    public AdminProductHotSkuResponse getHotSku(Long rawSpuId, String rawSkuId);

    public AdminProductHotSkuBatchResponse batchEnable(Long rawSpuId, AdminProductHotSkuBatchEnableRequest request);

    public AdminProductHotSkuBatchResponse batchDelete(Long rawSpuId, AdminProductSkuBatchIdsRequest request);
}
