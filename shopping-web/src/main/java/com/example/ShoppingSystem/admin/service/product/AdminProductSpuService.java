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
import org.springframework.stereotype.Service;
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

@Slf4j
@Service
public class AdminProductSpuService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_DISABLED = "DISABLED";
    private static final Set<String> SUPPORTED_STATUS = Set.of(STATUS_ACTIVE, STATUS_DISABLED);
    private static final int MAX_NAME_LENGTH = 128;
    private static final int MAX_SUBTITLE_LENGTH = 255;
    private static final int MAX_BRAND_NAME_LENGTH = 64;
    private static final int MAX_IMAGE_URL_LENGTH = 512;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int NANO_ID_LENGTH = 48;
    private static final char[] BASE62_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final Duration IMAGE_SESSION_TTL = Duration.ofHours(1);
    private static final String IMAGE_SESSION_PREFIX = "admin:product:spu:image:session:";
    private static final String IMAGE_CLEANUP_SET_KEY = "admin:product:spu:image:cleanup";
    private static final int CLEANUP_BATCH_SIZE = 100;
    private static final int CLEANUP_MAX_BATCHES_PER_RUN = 5;

    private final ProductSpuMapper productSpuMapper;
    private final ProductCategoryMapper productCategoryMapper;
    private final SnowflakeIdWorker snowflakeIdWorker;
    private final HybridSemaphoreIdWorker hybridSemaphoreIdWorker;
    private final AliyunUtils aliyunUtils;
    private final org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final AdminProductSpuAssembler assembler;
    private final AdminProductSkuService skuService;
    private final AdminProductSkuBloomService skuBloomService;
    private final AdminProductDetailCacheService detailCacheService;
    private final PublicProductDetailCacheService publicDetailCacheService;
    private final AdminProductSpuSearchService productSearchService;
    private final AdminProductSpuIndexService productIndexService;
    private final ProductReadReplicaQueryExecutor productReadReplicaQueryExecutor;
    private final ProductImageUrlValidator productImageUrlValidator;

    public AdminProductSpuService(ProductSpuMapper productSpuMapper,
                                  ProductCategoryMapper productCategoryMapper,
                                  SnowflakeIdWorker snowflakeIdWorker,
                                  HybridSemaphoreIdWorker hybridSemaphoreIdWorker,
                                  AliyunUtils aliyunUtils,
                                  org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate,
                                  ObjectMapper objectMapper,
                                  AdminProductSpuAssembler assembler,
                                  AdminProductSkuService skuService,
                                  AdminProductSkuBloomService skuBloomService,
                                  AdminProductDetailCacheService detailCacheService,
                                  PublicProductDetailCacheService publicDetailCacheService,
                                  AdminProductSpuSearchService productSearchService,
                                  AdminProductSpuIndexService productIndexService,
                                  ProductReadReplicaQueryExecutor productReadReplicaQueryExecutor,
                                  ProductImageUrlValidator productImageUrlValidator) {
        this.productSpuMapper = productSpuMapper;
        this.productCategoryMapper = productCategoryMapper;
        this.snowflakeIdWorker = snowflakeIdWorker;
        this.hybridSemaphoreIdWorker = hybridSemaphoreIdWorker;
        this.aliyunUtils = aliyunUtils;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.assembler = assembler;
        this.skuService = skuService;
        this.skuBloomService = skuBloomService;
        this.detailCacheService = detailCacheService;
        this.publicDetailCacheService = publicDetailCacheService;
        this.productSearchService = productSearchService;
        this.productIndexService = productIndexService;
        this.productReadReplicaQueryExecutor = productReadReplicaQueryExecutor;
        this.productImageUrlValidator = productImageUrlValidator;
    }

    public AdminProductSpuPageResponse page(Integer page, Integer pageSize, String name, Long categoryId, String status) {
        int normalizedPage = AdminPaginationValidator.normalizePage(page);
        int normalizedPageSize = AdminPaginationValidator.normalizePageSize(pageSize, DEFAULT_PAGE_SIZE);
        String normalizedName = normalizeOptionalText(name, MAX_NAME_LENGTH, "商品名称");
        Long normalizedCategoryId = normalizeOptionalId(categoryId, "分类 ID");
        String normalizedStatus = normalizeOptionalStatus(status);
        return productSearchService.searchPage(
                normalizedName,
                normalizedCategoryId,
                normalizedStatus,
                normalizedPage,
                normalizedPageSize);
    }

    public AdminProductImagePreuploadResponse preuploadMainImage(MultipartFile file) {
        validateImageFile(file);
        String uploadSessionId = IdUtil.nanoId(NANO_ID_LENGTH);
        String imageId = nextHybridImageBase62();
        String ext = resolveExt(file.getContentType());
        String objectKey = "product/temp/" + imageId + ext;
        String tempUrl = null;
        try {
            tempUrl = aliyunUtils.uploadFile(objectKey, file.getBytes()).get(60, TimeUnit.SECONDS);
            if (tempUrl == null || tempUrl.isBlank()) {
                throw new IllegalStateException("OSS returned blank url");
            }
            String sessionKey = imageSessionKey(uploadSessionId);
            Map<String, String> session = new LinkedHashMap<>();
            session.put("tempUrl", tempUrl);
            session.put("objectKey", objectKey);
            session.put("createdAt", String.valueOf(System.currentTimeMillis()));
            stringRedisTemplate.opsForHash().putAll(sessionKey, session);
            stringRedisTemplate.expire(sessionKey, IMAGE_SESSION_TTL);
            return new AdminProductImagePreuploadResponse(uploadSessionId, tempUrl);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            cleanupUploadedObjectAfterPreuploadFailure(objectKey, tempUrl);
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_IMAGE_PREUPLOAD_INTERRUPTED",
                    "商品主图预上传被中断，请重试。",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            cleanupUploadedObjectAfterPreuploadFailure(objectKey, tempUrl);
            log.warn("[商品管理] 商品主图预上传失败，objectKey={}", objectKey, e);
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_IMAGE_PREUPLOAD_FAILED",
                    "商品主图预上传失败，请重新上传。",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public void cancelPreupload(AdminProductImageCancelRequest request) {
        String uploadSessionId = normalizeText(request == null ? null : request.uploadSessionId());
        if (uploadSessionId.isEmpty()) {
            return;
        }
        ImageSession session = loadImageSession(uploadSessionId, false);
        if (session == null) {
            return;
        }
        String requestedTempUrl = normalizeText(request.tempUrl());
        if (!requestedTempUrl.isEmpty() && !requestedTempUrl.equals(session.tempUrl())) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_IMAGE_SESSION_MISMATCH",
                    "预上传图片与当前上传会话不匹配。",
                    HttpStatus.BAD_REQUEST);
        }
        deleteObjectKeysWithCompensation(List.of(session.objectKey()));
        stringRedisTemplate.delete(imageSessionKey(uploadSessionId));
    }

    public AdminProductSpuDetailResponse getDetail(Long id) {
        Long spuId = normalizeRequiredId(id, "商品 ID");
        return detailCacheService.getDetail(spuId, () ->
                productReadReplicaQueryExecutor.query(() -> findSpuDetailResponse(spuId)));
    }

    public AdminProductSpuDetailSkuResponse getSkuDetail(Long id, String skuId) {
        Long spuId = normalizeRequiredId(id, "商品 ID");
        String normalizedSkuId = normalizeSkuId(skuId);
        byte[] normalizedSkuIdBytes = skuIdBytes(normalizedSkuId);
        if (!skuBloomService.mightSkuExist(normalizedSkuId)) {
            throw skuNotFoundException();
        }
        return productReadReplicaQueryExecutor.query(() ->
                findSkuDetailResponse(spuId, normalizedSkuIdBytes));
    }

    @Transactional
    public AdminProductSpuDetailSkuResponse createSku(Long id, AdminProductSkuCreateRequest request) {
        Long spuId = normalizeRequiredId(id, "商品 ID");
        ensureProductExists(spuId);
        NormalizedSkuUpdate normalized = skuService.normalizeSkuCreate(spuId, request);
        SkuImageFinalizeResult imageFinalizeResult = finalizeRequestedSkuImages(
                spuId,
                normalized.finalId(),
                normalized.skuImageUrls(),
                normalizeImageUsageRequests(request == null ? null : request.imageUploadSessions()));
        registerTransferredImageSynchronization(
                imageFinalizeResult.tempObjectKeys(),
                imageFinalizeResult.finalObjectKeys(),
                imageFinalizeResult.sessionKeys());
        NormalizedSkuUpdate finalSku = normalized.withSkuImageUrls(imageFinalizeResult.skuImageUrls());
        Map<String, Object> result = productSpuMapper.insertSku(
                spuId,
                finalSku.finalIdBytes(),
                finalSku.skuCode(),
                finalSku.skuName(),
                toJsonString(finalSku.specJson()),
                toJsonString(finalSku.skuImageUrls()),
                finalSku.priceYuan(),
                finalSku.originalPriceYuan(),
                finalSku.stockQuantity(),
                finalSku.status());
        validateSkuCreateResult(result);
        skuBloomService.addSkuIdsAfterCommit(List.of(finalSku.finalId()));
        invalidateSkuProductAfterCommit(spuId);
        return findSkuDetailResponse(spuId, finalSku.finalIdBytes());
    }

    @Transactional
    public AdminProductSpuDetailSkuResponse updateSku(Long id, String skuId, AdminProductSkuUpdateRequest request) {
        Long spuId = normalizeRequiredId(id, "商品 ID");
        String normalizedSkuId = normalizeSkuId(skuId);
        byte[] normalizedSkuIdBytes = skuIdBytes(normalizedSkuId);
        ensureSkuExists(spuId, normalizedSkuIdBytes);
        NormalizedSkuUpdate normalized = skuService.normalizeSkuUpdate(spuId, normalizedSkuId, request);
        SkuImageFinalizeResult imageFinalizeResult = finalizeRequestedSkuImages(
                spuId,
                normalized.finalId(),
                normalized.skuImageUrls(),
                normalizeImageUsageRequests(request == null ? null : request.imageUploadSessions()));
        registerTransferredImageSynchronization(
                imageFinalizeResult.tempObjectKeys(),
                imageFinalizeResult.finalObjectKeys(),
                imageFinalizeResult.sessionKeys());
        NormalizedSkuUpdate finalSku = normalized.withSkuImageUrls(imageFinalizeResult.skuImageUrls());
        Map<String, Object> result = productSpuMapper.updateSkuBySpuIdAndSkuId(
                spuId,
                finalSku.finalIdBytes(),
                finalSku.skuCode(),
                finalSku.skuName(),
                toJsonString(finalSku.specJson()),
                toJsonString(finalSku.skuImageUrls()),
                finalSku.priceYuan(),
                finalSku.originalPriceYuan(),
                finalSku.stockQuantity(),
                finalSku.status());
        validateSkuUpdateResult(result);
        Set<String> oldKeys = new LinkedHashSet<>(cleanupObjectKeys(value(result, "oldImageUrlsJson")));
        oldKeys.removeAll(cleanupObjectKeys(collectJsonImageUrls(finalSku.skuImageUrls())));
        queueCleanupKeysAfterCommit(oldKeys);
        invalidateSkuProductAfterCommit(spuId);
        return findSkuDetailResponse(spuId, finalSku.finalIdBytes());
    }

    @Transactional
    public AdminProductSpuDetailSkuResponse changeSkuStatus(Long id, String skuId, AdminProductSpuStatusRequest request) {
        Long spuId = normalizeRequiredId(id, "商品 ID");
        String normalizedSkuId = normalizeSkuId(skuId);
        String status = normalizeStatus(request == null ? null : request.status(), "");
        byte[] normalizedSkuIdBytes = skuIdBytes(normalizedSkuId);
        Map<String, Object> result = productSpuMapper.updateSkuStatusBySpuIdAndSkuId(spuId, normalizedSkuIdBytes, status);
        validateSkuUpdateResult(result);
        invalidateSkuProductAfterCommit(spuId);
        return findSkuDetailResponse(spuId, normalizedSkuIdBytes);
    }

    @Transactional
    public AdminProductSkuDeleteResponse deleteSku(Long id, String skuId) {
        Long spuId = normalizeRequiredId(id, "商品 ID");
        String normalizedSkuId = normalizeSkuId(skuId);
        Map<String, Object> result = productSpuMapper.deleteSkuBySpuIdAndSkuIdReturningImages(spuId, skuIdBytes(normalizedSkuId));
        validateSkuDeleteResult(result);
        List<String> cleanupKeys = cleanupObjectKeys(value(result, "imageUrlsJson"));
        queueCleanupKeysAfterCommit(cleanupKeys);
        skuBloomService.removeSkuIdsAfterCommit(List.of(normalizedSkuId));
        invalidateSkuProductAfterCommit(spuId);
        return new AdminProductSkuDeleteResponse(spuId, normalizedSkuId, true, cleanupKeys.size());
    }

    @Transactional
    public AdminProductSkuBatchResponse batchChangeSkuStatus(Long id, AdminProductSkuBatchStatusRequest request) {
        Long spuId = normalizeRequiredId(id, "商品 ID");
        List<String> skuIds = normalizeBatchSkuIds(request == null ? null : request.ids());
        String status = normalizeStatus(request == null ? null : request.status(), "");
        Map<String, Object> result = productSpuMapper.batchUpdateSkuStatusByIds(spuId, skuIdBytes(skuIds), status);
        validateSkuBatchResult(result);
        invalidateSkuProductAfterCommit(spuId);
        return new AdminProductSkuBatchResponse(
                toInt(value(result, "requestedCount"), skuIds.size()),
                toInt(value(result, "matchedCount"), 0),
                toInt(value(result, "affectedCount"), 0));
    }

    @Transactional
    public AdminProductSkuBatchResponse batchDeleteSku(Long id, AdminProductSkuBatchIdsRequest request) {
        Long spuId = normalizeRequiredId(id, "商品 ID");
        List<String> skuIds = normalizeBatchSkuIds(request == null ? null : request.ids());
        Map<String, Object> result = productSpuMapper.batchDeleteSkuByIdsReturningImages(spuId, skuIdBytes(skuIds));
        validateSkuBatchResult(result);
        List<String> cleanupKeys = cleanupObjectKeys(value(result, "imageUrlsJson"));
        queueCleanupKeysAfterCommit(cleanupKeys);
        List<String> deletedSkuIds = parseSkuIdHexList(value(result, "deletedSkuIdsJson"));
        skuBloomService.removeSkuIdsAfterCommit(deletedSkuIds);
        invalidateSkuProductAfterCommit(spuId);
        return new AdminProductSkuBatchResponse(
                toInt(value(result, "requestedCount"), skuIds.size()),
                toInt(value(result, "matchedCount"), 0),
                toInt(value(result, "affectedCount"), 0));
    }

    @Transactional
    public AdminProductSpuDetailResponse updateDetail(Long id, AdminProductSpuDetailUpdateRequest request) {
        Long spuId = normalizeRequiredId(id, "商品 ID");
        NormalizedProductDetailUpdate normalized = normalizeDetailUpdateRequest(spuId, request);
        ImageFinalizeResult imageFinalizeResult = finalizeRequestedImages(spuId, normalized);
        registerTransferredImageSynchronization(
                imageFinalizeResult.tempObjectKeys(),
                imageFinalizeResult.finalObjectKeys(),
                imageFinalizeResult.sessionKeys());
        Map<String, Object> result = productSpuMapper.updateSpuDetail(
                spuId,
                normalized.categoryId(),
                normalized.name(),
                normalized.subtitle(),
                normalized.brandName(),
                imageFinalizeResult.mainImageUrl(),
                normalized.status(),
                snowflakeIdWorker.nextId(),
                toJsonString(imageFinalizeResult.imageUrls()),
                toJsonString(imageFinalizeResult.detailImageUrls()),
                toJsonString(normalized.attributes()),
                normalized.description(),
                normalized.afterSale(),
                imageFinalizeResult.skusJson());
        validateDetailUpdateResult(result);
        List<String> createdSkuIds = assembler.parseStringList(value(result, "createdSkuIdsJson"));
        List<String> deletedSkuIds = parseSkuIdHexList(value(result, "deletedSkuIdsJson"));
        skuBloomService.removeSkuIdsAfterCommit(deletedSkuIds);
        skuBloomService.addSkuIdsAfterCommit(createdSkuIds);
        Set<String> oldKeys = new LinkedHashSet<>(cleanupObjectKeys(value(result, "oldImageUrlsJson")));
        Set<String> newKeys = new LinkedHashSet<>(cleanupObjectKeys(collectFinalImageUrls(
                imageFinalizeResult.mainImageUrl(),
                imageFinalizeResult.imageUrls(),
                imageFinalizeResult.detailImageUrls(),
                imageFinalizeResult.skus())));
        oldKeys.removeAll(newKeys);
        queueCleanupKeysAfterCommit(oldKeys);
        detailCacheService.invalidateAfterCommit(List.of(spuId));
        publicDetailCacheService.invalidateAfterCommit(List.of(spuId));
        productIndexService.syncProductsAfterCommit(List.of(spuId));
        AdminProductSpuDetailResponse detail = findSpuDetailResponse(spuId);
        if (detail == null) {
            throw productNotFoundException();
        }
        return detail;
    }

    @Transactional
    public AdminProductSpuResponse create(AdminProductSpuCreateRequest request) {
        Long categoryId = normalizeRequiredId(request == null ? null : request.categoryId(), "分类 ID");
        validateLeafCategoryForProduct(categoryId);
        String name = normalizeRequiredText(request == null ? null : request.name(), "商品名称", MAX_NAME_LENGTH);
        String subtitle = normalizeNullableText(request == null ? null : request.subtitle(), "商品副标题", MAX_SUBTITLE_LENGTH);
        String brandName = normalizeNullableText(request == null ? null : request.brandName(), "品牌名称", MAX_BRAND_NAME_LENGTH);
        String status = normalizeStatus(request == null ? null : request.status(), STATUS_ACTIVE);
        String uploadSessionId = normalizeRequiredText(request == null ? null : request.uploadSessionId(), "图片上传会话", NANO_ID_LENGTH);
        String tempUrl = normalizeRequiredText(request == null ? null : request.mainImageTempUrl(), "商品主图", MAX_IMAGE_URL_LENGTH);
        ImageSession session = loadImageSession(uploadSessionId, true);
        if (!tempUrl.equals(session.tempUrl())) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_IMAGE_SESSION_MISMATCH",
                    "商品主图与预上传会话不匹配，请重新上传。",
                    HttpStatus.BAD_REQUEST);
        }

        Long spuId = snowflakeIdWorker.nextId();
        FinalImage finalImage = copyTempImageToProduct(session.objectKey(), spuId);
        registerImageCleanupSynchronization(session.objectKey(), finalImage.objectKey(), imageSessionKey(uploadSessionId));
        int insertedRows = productSpuMapper.insertSpu(
                spuId,
                categoryId,
                name,
                subtitle,
                brandName,
                finalImage.url(),
                status);
        if (insertedRows == 0) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_SPU_CREATE_FAILED",
                    "商品创建失败，请刷新后重试。",
                    HttpStatus.CONFLICT);
        }
        detailCacheService.syncCreatedProductAfterCommit(spuId);
        publicDetailCacheService.invalidateAfterCommit(List.of(spuId));
        productIndexService.syncProductsAfterCommit(List.of(spuId));
        return findSpuResponse(spuId);
    }

    @Transactional
    public AdminProductSpuResponse changeStatus(Long id, AdminProductSpuStatusRequest request) {
        Long spuId = normalizeRequiredId(id, "商品 ID");
        String status = normalizeStatus(request == null ? null : request.status(), "");
        Map<String, Object> existing = productSpuMapper.findSpuById(spuId);
        if (existing == null || existing.isEmpty()) {
            throw productNotFoundException();
        }
        if (STATUS_ACTIVE.equals(status)) {
            Long categoryId = toLong(value(existing, "categoryId"), 0L);
            validateLeafCategoryForProduct(categoryId);
        }
        int updatedRows = productSpuMapper.updateStatus(spuId, status);
        if (updatedRows == 0) {
            throw productNotFoundException();
        }
        detailCacheService.invalidateAfterCommit(List.of(spuId));
        publicDetailCacheService.invalidateAfterCommit(List.of(spuId));
        productIndexService.syncProductsAfterCommit(List.of(spuId));
        return findSpuResponse(spuId);
    }

    @Transactional
    public AdminProductSpuBatchDisableResponse batchDisable(AdminProductSpuBatchIdsRequest request) {
        List<Long> spuIds = normalizeBatchIds(request == null ? null : request.ids());
        Map<String, Object> result = productSpuMapper.batchUpdateStatusByIds(spuIds, STATUS_DISABLED);
        int requestedCount = toInt(value(result, "requestedCount"), spuIds.size());
        int matchedCount = toInt(value(result, "matchedCount"), 0);
        if (matchedCount != requestedCount) {
            throw productNotFoundException();
        }
        List<Long> targetIds = assembler.parseLongList(value(result, "targetIdsJson"));
        detailCacheService.invalidateAfterCommit(targetIds);
        publicDetailCacheService.invalidateAfterCommit(targetIds);
        productIndexService.syncProductsAfterCommit(targetIds);
        return new AdminProductSpuBatchDisableResponse(
                requestedCount,
                matchedCount,
                toInt(value(result, "affectedCount"), 0));
    }

    @Transactional
    public AdminProductSpuBatchDisableResponse batchDisableByLeafCategory(Long id) {
        Long categoryId = normalizeRequiredId(id, "分类 ID");
        Map<String, Object> result = productSpuMapper.batchUpdateStatusByLeafCategory(categoryId, STATUS_DISABLED);
        validateLeafCategoryBatchResult(result);
        int matchedCount = toInt(value(result, "matchedCount"), 0);
        List<Long> targetIds = assembler.parseLongList(value(result, "targetIdsJson"));
        detailCacheService.invalidateAfterCommit(targetIds);
        publicDetailCacheService.invalidateAfterCommit(targetIds);
        productIndexService.syncProductsAfterCommit(targetIds);
        return new AdminProductSpuBatchDisableResponse(
                toInt(value(result, "requestedCount"), matchedCount),
                matchedCount,
                toInt(value(result, "affectedCount"), 0));
    }

    @Transactional
    public AdminProductSpuBatchDeleteResponse batchDelete(AdminProductSpuBatchIdsRequest request) {
        List<Long> spuIds = normalizeBatchIds(request == null ? null : request.ids());
        Map<String, Object> result = productSpuMapper.batchDeleteByIds(spuIds);
        int requestedCount = toInt(value(result, "requestedCount"), spuIds.size());
        int matchedCount = toInt(value(result, "matchedCount"), 0);
        if (matchedCount != requestedCount) {
            throw productNotFoundException();
        }
        List<String> cleanupKeys = cleanupObjectKeys(value(result, "imageUrlsJson"));
        queueCleanupKeysAfterCommit(cleanupKeys);
        List<Long> deletedIds = assembler.parseLongList(value(result, "deletedIdsJson"));
        List<String> deletedSkuIds = parseSkuIdHexList(value(result, "deletedSkuIdsJson"));
        detailCacheService.invalidateAfterCommit(deletedIds);
        publicDetailCacheService.invalidateAfterCommit(deletedIds);
        detailCacheService.deleteProductBloomIdsAfterCommit(deletedIds);
        skuBloomService.removeSkuIdsAfterCommit(deletedSkuIds);
        productIndexService.deleteProductsAfterCommit(deletedIds);
        return new AdminProductSpuBatchDeleteResponse(
                requestedCount,
                matchedCount,
                toInt(value(result, "deletedSpuCount"), 0),
                toInt(value(result, "deletedSkuCount"), 0),
                toInt(value(result, "deletedDetailCount"), 0),
                cleanupKeys.size());
    }

    @Transactional
    public AdminProductSpuBatchDeleteResponse batchDeleteByLeafCategory(Long id) {
        Long categoryId = normalizeRequiredId(id, "分类 ID");
        Map<String, Object> result = productSpuMapper.batchDeleteByLeafCategory(categoryId);
        validateLeafCategoryBatchResult(result);
        int matchedCount = toInt(value(result, "matchedCount"), 0);
        List<String> cleanupKeys = cleanupObjectKeys(value(result, "imageUrlsJson"));
        queueCleanupKeysAfterCommit(cleanupKeys);
        List<Long> deletedIds = assembler.parseLongList(value(result, "deletedIdsJson"));
        List<String> deletedSkuIds = parseSkuIdHexList(value(result, "deletedSkuIdsJson"));
        detailCacheService.invalidateAfterCommit(deletedIds);
        publicDetailCacheService.invalidateAfterCommit(deletedIds);
        detailCacheService.deleteProductBloomIdsAfterCommit(deletedIds);
        skuBloomService.removeSkuIdsAfterCommit(deletedSkuIds);
        productIndexService.deleteProductsAfterCommit(deletedIds);
        return new AdminProductSpuBatchDeleteResponse(
                toInt(value(result, "requestedCount"), matchedCount),
                matchedCount,
                toInt(value(result, "deletedSpuCount"), 0),
                toInt(value(result, "deletedSkuCount"), 0),
                toInt(value(result, "deletedDetailCount"), 0),
                cleanupKeys.size());
    }

    @Scheduled(fixedDelayString = "${shopping.admin.product-image-cleanup-delay-ms:600000}",
            initialDelayString = "${shopping.admin.product-image-cleanup-initial-delay-ms:120000}")
    public void cleanupPendingImages() {
        int batchCount = 0;
        int totalRequested = 0;
        int totalFailed = 0;
        while (batchCount < CLEANUP_MAX_BATCHES_PER_RUN) {
            List<String> objectKeys;
            try {
                objectKeys = stringRedisTemplate.opsForSet().pop(IMAGE_CLEANUP_SET_KEY, CLEANUP_BATCH_SIZE);
            } catch (Exception e) {
                log.warn("[商品管理] 读取商品图片补偿清理队列失败", e);
                break;
            }
            if (objectKeys == null || objectKeys.isEmpty()) {
                break;
            }
            batchCount++;
            totalRequested += objectKeys.size();
            List<String> failedKeys = deleteObjectKeys(objectKeys);
            if (!failedKeys.isEmpty()) {
                totalFailed += failedKeys.size();
                addCleanupKeys(failedKeys);
                break;
            }
            if (objectKeys.size() < CLEANUP_BATCH_SIZE) {
                break;
            }
        }
        if (batchCount > 0) {
            log.info("[Product admin] image cleanup finished, batches={}, requested={}, failed={}, batchSize={}, maxBatches={}",
                    batchCount, totalRequested, totalFailed, CLEANUP_BATCH_SIZE, CLEANUP_MAX_BATCHES_PER_RUN);
        }
    }

    private AdminProductSpuDetailResponse findSpuDetailResponse(Long spuId) {
        Map<String, Object> row = productSpuMapper.findSpuDetailById(spuId);
        if (row == null || row.isEmpty()) {
            return null;
        }
        return assembler.toSpuDetailResponse(row);
    }

    private AdminProductSpuDetailSkuResponse findSkuDetailResponse(Long spuId, byte[] skuId) {
        Map<String, Object> row = productSpuMapper.findSkuBySpuIdAndSkuId(spuId, skuId);
        AdminProductSpuDetailSkuResponse response = assembler.toSkuResponse(row);
        if (response == null) {
            throw skuNotFoundException();
        }
        return response;
    }

    private void ensureProductExists(Long spuId) {
        Map<String, Object> existing = productSpuMapper.findSpuById(spuId);
        if (existing == null || existing.isEmpty()) {
            throw productNotFoundException();
        }
    }

    private void ensureSkuExists(Long spuId, byte[] skuId) {
        Map<String, Object> existing = productSpuMapper.findSkuBySpuIdAndSkuId(spuId, skuId);
        if (existing == null || existing.isEmpty()) {
            throw skuNotFoundException();
        }
    }

    private void invalidateSkuProductAfterCommit(Long spuId) {
        detailCacheService.invalidateAfterCommit(List.of(spuId));
        publicDetailCacheService.invalidateAfterCommit(List.of(spuId));
        productIndexService.syncProductsAfterCommit(List.of(spuId));
    }

    private void validateSkuCreateResult(Map<String, Object> result) {
        if (!toBoolean(value(result, "spuExists"))) {
            throw productNotFoundException();
        }
        if (toInt(value(result, "insertedCount"), 0) <= 0) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_SKU_CREATE_FAILED",
                    "SKU 创建失败，请刷新后重试。",
                    HttpStatus.CONFLICT);
        }
    }

    private void validateSkuUpdateResult(Map<String, Object> result) {
        if (!toBoolean(value(result, "spuExists"))) {
            throw productNotFoundException();
        }
        if (!toBoolean(value(result, "skuExists"))) {
            throw skuNotFoundException();
        }
        if (toInt(value(result, "updatedCount"), 0) <= 0) {
            throw skuNotFoundException();
        }
    }

    private void validateSkuDeleteResult(Map<String, Object> result) {
        if (!toBoolean(value(result, "spuExists"))) {
            throw productNotFoundException();
        }
        if (!toBoolean(value(result, "skuExists"))) {
            throw skuNotFoundException();
        }
        if (toInt(value(result, "deletedCount"), 0) <= 0) {
            throw skuNotFoundException();
        }
    }

    private void validateSkuBatchResult(Map<String, Object> result) {
        if (!toBoolean(value(result, "spuExists"))) {
            throw productNotFoundException();
        }
        int requestedCount = toInt(value(result, "requestedCount"), 0);
        int matchedCount = toInt(value(result, "matchedCount"), 0);
        if (matchedCount != requestedCount) {
            throw skuNotFoundException();
        }
    }

    private NormalizedProductDetailUpdate normalizeDetailUpdateRequest(Long spuId, AdminProductSpuDetailUpdateRequest request) {
        if (request == null) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_DETAIL_REQUIRED",
                    "商品详情不能为空。",
                    HttpStatus.BAD_REQUEST);
        }
        Long categoryId = normalizeRequiredId(request.categoryId(), "分类 ID");
        String name = normalizeRequiredText(request.name(), "商品名称", MAX_NAME_LENGTH);
        String subtitle = normalizeNullableText(request.subtitle(), "商品副标题", MAX_SUBTITLE_LENGTH);
        String brandName = normalizeNullableText(request.brandName(), "品牌名称", MAX_BRAND_NAME_LENGTH);
        String mainImageUrl = normalizeNullableImageUrl(request.mainImageUrl(), "商品主图");
        String status = normalizeStatus(request.status(), "");
        JsonNode imageUrls = normalizeImageUrlArray(request.imageUrls(), "商品展示图片", true);
        JsonNode detailImageUrls = normalizeImageUrlArray(request.detailImageUrls(), "商品详情图片", false);
        JsonNode attributes = normalizeJsonNode(request.attributes(), false, "商品参数");
        String description = normalizeText(request.description());
        String afterSale = normalizeText(request.afterSale());
        List<NormalizedSkuUpdate> skus = skuService.normalizeSkuUpdates(spuId, request.skus());
        List<AdminProductImageUsageRequest> imageUploadSessions = normalizeImageUsageRequests(request.imageUploadSessions());
        return new NormalizedProductDetailUpdate(
                categoryId,
                name,
                subtitle,
                brandName,
                mainImageUrl,
                status,
                imageUrls,
                detailImageUrls,
                attributes,
                description.isEmpty() ? null : description,
                afterSale.isEmpty() ? null : afterSale,
                skus,
                imageUploadSessions);
    }

    private List<AdminProductImageUsageRequest> normalizeImageUsageRequests(List<AdminProductImageUsageRequest> rawUsages) {
        if (rawUsages == null || rawUsages.isEmpty()) {
            return List.of();
        }
        Map<String, AdminProductImageUsageRequest> usages = new LinkedHashMap<>();
        for (AdminProductImageUsageRequest rawUsage : rawUsages) {
            String uploadSessionId = normalizeText(rawUsage == null ? null : rawUsage.uploadSessionId());
            if (uploadSessionId.isEmpty()) {
                continue;
            }
            String tempUrl = normalizeText(rawUsage.tempUrl());
            usages.put(uploadSessionId, new AdminProductImageUsageRequest(uploadSessionId, tempUrl));
        }
        return List.copyOf(usages.values());
    }

    private ImageFinalizeResult finalizeRequestedImages(Long spuId, NormalizedProductDetailUpdate normalized) {
        List<String> productImageUrls = collectProductImageUrls(
                normalized.mainImageUrl(),
                normalized.imageUrls(),
                normalized.detailImageUrls());
        Set<String> requestedTempUrls = new LinkedHashSet<>();
        for (String url : productImageUrls) {
            if (!extractTempProductObjectKey(url).isBlank()) {
                requestedTempUrls.add(url);
            }
        }
        Map<String, String> skuIdsByTempUrl = new LinkedHashMap<>();
        for (NormalizedSkuUpdate sku : normalized.skus()) {
            List<String> skuImageUrls = collectJsonImageUrls(sku.skuImageUrls());
            for (String url : skuImageUrls) {
                if (!extractTempProductObjectKey(url).isBlank()) {
                    requestedTempUrls.add(url);
                    skuIdsByTempUrl.putIfAbsent(url, sku.finalId());
                }
            }
        }
        Map<String, ImageSession> sessionsById = loadImageSessionsById(normalized.imageUploadSessions());
        Map<String, ImageSession> sessionsByTempUrl = new LinkedHashMap<>();
        sessionsById.forEach((uploadSessionId, session) -> sessionsByTempUrl.put(session.tempUrl(), session));
        for (String tempUrl : requestedTempUrls) {
            if (!sessionsByTempUrl.containsKey(tempUrl)) {
                throw new AdminServiceException(
                        "ADMIN_PRODUCT_IMAGE_SESSION_REQUIRED",
                        "新上传图片缺少有效的预上传会话。",
                        HttpStatus.BAD_REQUEST);
            }
        }
        Map<String, FinalImage> finalImagesByTempUrl = new LinkedHashMap<>();
        List<String> finalObjectKeys = new ArrayList<>();
        try {
            for (Map.Entry<String, String> entry : skuIdsByTempUrl.entrySet()) {
                ImageSession session = sessionsByTempUrl.get(entry.getKey());
                FinalImage finalImage = copyTempImageToSkuProduct(session.objectKey(), spuId, entry.getValue());
                finalImagesByTempUrl.put(entry.getKey(), finalImage);
                finalObjectKeys.add(finalImage.objectKey());
            }
            for (String tempUrl : requestedTempUrls) {
                if (finalImagesByTempUrl.containsKey(tempUrl)) {
                    continue;
                }
                ImageSession session = sessionsByTempUrl.get(tempUrl);
                FinalImage finalImage = copyTempImageToProduct(session.objectKey(), spuId);
                finalImagesByTempUrl.put(tempUrl, finalImage);
                finalObjectKeys.add(finalImage.objectKey());
            }
        } catch (RuntimeException e) {
            deleteObjectKeysWithCompensation(finalObjectKeys);
            throw e;
        }
        List<String> tempObjectKeys = sessionsById.values().stream()
                .map(ImageSession::objectKey)
                .filter(key -> !key.isBlank())
                .distinct()
                .toList();
        List<String> sessionKeys = sessionsById.keySet().stream()
                .map(this::imageSessionKey)
                .toList();
        String mainImageUrl = replaceTempUrl(normalized.mainImageUrl(), finalImagesByTempUrl);
        JsonNode imageUrls = replaceTempUrls(normalized.imageUrls(), finalImagesByTempUrl);
        JsonNode detailImageUrls = replaceTempUrls(normalized.detailImageUrls(), finalImagesByTempUrl);
        List<NormalizedSkuUpdate> skus = normalized.skus().stream()
                .map(sku -> sku.withSkuImageUrls(replaceTempUrls(sku.skuImageUrls(), finalImagesByTempUrl)))
                .toList();
        return new ImageFinalizeResult(
                mainImageUrl,
                imageUrls,
                detailImageUrls,
                skus,
                skuService.toSkuJson(skus),
                tempObjectKeys,
                finalObjectKeys,
                sessionKeys);
    }

    private SkuImageFinalizeResult finalizeRequestedSkuImages(Long spuId,
                                                              String skuId,
                                                              JsonNode skuImageUrls,
                                                              List<AdminProductImageUsageRequest> imageUploadSessions) {
        Set<String> requestedTempUrls = new LinkedHashSet<>();
        for (String url : collectJsonImageUrls(skuImageUrls)) {
            if (!extractTempProductObjectKey(url).isBlank()) {
                requestedTempUrls.add(url);
            }
        }
        if (requestedTempUrls.isEmpty()) {
            return new SkuImageFinalizeResult(
                    jsonNodeOrDefault(skuImageUrls, true),
                    List.of(),
                    List.of(),
                    List.of());
        }
        Map<String, ImageSession> sessionsById = loadImageSessionsById(imageUploadSessions);
        Map<String, String> uploadSessionIdsByTempUrl = new LinkedHashMap<>();
        Map<String, ImageSession> sessionsByTempUrl = new LinkedHashMap<>();
        sessionsById.forEach((uploadSessionId, session) -> {
            uploadSessionIdsByTempUrl.put(session.tempUrl(), uploadSessionId);
            sessionsByTempUrl.put(session.tempUrl(), session);
        });
        for (String tempUrl : requestedTempUrls) {
            if (!sessionsByTempUrl.containsKey(tempUrl)) {
                throw new AdminServiceException(
                        "ADMIN_PRODUCT_IMAGE_SESSION_REQUIRED",
                        "新上传图片缺少有效的预上传会话。",
                        HttpStatus.BAD_REQUEST);
            }
        }
        Map<String, FinalImage> finalImagesByTempUrl = new LinkedHashMap<>();
        List<String> finalObjectKeys = new ArrayList<>();
        try {
            for (String tempUrl : requestedTempUrls) {
                ImageSession session = sessionsByTempUrl.get(tempUrl);
                FinalImage finalImage = copyTempImageToSkuProduct(session.objectKey(), spuId, skuId);
                finalImagesByTempUrl.put(tempUrl, finalImage);
                finalObjectKeys.add(finalImage.objectKey());
            }
        } catch (RuntimeException e) {
            deleteObjectKeysWithCompensation(finalObjectKeys);
            throw e;
        }
        List<String> tempObjectKeys = requestedTempUrls.stream()
                .map(sessionsByTempUrl::get)
                .map(ImageSession::objectKey)
                .filter(key -> !key.isBlank())
                .distinct()
                .toList();
        List<String> sessionKeys = requestedTempUrls.stream()
                .map(uploadSessionIdsByTempUrl::get)
                .filter(id -> id != null && !id.isBlank())
                .map(this::imageSessionKey)
                .toList();
        return new SkuImageFinalizeResult(
                replaceTempUrls(skuImageUrls, finalImagesByTempUrl),
                tempObjectKeys,
                finalObjectKeys,
                sessionKeys);
    }

    private Map<String, ImageSession> loadImageSessionsById(List<AdminProductImageUsageRequest> usages) {
        if (usages == null || usages.isEmpty()) {
            return Map.of();
        }
        List<String> uploadSessionIds = usages.stream()
                .map(AdminProductImageUsageRequest::uploadSessionId)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
        if (uploadSessionIds.isEmpty()) {
            return Map.of();
        }
        List<Object> rawResults;
        try {
            rawResults = stringRedisTemplate.executePipelined(new SessionCallback<Object>() {
                @Override
                @SuppressWarnings({"rawtypes", "unchecked"})
                public Object execute(RedisOperations operations) {
                    for (String uploadSessionId : uploadSessionIds) {
                        operations.opsForHash().entries(imageSessionKey(uploadSessionId));
                    }
                    return null;
                }
            });
        } catch (Exception e) {
            log.warn("[商品管理] 批量读取商品图片预上传会话失败，count={}", uploadSessionIds.size(), e);
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_IMAGE_SESSION_LOAD_FAILED",
                    "读取图片预上传会话失败，请重试。",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
        Map<String, String> requestedTempUrls = new LinkedHashMap<>();
        for (AdminProductImageUsageRequest usage : usages) {
            requestedTempUrls.put(usage.uploadSessionId(), normalizeText(usage.tempUrl()));
        }
        Map<String, ImageSession> sessions = new LinkedHashMap<>();
        for (int index = 0; index < uploadSessionIds.size(); index += 1) {
            String uploadSessionId = uploadSessionIds.get(index);
            Object raw = rawResults == null || index >= rawResults.size() ? null : rawResults.get(index);
            if (!(raw instanceof Map<?, ?> rawMap) || rawMap.isEmpty()) {
                throw new AdminServiceException(
                        "ADMIN_PRODUCT_IMAGE_SESSION_EXPIRED",
                        "图片预上传信息已过期，请重新上传。",
                        HttpStatus.BAD_REQUEST);
            }
            ImageSession session = toImageSession(uploadSessionId, rawMap);
            String requestedTempUrl = requestedTempUrls.getOrDefault(uploadSessionId, "");
            if (!requestedTempUrl.isEmpty() && !requestedTempUrl.equals(session.tempUrl())) {
                throw new AdminServiceException(
                        "ADMIN_PRODUCT_IMAGE_SESSION_MISMATCH",
                        "图片与当前上传会话不匹配。",
                        HttpStatus.BAD_REQUEST);
            }
            sessions.put(uploadSessionId, session);
        }
        return sessions;
    }

    private ImageSession toImageSession(String uploadSessionId, Map<?, ?> raw) {
        String tempUrl = normalizeText(raw.get("tempUrl"));
        String objectKey = normalizeText(raw.get("objectKey"));
        if (tempUrl.isEmpty() || !isValidTempProductObjectKey(objectKey)) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_IMAGE_SESSION_INVALID",
                    "商品图片预上传信息无效，请重新上传。",
                    HttpStatus.BAD_REQUEST);
        }
        return new ImageSession(tempUrl, objectKey);
    }

    private void registerTransferredImageSynchronization(Collection<String> tempObjectKeys,
                                                        Collection<String> finalObjectKeys,
                                                        Collection<String> sessionKeys) {
        List<String> tempKeys = normalizeStringCollection(tempObjectKeys);
        List<String> finalKeys = normalizeStringCollection(finalObjectKeys);
        List<String> redisSessionKeys = normalizeStringCollection(sessionKeys);
        if (tempKeys.isEmpty() && finalKeys.isEmpty() && redisSessionKeys.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteObjectKeysWithCompensation(tempKeys);
            if (!redisSessionKeys.isEmpty()) {
                stringRedisTemplate.delete(redisSessionKeys);
            }
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteObjectKeysWithCompensation(tempKeys);
                if (!redisSessionKeys.isEmpty()) {
                    try {
                        stringRedisTemplate.delete(redisSessionKeys);
                    } catch (Exception e) {
                        log.warn("[商品管理] 批量删除商品图片预上传会话失败，count={}", redisSessionKeys.size(), e);
                    }
                }
            }

            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    deleteObjectKeysWithCompensation(finalKeys);
                }
            }
        });
    }

    private void validateDetailUpdateResult(Map<String, Object> result) {
        if (!toBoolean(value(result, "spuExists"))) {
            throw productNotFoundException();
        }
        if (!toBoolean(value(result, "categoryExists"))) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_CATEGORY_NOT_FOUND",
                    "分类不存在。",
                    HttpStatus.NOT_FOUND);
        }
        if (toInt(value(result, "childCount"), 0) > 0) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_CATEGORY_NOT_LEAF",
                    "商品只能挂在叶子分类下。",
                    HttpStatus.BAD_REQUEST);
        }
        if (!STATUS_ACTIVE.equals(toText(value(result, "categoryStatus")))) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_CATEGORY_DISABLED",
                    "禁用分类不能保存商品。",
                    HttpStatus.BAD_REQUEST);
        }
        if (toInt(value(result, "invalidSkuCount"), 0) > 0) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_SKU_NOT_FOUND",
                    "请求中包含不属于该商品的 SKU。",
                    HttpStatus.BAD_REQUEST);
        }
        if (toInt(value(result, "updatedSpuCount"), 0) == 0) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_DETAIL_UPDATE_FAILED",
                    "商品详情保存失败，请刷新后重试。",
                    HttpStatus.CONFLICT);
        }
    }

    private void validateLeafCategoryForProduct(Long categoryId) {
        Map<String, Object> category = productCategoryMapper.findCategoryTreeRowById(categoryId);
        if (category == null || category.isEmpty()) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_CATEGORY_NOT_FOUND",
                    "分类不存在。",
                    HttpStatus.NOT_FOUND);
        }
        if (!STATUS_ACTIVE.equals(toText(value(category, "status")))) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_CATEGORY_DISABLED",
                    "禁用分类不能创建或启用商品。",
                    HttpStatus.BAD_REQUEST);
        }
        int childCount = toInt(value(category, "childCount"), 0);
        if (childCount > 0) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_CATEGORY_NOT_LEAF",
                    "商品只能创建在没有子分类的叶子分类下。",
                    HttpStatus.BAD_REQUEST);
        }
    }

    private List<Long> normalizeBatchIds(List<Long> rawIds) {
        if (rawIds == null || rawIds.isEmpty()) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_BATCH_EMPTY",
                    "请选择需要处理的商品。",
                    HttpStatus.BAD_REQUEST);
        }
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (Long rawId : rawIds) {
            ids.add(normalizeRequiredId(rawId, "商品 ID"));
        }
        if (ids.isEmpty()) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_BATCH_EMPTY",
                    "请选择需要处理的商品。",
                    HttpStatus.BAD_REQUEST);
        }
        return new ArrayList<>(ids);
    }

    private List<String> normalizeBatchSkuIds(List<String> rawIds) {
        if (rawIds == null || rawIds.isEmpty()) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_SKU_BATCH_EMPTY",
                    "请选择需要处理的 SKU。",
                    HttpStatus.BAD_REQUEST);
        }
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (String rawId : rawIds) {
            ids.add(normalizeSkuId(rawId));
        }
        if (ids.isEmpty()) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_SKU_BATCH_EMPTY",
                    "请选择需要处理的 SKU。",
                    HttpStatus.BAD_REQUEST);
        }
        return new ArrayList<>(ids);
    }

    private List<byte[]> skuIdBytes(List<String> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return List.of();
        }
        return skuIds.stream()
                .map(this::skuIdBytes)
                .toList();
    }

    private byte[] skuIdBytes(String skuId) {
        try {
            return ProductSkuIdCodec.fromBase62(skuId);
        } catch (IllegalArgumentException e) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_SKU_ID_INVALID",
                    "SKU ID 无效。",
                    HttpStatus.BAD_REQUEST);
        }
    }

    private List<String> parseSkuIdHexList(Object raw) {
        return assembler.parseStringList(raw).stream()
                .map(ProductSkuIdCodec::hexToBase62)
                .toList();
    }

    private void validateLeafCategoryBatchResult(Map<String, Object> result) {
        if (!toBoolean(value(result, "categoryExists"))) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_CATEGORY_NOT_FOUND",
                    "分类不存在。",
                    HttpStatus.NOT_FOUND);
        }
        int childCount = toInt(value(result, "childCount"), 0);
        if (childCount > 0) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_CATEGORY_NOT_LEAF",
                    "只能处理叶子分类下的商品。",
                    HttpStatus.BAD_REQUEST);
        }
    }

    private JsonNode normalizeImageUrlArray(JsonNode node, String label, boolean dedupe) {
        JsonNode value = normalizeJsonNode(node, true, label);
        ArrayNode normalized = objectMapper.createArrayNode();
        Set<String> seen = dedupe ? new LinkedHashSet<>() : null;
        value.elements().forEachRemaining(item -> {
            String url = extractImageUrl(item);
            if (url.isEmpty()) {
                return;
            }
            url = normalizeImageUrl(url, label);
            if (seen != null && !seen.add(url)) {
                return;
            }
            normalized.add(url);
        });
        return normalized;
    }

    private String extractImageUrl(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        if (node.isTextual()) {
            return normalizeText(node.asText());
        }
        if (node.isObject()) {
            JsonNode urlNode = node.get("url");
            return urlNode != null && urlNode.isTextual() ? normalizeText(urlNode.asText()) : "";
        }
        return "";
    }

    private String normalizeImageUrl(String raw, String label) {
        String value = normalizeText(raw);
        if (value.isEmpty()) {
            return "";
        }
        if (value.length() > MAX_IMAGE_URL_LENGTH) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_TEXT_TOO_LONG",
                    label + "不能超过 " + MAX_IMAGE_URL_LENGTH + " 个字符。",
                    HttpStatus.BAD_REQUEST);
        }
        return productImageUrlValidator.validateImageUrl(value, label);
    }

    private JsonNode normalizeJsonNode(JsonNode node, boolean array, String label) {
        JsonNode value = jsonNodeOrDefault(node, array);
        if (array && !value.isArray()) {
            throw new AdminServiceException("ADMIN_PRODUCT_JSON_INVALID", label + "必须是 JSON 数组。", HttpStatus.BAD_REQUEST);
        }
        if (!array && !value.isObject()) {
            throw new AdminServiceException("ADMIN_PRODUCT_JSON_INVALID", label + "必须是 JSON 对象。", HttpStatus.BAD_REQUEST);
        }
        return value;
    }

    private JsonNode jsonNodeOrDefault(JsonNode node, boolean array) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return array ? objectMapper.createArrayNode() : objectMapper.createObjectNode();
        }
        return node.deepCopy();
    }

    private String toJsonString(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_JSON_SERIALIZE_FAILED",
                    "商品详情 JSON 序列化失败。",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private List<String> collectFinalImageUrls(String mainImageUrl,
                                               JsonNode imageUrls,
                                               JsonNode detailImageUrls,
                                               List<NormalizedSkuUpdate> skus) {
        LinkedHashSet<String> urls = new LinkedHashSet<>(collectProductImageUrls(mainImageUrl, imageUrls, detailImageUrls));
        if (skus != null) {
            for (NormalizedSkuUpdate sku : skus) {
                urls.addAll(collectJsonImageUrls(sku.skuImageUrls()));
            }
        }
        return List.copyOf(urls);
    }

    private List<String> collectProductImageUrls(String mainImageUrl,
                                                 JsonNode imageUrls,
                                                 JsonNode detailImageUrls) {
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        String main = normalizeText(mainImageUrl);
        if (!main.isEmpty()) {
            urls.add(main);
        }
        collectImageUrls(imageUrls, urls);
        collectImageUrls(detailImageUrls, urls);
        return List.copyOf(urls);
    }

    private List<String> collectJsonImageUrls(JsonNode imageUrls) {
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        collectImageUrls(imageUrls, urls);
        return List.copyOf(urls);
    }

    private void collectImageUrls(JsonNode node, Collection<String> urls) {
        if (node == null || urls == null || node.isNull()) {
            return;
        }
        if (node.isTextual()) {
            String value = normalizeText(node.asText());
            if (!value.isEmpty()) {
                urls.add(value);
            }
            return;
        }
        if (node.isObject()) {
            JsonNode urlNode = node.get("url");
            if (urlNode != null && urlNode.isTextual()) {
                String value = normalizeText(urlNode.asText());
                if (!value.isEmpty()) {
                    urls.add(value);
                }
            }
            node.elements().forEachRemaining(child -> collectImageUrls(child, urls));
            return;
        }
        if (node.isArray()) {
            node.elements().forEachRemaining(child -> collectImageUrls(child, urls));
        }
    }

    private String replaceTempUrl(String value, Map<String, FinalImage> finalImagesByTempUrl) {
        String text = normalizeText(value);
        if (text.isEmpty() || finalImagesByTempUrl == null || finalImagesByTempUrl.isEmpty()) {
            return text.isEmpty() ? null : text;
        }
        FinalImage finalImage = finalImagesByTempUrl.get(text);
        return finalImage == null ? text : finalImage.url();
    }

    private JsonNode replaceTempUrls(JsonNode node, Map<String, FinalImage> finalImagesByTempUrl) {
        if (node == null || node.isNull() || finalImagesByTempUrl == null || finalImagesByTempUrl.isEmpty()) {
            return jsonNodeOrDefault(node, true);
        }
        if (node.isTextual()) {
            FinalImage finalImage = finalImagesByTempUrl.get(node.asText());
            return finalImage == null ? node.deepCopy() : TextNode.valueOf(finalImage.url());
        }
        if (node.isObject()) {
            ObjectNode copy = node.deepCopy();
            List<String> fieldNames = new ArrayList<>();
            copy.fieldNames().forEachRemaining(fieldNames::add);
            for (String fieldName : fieldNames) {
                copy.set(fieldName, replaceTempUrls(copy.get(fieldName), finalImagesByTempUrl));
            }
            return copy;
        }
        if (node.isArray()) {
            com.fasterxml.jackson.databind.node.ArrayNode copy = (com.fasterxml.jackson.databind.node.ArrayNode) node.deepCopy();
            for (int index = 0; index < copy.size(); index += 1) {
                copy.set(index, replaceTempUrls(copy.get(index), finalImagesByTempUrl));
            }
            return copy;
        }
        return node.deepCopy();
    }

    private List<String> normalizeStringCollection(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .map(this::normalizeText)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
    }

    private String extractTempProductObjectKey(String imageUrl) {
        String text = normalizeText(imageUrl);
        if (text.isEmpty()) {
            return "";
        }
        String path = text;
        try {
            URI uri = URI.create(text);
            if (uri.getPath() != null && !uri.getPath().isBlank()) {
                path = uri.getPath();
            }
        } catch (Exception ignored) {
            path = text;
        }
        try {
            path = URLDecoder.decode(path, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return "";
        }
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        int productIndex = path.indexOf("product/temp/");
        if (productIndex >= 0) {
            path = path.substring(productIndex);
        }
        return path.startsWith("product/temp/") ? path : "";
    }

    private boolean isValidTempProductObjectKey(String objectKey) {
        String key = normalizeText(objectKey);
        String prefix = "product/temp/";
        if (!key.startsWith(prefix)) {
            return false;
        }
        String relativePath = key.substring(prefix.length());
        if (relativePath.isBlank() || relativePath.startsWith("/") || relativePath.endsWith("/")) {
            return false;
        }
        int firstSlash = relativePath.indexOf('/');
        if (firstSlash >= 0 && relativePath.indexOf('/', firstSlash + 1) >= 0) {
            return false;
        }
        String fileName = relativePath.substring(relativePath.lastIndexOf('/') + 1);
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 && dotIndex < fileName.length() - 1;
    }

    private void runAfterCommit(Runnable runnable) {
        if (runnable == null) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            runnable.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                runnable.run();
            }
        });
    }

    private List<String> cleanupObjectKeys(Object rawImageUrlsJson) {
        List<String> imageUrls = parseImageUrls(rawImageUrlsJson);
        return imageUrls.stream()
                .map(this::extractProductObjectKey)
                .filter(key -> key != null && !key.isBlank())
                .distinct()
                .toList();
    }

    private List<String> parseImageUrls(Object rawImageUrlsJson) {
        if (rawImageUrlsJson instanceof Collection<?> values) {
            return values.stream()
                    .map(this::normalizeText)
                    .filter(value -> !value.isEmpty())
                    .toList();
        }
        String json = normalizeText(rawImageUrlsJson);
        if (json.isEmpty() || "[]".equals(json)) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            List<String> urls = new ArrayList<>();
            collectImageUrls(node, urls);
            return urls;
        } catch (Exception e) {
            log.warn("[商品管理] 商品图片清理地址解析失败，raw={}", json, e);
            return List.of();
        }
    }

    private String extractProductObjectKey(String imageUrl) {
        String text = normalizeText(imageUrl);
        if (text.isEmpty()) {
            return "";
        }
        String path = text;
        try {
            URI uri = URI.create(text);
            if (uri.getPath() != null && !uri.getPath().isBlank()) {
                path = uri.getPath();
            }
        } catch (Exception ignored) {
            path = text;
        }
        try {
            path = URLDecoder.decode(path, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return "";
        }
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        int productIndex = path.indexOf("product/");
        if (productIndex >= 0) {
            path = path.substring(productIndex);
        }
        if (!path.startsWith("product/") || path.startsWith("product/temp/")) {
            return "";
        }
        return path;
    }

    private void queueCleanupKeysAfterCommit(Collection<String> objectKeys) {
        if (objectKeys == null || objectKeys.isEmpty()) {
            return;
        }
        List<String> keys = objectKeys.stream()
                .filter(key -> key != null && !key.isBlank())
                .distinct()
                .toList();
        if (keys.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            addCleanupKeys(keys);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                addCleanupKeys(keys);
            }
        });
    }

    private ImageSession loadImageSession(String uploadSessionId, boolean required) {
        Map<Object, Object> raw = stringRedisTemplate.opsForHash().entries(imageSessionKey(uploadSessionId));
        if (raw == null || raw.isEmpty()) {
            if (!required) {
                return null;
            }
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_IMAGE_SESSION_EXPIRED",
                    "商品主图预上传信息已过期，请重新上传。",
                    HttpStatus.BAD_REQUEST);
        }
        String tempUrl = normalizeText(raw.get("tempUrl"));
        String objectKey = normalizeText(raw.get("objectKey"));
        if (tempUrl.isEmpty() || !isValidTempProductObjectKey(objectKey)) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_IMAGE_SESSION_INVALID",
                    "商品主图预上传信息无效，请重新上传。",
                    HttpStatus.BAD_REQUEST);
        }
        return new ImageSession(tempUrl, objectKey);
    }

    private FinalImage copyTempImageToProduct(String tempObjectKey, Long spuId) {
        String fileName = tempObjectKey.substring(tempObjectKey.lastIndexOf('/') + 1);
        String finalObjectKey = "product/" + spuId + "/" + fileName;
        try {
            String finalUrl = aliyunUtils.copyFile(tempObjectKey, finalObjectKey).get(30, TimeUnit.SECONDS);
            if (finalUrl == null || finalUrl.isBlank()) {
                throw new IllegalStateException("OSS returned blank url");
            }
            if (finalUrl.length() > MAX_IMAGE_URL_LENGTH) {
                deleteObjectKeysWithCompensation(List.of(finalObjectKey));
                throw new AdminServiceException(
                        "ADMIN_PRODUCT_IMAGE_URL_TOO_LONG",
                        "商品主图地址过长，无法保存。",
                        HttpStatus.BAD_REQUEST);
            }
            return new FinalImage(finalUrl, finalObjectKey);
        } catch (AdminServiceException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            deleteObjectKeysWithCompensation(List.of(finalObjectKey));
            throw imageTransferFailedException();
        } catch (Exception e) {
            deleteObjectKeysWithCompensation(List.of(finalObjectKey));
            log.warn("[商品管理] 商品主图转存失败，tempObjectKey={}, finalObjectKey={}", tempObjectKey, finalObjectKey, e);
            throw imageTransferFailedException();
        }
    }

    private FinalImage copyTempImageToSkuProduct(String tempObjectKey, Long spuId, String skuId) {
        String finalObjectKey = "product/spu/" + spuId + "/sku/" + skuId + "/" + nextHybridImageBase62() + resolveObjectKeyExt(tempObjectKey);
        try {
            String finalUrl = aliyunUtils.copyFile(tempObjectKey, finalObjectKey).get(30, TimeUnit.SECONDS);
            if (finalUrl == null || finalUrl.isBlank()) {
                throw new IllegalStateException("OSS returned blank url");
            }
            if (finalUrl.length() > MAX_IMAGE_URL_LENGTH) {
                deleteObjectKeysWithCompensation(List.of(finalObjectKey));
                throw new AdminServiceException(
                        "ADMIN_PRODUCT_IMAGE_URL_TOO_LONG",
                        "商品图片地址过长，无法保存。",
                        HttpStatus.BAD_REQUEST);
            }
            return new FinalImage(finalUrl, finalObjectKey);
        } catch (AdminServiceException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            deleteObjectKeysWithCompensation(List.of(finalObjectKey));
            throw imageTransferFailedException();
        } catch (Exception e) {
            deleteObjectKeysWithCompensation(List.of(finalObjectKey));
            log.warn("[商品管理] SKU 图片转存失败，tempObjectKey={}, finalObjectKey={}", tempObjectKey, finalObjectKey, e);
            throw imageTransferFailedException();
        }
    }

    private String resolveObjectKeyExt(String objectKey) {
        String fileName = normalizeText(objectKey);
        int slashIndex = fileName.lastIndexOf('/');
        if (slashIndex >= 0) {
            fileName = fileName.substring(slashIndex + 1);
        }
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 && dotIndex < fileName.length() - 1 ? fileName.substring(dotIndex) : "";
    }

    private String nextHybridImageBase62() {
        return toBase62(hybridSemaphoreIdWorker.nextId());
    }

    private String toBase62(byte[] bytes) {
        BigInteger value = new BigInteger(1, bytes);
        if (BigInteger.ZERO.equals(value)) {
            return "0";
        }
        BigInteger radix = BigInteger.valueOf(BASE62_ALPHABET.length);
        StringBuilder builder = new StringBuilder();
        while (value.compareTo(BigInteger.ZERO) > 0) {
            BigInteger[] divideAndRemainder = value.divideAndRemainder(radix);
            builder.append(BASE62_ALPHABET[divideAndRemainder[1].intValue()]);
            value = divideAndRemainder[0];
        }
        return builder.reverse().toString();
    }

    private void registerImageCleanupSynchronization(String tempObjectKey, String finalObjectKey, String sessionKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteObjectKeysWithCompensation(List.of(tempObjectKey));
            stringRedisTemplate.delete(sessionKey);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteObjectKeysWithCompensation(List.of(tempObjectKey));
                try {
                    stringRedisTemplate.delete(sessionKey);
                } catch (Exception e) {
                    log.warn("[商品管理] 删除商品图片预上传会话失败，sessionKey={}", sessionKey, e);
                }
            }

            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    deleteObjectKeysWithCompensation(List.of(finalObjectKey));
                }
            }
        });
    }

    private AdminProductSpuResponse findSpuResponse(Long spuId) {
        Map<String, Object> row = productSpuMapper.findSpuById(spuId);
        if (row == null || row.isEmpty()) {
            throw productNotFoundException();
        }
        return assembler.toSpuResponse(row);
    }

    private void cleanupUploadedObjectAfterPreuploadFailure(String objectKey, String tempUrl) {
        if (tempUrl == null || tempUrl.isBlank()) {
            addCleanupKeys(List.of(objectKey));
            return;
        }
        deleteObjectKeysWithCompensation(List.of(objectKey));
    }

    private void deleteObjectKeysWithCompensation(Collection<String> objectKeys) {
        List<String> failedKeys = deleteObjectKeys(objectKeys);
        if (!failedKeys.isEmpty()) {
            addCleanupKeys(failedKeys);
        }
    }

    private List<String> deleteObjectKeys(Collection<String> objectKeys) {
        if (objectKeys == null || objectKeys.isEmpty()) {
            return List.of();
        }
        List<String> keys = new ArrayList<>(new LinkedHashSet<>(objectKeys));
        List<CompletableFuture<String>> futures = keys.stream()
                .map(key -> aliyunUtils.deleteFile(key)
                        .thenApply(ignored -> "")
                        .exceptionally(ex -> {
                            log.warn("[商品管理] 商品图片删除失败，objectKey={}", key, ex);
                            return key;
                        }))
                .toList();
        List<String> failedKeys = new ArrayList<>();
        for (CompletableFuture<String> future : futures) {
            String failedKey = future.join();
            if (failedKey != null && !failedKey.isBlank()) {
                failedKeys.add(failedKey);
            }
        }
        return failedKeys;
    }

    private void addCleanupKeys(Collection<String> objectKeys) {
        if (objectKeys == null || objectKeys.isEmpty()) {
            return;
        }
        String[] keys = objectKeys.stream()
                .filter(key -> key != null && !key.isBlank())
                .distinct()
                .toArray(String[]::new);
        if (keys.length == 0) {
            return;
        }
        try {
            stringRedisTemplate.opsForSet().add(IMAGE_CLEANUP_SET_KEY, keys);
        } catch (Exception e) {
            log.warn("[商品管理] 写入商品图片补偿清理队列失败，count={}", keys.length, e);
        }
    }

    private void validateImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_IMAGE_REQUIRED",
                    "请上传商品主图。",
                    HttpStatus.BAD_REQUEST);
        }
        String contentType = normalizeText(file.getContentType()).toLowerCase(Locale.ROOT);
        if (!contentType.startsWith("image/")) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_IMAGE_TYPE_INVALID",
                    "商品主图只支持图片文件。",
                    HttpStatus.BAD_REQUEST);
        }
        try {
            file.getBytes();
        } catch (IOException e) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_IMAGE_READ_FAILED",
                    "商品主图读取失败，请重新上传。",
                    HttpStatus.BAD_REQUEST);
        }
    }

    private String resolveExt(String contentType) {
        String normalized = normalizeText(contentType).toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "image/jpeg", "image/jpg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            case "image/bmp" -> ".bmp";
            case "image/svg+xml" -> ".svg";
            default -> ".png";
        };
    }

    private Long normalizeRequiredId(Long id, String label) {
        if (id == null || id <= 0) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_ID_INVALID",
                    label + "无效。",
                    HttpStatus.BAD_REQUEST);
        }
        return id;
    }

    private Long normalizeOptionalId(Long id, String label) {
        if (id == null) {
            return null;
        }
        return normalizeRequiredId(id, label);
    }

    private String normalizeSkuId(String id) {
        String value = normalizeText(id);
        if (!value.matches(ProductSkuIdCodec.BASE62_PATTERN)) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_SKU_ID_INVALID",
                    "SKU ID 无效。",
                    HttpStatus.BAD_REQUEST);
        }
        skuIdBytes(value);
        return value;
    }

    private String normalizeRequiredText(String raw, String label, int maxLength) {
        String value = normalizeText(raw);
        if (value.isEmpty()) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_REQUIRED",
                    label + "不能为空。",
                    HttpStatus.BAD_REQUEST);
        }
        if (value.length() > maxLength) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_TEXT_TOO_LONG",
                    label + "不能超过 " + maxLength + " 个字符。",
                    HttpStatus.BAD_REQUEST);
        }
        return value;
    }

    private String normalizeNullableText(String raw, String label, int maxLength) {
        String value = normalizeText(raw);
        if (value.length() > maxLength) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_TEXT_TOO_LONG",
                    label + "不能超过 " + maxLength + " 个字符。",
                    HttpStatus.BAD_REQUEST);
        }
        return value.isEmpty() ? null : value;
    }

    private String normalizeNullableImageUrl(String raw, String label) {
        String value = normalizeNullableText(raw, label, MAX_IMAGE_URL_LENGTH);
        return productImageUrlValidator.validateNullableImageUrl(value, label);
    }

    private String normalizeOptionalText(String raw, int maxLength, String label) {
        String value = normalizeText(raw);
        if (value.length() > maxLength) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_TEXT_TOO_LONG",
                    label + "不能超过 " + maxLength + " 个字符。",
                    HttpStatus.BAD_REQUEST);
        }
        return value.isEmpty() ? null : value;
    }

    private String normalizeStatus(String raw, String defaultStatus) {
        String value = normalizeText(raw);
        if (value.isEmpty()) {
            value = defaultStatus;
        }
        String status = value.toUpperCase(Locale.ROOT);
        if (!SUPPORTED_STATUS.contains(status)) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_STATUS_INVALID",
                    "商品状态只能是 ACTIVE 或 DISABLED。",
                    HttpStatus.BAD_REQUEST);
        }
        return status;
    }

    private String normalizeOptionalStatus(String raw) {
        String value = normalizeText(raw);
        return value.isEmpty() ? null : normalizeStatus(value, "");
    }

    private String imageSessionKey(String uploadSessionId) {
        return IMAGE_SESSION_PREFIX + uploadSessionId;
    }

    private AdminServiceException imageTransferFailedException() {
        return new AdminServiceException(
                "ADMIN_PRODUCT_IMAGE_TRANSFER_FAILED",
                "商品图片转存失败，请重试。",
                HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private AdminServiceException productNotFoundException() {
        return new AdminServiceException(
                "ADMIN_PRODUCT_SPU_NOT_FOUND",
                "商品不存在。",
                HttpStatus.NOT_FOUND);
    }

    private AdminServiceException skuNotFoundException() {
        return new AdminServiceException(
                "ADMIN_PRODUCT_SKU_NOT_FOUND",
                "SKU 不存在。",
                HttpStatus.NOT_FOUND);
    }

    private Object value(Map<String, Object> row, String key) {
        return assembler.value(row, key);
    }

    private String normalizeText(Object raw) {
        return raw == null ? "" : String.valueOf(raw).trim();
    }

    private String toText(Object value) {
        return assembler.toText(value);
    }

    private Long toLong(Object value, Long defaultValue) {
        return assembler.toLong(value, defaultValue);
    }

    private int toInt(Object value, int defaultValue) {
        return assembler.toInt(value, defaultValue);
    }

    private boolean toBoolean(Object value) {
        return assembler.toBoolean(value);
    }

    private record NormalizedProductDetailUpdate(Long categoryId,
                                                 String name,
                                                 String subtitle,
                                                 String brandName,
                                                 String mainImageUrl,
                                                 String status,
                                                 JsonNode imageUrls,
                                                 JsonNode detailImageUrls,
                                                 JsonNode attributes,
                                                 String description,
                                                 String afterSale,
                                                 List<NormalizedSkuUpdate> skus,
                                                 List<AdminProductImageUsageRequest> imageUploadSessions) {
    }

    private record ImageFinalizeResult(String mainImageUrl,
                                       JsonNode imageUrls,
                                       JsonNode detailImageUrls,
                                       List<NormalizedSkuUpdate> skus,
                                       String skusJson,
                                       List<String> tempObjectKeys,
                                       List<String> finalObjectKeys,
                                       List<String> sessionKeys) {
    }

    private record SkuImageFinalizeResult(JsonNode skuImageUrls,
                                          List<String> tempObjectKeys,
                                          List<String> finalObjectKeys,
                                          List<String> sessionKeys) {
    }

    private record ImageSession(String tempUrl, String objectKey) {
    }

    private record FinalImage(String url, String objectKey) {
    }
}
