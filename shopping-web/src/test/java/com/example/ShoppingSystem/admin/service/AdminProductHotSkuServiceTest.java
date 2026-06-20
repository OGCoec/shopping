package com.example.ShoppingSystem.admin.service;

import com.example.ShoppingSystem.Utils.HybridSemaphoreIdWorker;
import com.example.ShoppingSystem.admin.dto.AdminProductHotSkuResponse;
import com.example.ShoppingSystem.admin.service.common.AdminServiceException;
import com.example.ShoppingSystem.admin.service.product.AdminProductHotSkuService;
import com.example.ShoppingSystem.config.datasource.ProductReadReplicaProperties;
import com.example.ShoppingSystem.config.datasource.ProductReadReplicaQueryExecutor;
import com.example.ShoppingSystem.config.datasource.ReadReplicaQueryRunner;
import com.example.ShoppingSystem.mapper.product.ProductHotSkuMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.ShoppingSystem.admin.service.product.impl.AdminProductHotSkuService.AdminProductHotSkuServiceImpl;
class AdminProductHotSkuServiceTest {

    private static final String VALID_SKU_ID = "abc123";
    private static final String ID_HEX = "0123456789abcdeffedcba9876543210";
    private static final String SKU_ID_HEX = "fedcba98765432100123456789abcdef";

    private final ProductHotSkuMapper productHotSkuMapper = mock(ProductHotSkuMapper.class);
    private final HybridSemaphoreIdWorker hybridSemaphoreIdWorker = mock(HybridSemaphoreIdWorker.class);
    private final StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AdminProductHotSkuService service = new AdminProductHotSkuServiceImpl(
            productHotSkuMapper,
            hybridSemaphoreIdWorker,
            stringRedisTemplate,
            objectMapper,
            new ProductReadReplicaQueryExecutor(new ProductReadReplicaProperties(), mock(ReadReplicaQueryRunner.class)));

    @Test
    void hitReturnsFullFieldsAndOverridesRemainingFromRedis() {
        when(productHotSkuMapper.findHotSkuBySkuId(any(), any())).thenReturn(sampleRow(5));
        ValueOperations<String, String> valueOps = mockValueOps();
        when(valueOps.get(anyString())).thenReturn("42");

        AdminProductHotSkuResponse response = service.getHotSku(100L, VALID_SKU_ID);

        assertEquals(100L, response.spuId());
        assertEquals("SKU-CODE", response.skuCode());
        assertEquals("SKU Name", response.skuName());
        assertEquals(80, response.stockQuantity());
        assertEquals("ENABLED", response.status());
        assertEquals(42, response.remainingQuantity());
    }

    @Test
    void redisMissingFallsBackToDbRemaining() {
        when(productHotSkuMapper.findHotSkuBySkuId(any(), any())).thenReturn(sampleRow(5));
        ValueOperations<String, String> valueOps = mockValueOps();
        when(valueOps.get(anyString())).thenReturn(null);

        AdminProductHotSkuResponse response = service.getHotSku(100L, VALID_SKU_ID);

        assertEquals(5, response.remainingQuantity());
    }

    @Test
    void redisInvalidValueFallsBackToDbRemaining() {
        when(productHotSkuMapper.findHotSkuBySkuId(any(), any())).thenReturn(sampleRow(7));
        ValueOperations<String, String> valueOps = mockValueOps();
        when(valueOps.get(anyString())).thenReturn("not-a-number");

        AdminProductHotSkuResponse response = service.getHotSku(100L, VALID_SKU_ID);

        assertEquals(7, response.remainingQuantity());
    }

    @Test
    void redisExceptionFallsBackToDbRemaining() {
        when(productHotSkuMapper.findHotSkuBySkuId(any(), any())).thenReturn(sampleRow(9));
        when(stringRedisTemplate.opsForValue()).thenThrow(new RuntimeException("redis down"));

        AdminProductHotSkuResponse response = service.getHotSku(100L, VALID_SKU_ID);

        assertEquals(9, response.remainingQuantity());
    }

    @Test
    void missingRowThrowsNotFound() {
        when(productHotSkuMapper.findHotSkuBySkuId(any(), any())).thenReturn(null);

        AdminServiceException ex = assertThrows(AdminServiceException.class,
                () -> service.getHotSku(100L, VALID_SKU_ID));

        assertEquals("ADMIN_PRODUCT_HOT_SKU_NOT_FOUND", ex.getCode());
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void emptyRowThrowsNotFound() {
        when(productHotSkuMapper.findHotSkuBySkuId(any(), any())).thenReturn(new LinkedHashMap<>());

        AdminServiceException ex = assertThrows(AdminServiceException.class,
                () -> service.getHotSku(100L, VALID_SKU_ID));

        assertEquals("ADMIN_PRODUCT_HOT_SKU_NOT_FOUND", ex.getCode());
    }

    @Test
    void invalidSkuIdThrowsSkuIdInvalid() {
        AdminServiceException ex = assertThrows(AdminServiceException.class,
                () -> service.getHotSku(100L, "bad id!!"));

        assertEquals("ADMIN_PRODUCT_SKU_ID_INVALID", ex.getCode());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void invalidSpuIdThrowsProductIdInvalid() {
        AdminServiceException ex = assertThrows(AdminServiceException.class,
                () -> service.getHotSku(0L, VALID_SKU_ID));

        assertEquals("ADMIN_PRODUCT_ID_INVALID", ex.getCode());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @SuppressWarnings("unchecked")
    private ValueOperations<String, String> mockValueOps() {
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        return valueOps;
    }

    private Map<String, Object> sampleRow(int remainingQuantity) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", ID_HEX);
        row.put("spuId", 100L);
        row.put("skuId", SKU_ID_HEX);
        row.put("skuCode", "SKU-CODE");
        row.put("skuName", "SKU Name");
        row.put("skuStockQuantity", 100);
        row.put("skuStatus", "ACTIVE");
        row.put("stockQuantity", 80);
        row.put("remainingQuantity", remainingQuantity);
        row.put("status", "ENABLED");
        row.put("startAt", null);
        row.put("endAt", null);
        row.put("version", 1L);
        row.put("createdAt", null);
        row.put("updatedAt", null);
        return row;
    }
}
