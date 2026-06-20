package com.example.ShoppingSystem.admin.service.product;

import cn.hutool.core.util.IdUtil;
import com.example.ShoppingSystem.Utils.AliyunUtils;
import com.example.ShoppingSystem.Utils.HybridSemaphoreIdWorker;
import com.example.ShoppingSystem.Utils.ProductSkuIdCodec;
import com.example.ShoppingSystem.Utils.SnowflakeIdWorker;
import com.example.ShoppingSystem.admin.dto.AdminProductImageCancelRequest;
import com.example.ShoppingSystem.admin.dto.AdminProductImagePreuploadResponse;
import com.example.ShoppingSystem.admin.dto.AdminProductImageUsageRequest;
import com.example.ShoppingSystem.admin.dto.AdminProductSkuBatchIdsRequest;
import com.example.ShoppingSystem.admin.dto.AdminProductSkuBatchResponse;
import com.example.ShoppingSystem.admin.dto.AdminProductSkuBatchStatusRequest;
import com.example.ShoppingSystem.admin.dto.AdminProductSkuCreateRequest;
import com.example.ShoppingSystem.admin.dto.AdminProductSkuDeleteResponse;
import com.example.ShoppingSystem.admin.dto.AdminProductSkuUpdateRequest;
import com.example.ShoppingSystem.admin.dto.AdminProductSpuBatchDeleteResponse;
import com.example.ShoppingSystem.admin.dto.AdminProductSpuBatchDisableResponse;
import com.example.ShoppingSystem.admin.dto.AdminProductSpuBatchIdsRequest;
import com.example.ShoppingSystem.admin.dto.AdminProductSpuCreateRequest;
import com.example.ShoppingSystem.admin.dto.AdminProductSpuDetailResponse;
import com.example.ShoppingSystem.admin.dto.AdminProductSpuDetailSkuResponse;
import com.example.ShoppingSystem.admin.dto.AdminProductSpuDetailUpdateRequest;
import com.example.ShoppingSystem.admin.dto.AdminProductSpuPageResponse;
import com.example.ShoppingSystem.admin.dto.AdminProductSpuResponse;
import com.example.ShoppingSystem.admin.dto.AdminProductSpuStatusRequest;
import com.example.ShoppingSystem.admin.service.common.AdminPaginationValidator;
import com.example.ShoppingSystem.admin.service.product.AdminProductSkuService.NormalizedSkuUpdate;
import com.example.ShoppingSystem.config.datasource.ProductReadReplicaQueryExecutor;
import com.example.ShoppingSystem.mapper.product.ProductCategoryMapper;
import com.example.ShoppingSystem.mapper.product.ProductSpuMapper;
import com.example.ShoppingSystem.product.service.PublicProductDetailCacheService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import com.example.ShoppingSystem.admin.service.common.AdminServiceException;

public interface AdminProductSpuService {
    public AdminProductSpuPageResponse page(Integer page, Integer pageSize, String name, Long categoryId, String status);

    public AdminProductImagePreuploadResponse preuploadMainImage(MultipartFile file);

    public void cancelPreupload(AdminProductImageCancelRequest request);

    public AdminProductSpuDetailResponse getDetail(Long id);

    public AdminProductSpuDetailSkuResponse getSkuDetail(Long id, String skuId);

    public AdminProductSpuDetailSkuResponse createSku(Long id, AdminProductSkuCreateRequest request);

    public AdminProductSpuDetailSkuResponse updateSku(Long id, String skuId, AdminProductSkuUpdateRequest request);

    public AdminProductSpuDetailSkuResponse changeSkuStatus(Long id, String skuId, AdminProductSpuStatusRequest request);

    public AdminProductSkuDeleteResponse deleteSku(Long id, String skuId);

    public AdminProductSkuBatchResponse batchChangeSkuStatus(Long id, AdminProductSkuBatchStatusRequest request);

    public AdminProductSkuBatchResponse batchDeleteSku(Long id, AdminProductSkuBatchIdsRequest request);

    public AdminProductSpuDetailResponse updateDetail(Long id, AdminProductSpuDetailUpdateRequest request);

    public AdminProductSpuResponse create(AdminProductSpuCreateRequest request);

    public AdminProductSpuResponse changeStatus(Long id, AdminProductSpuStatusRequest request);

    public AdminProductSpuBatchDisableResponse batchDisable(AdminProductSpuBatchIdsRequest request);

    public AdminProductSpuBatchDisableResponse batchDisableByLeafCategory(Long id);

    public AdminProductSpuBatchDeleteResponse batchDelete(AdminProductSpuBatchIdsRequest request);

    public AdminProductSpuBatchDeleteResponse batchDeleteByLeafCategory(Long id);
}
