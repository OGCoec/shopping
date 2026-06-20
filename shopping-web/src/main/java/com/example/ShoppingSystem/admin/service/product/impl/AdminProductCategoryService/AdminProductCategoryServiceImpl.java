package com.example.ShoppingSystem.admin.service.product.impl.AdminProductCategoryService;

import com.example.ShoppingSystem.Utils.SnowflakeIdWorker;
import com.example.ShoppingSystem.admin.dto.AdminProductCategoryBatchDisableRequest;
import com.example.ShoppingSystem.admin.dto.AdminProductCategoryBatchDisableResponse;
import com.example.ShoppingSystem.admin.dto.AdminProductCategoryCreateRequest;
import com.example.ShoppingSystem.admin.dto.AdminProductCategoryStatusRequest;
import com.example.ShoppingSystem.admin.dto.AdminProductCategoryTreeNodeResponse;
import com.example.ShoppingSystem.admin.dto.AdminProductCategoryUpdateRequest;
import com.example.ShoppingSystem.config.datasource.ProductReadReplicaQueryExecutor;
import com.example.ShoppingSystem.mapper.product.ProductCategoryMapper;
import com.example.ShoppingSystem.product.service.ProductCategoryBloomService;
import com.example.ShoppingSystem.product.service.PublicProductDetailCacheService;
import com.example.ShoppingSystem.product.service.PublicProductCategoryRelationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import com.example.ShoppingSystem.admin.service.common.AdminServiceException;

import com.example.ShoppingSystem.admin.service.product.AdminProductCategoryService;
import com.example.ShoppingSystem.admin.service.product.AdminProductCategoryIndexService;
import com.example.ShoppingSystem.admin.service.product.AdminProductCategorySearchService;
import com.example.ShoppingSystem.admin.service.product.AdminProductSpuIndexService;
@Service
public class AdminProductCategoryServiceImpl implements AdminProductCategoryService {

    private static final int MAX_NAME_LENGTH = 64;
    private static final int MAX_CODE_LENGTH = 64;
    private static final int MAX_DESCRIPTION_LENGTH = 255;
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_DISABLED = "DISABLED";
    private static final Set<String> SUPPORTED_STATUS = Set.of(STATUS_ACTIVE, STATUS_DISABLED);

    private final ProductCategoryMapper productCategoryMapper;
    private final SnowflakeIdWorker snowflakeIdWorker;
    private final ObjectMapper objectMapper;
    private final PublicProductCategoryRelationService publicProductCategoryRelationService;
    private final PublicProductDetailCacheService publicProductDetailCacheService;
    private final ProductCategoryBloomService categoryBloomService;
    private final AdminProductSpuIndexService productIndexService;
    private final AdminProductCategoryIndexService categoryIndexService;
    private final AdminProductCategorySearchService categorySearchService;
    private final ProductReadReplicaQueryExecutor productReadReplicaQueryExecutor;

    public AdminProductCategoryServiceImpl(ProductCategoryMapper productCategoryMapper,
                                       SnowflakeIdWorker snowflakeIdWorker,
                                       ObjectMapper objectMapper,
                                       PublicProductCategoryRelationService publicProductCategoryRelationService,
                                       PublicProductDetailCacheService publicProductDetailCacheService,
                                       ProductCategoryBloomService categoryBloomService,
                                       AdminProductSpuIndexService productIndexService,
                                       AdminProductCategoryIndexService categoryIndexService,
                                       AdminProductCategorySearchService categorySearchService,
                                       ProductReadReplicaQueryExecutor productReadReplicaQueryExecutor) {
        this.productCategoryMapper = productCategoryMapper;
        this.snowflakeIdWorker = snowflakeIdWorker;
        this.objectMapper = objectMapper;
        this.publicProductCategoryRelationService = publicProductCategoryRelationService;
        this.publicProductDetailCacheService = publicProductDetailCacheService;
        this.categoryBloomService = categoryBloomService;
        this.productIndexService = productIndexService;
        this.categoryIndexService = categoryIndexService;
        this.categorySearchService = categorySearchService;
        this.productReadReplicaQueryExecutor = productReadReplicaQueryExecutor;
    }

    public List<AdminProductCategoryTreeNodeResponse> tree() {
        return tree(null);
    }

    public List<AdminProductCategoryTreeNodeResponse> tree(String keyword) {
        String normalizedKeyword = normalizeSearchKeyword(keyword);
        if (!normalizedKeyword.isEmpty()) {
            List<Long> matchedIds = categorySearchService.searchMatchedIds(normalizedKeyword);
            if (matchedIds.isEmpty()) {
                return List.of();
            }
            List<Map<String, Object>> rows = productReadReplicaQueryExecutor.query(() ->
                    productCategoryMapper.listCategorySearchDisplayRows(matchedIds));
            if (rows == null || rows.isEmpty()) {
                return List.of();
            }
            List<Long> displayIds = rows.stream()
                    .map(row -> toLong(value(row, "id"), 0L))
                    .filter(id -> id > 0)
                    .distinct()
                    .toList();
            Map<Long, String> highlights = categorySearchService.searchNameHighlights(normalizedKeyword, displayIds);
            return buildTree(rows, highlights);
        }
        return productReadReplicaQueryExecutor.query(() ->
                buildTree(productCategoryMapper.listCategoryTreeRows(), Map.of()));
    }

    private List<AdminProductCategoryTreeNodeResponse> buildTree(List<Map<String, Object>> rows,
                                                                 Map<Long, String> nameHighlights) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        Map<Long, CategoryNode> byId = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Long id = toLong(value(row, "id"), 0L);
            CategoryNode node = toNode(row, nameHighlights == null ? null : nameHighlights.get(id));
            byId.put(node.id(), node);
        }

        List<CategoryNode> roots = new ArrayList<>();
        for (CategoryNode node : byId.values()) {
            if (node.parentId() == 0 || !byId.containsKey(node.parentId())) {
                roots.add(node);
                continue;
            }
            byId.get(node.parentId()).children().add(node);
        }
        return roots.stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public AdminProductCategoryTreeNodeResponse create(AdminProductCategoryCreateRequest request) {
        Long parentId = normalizeParentId(request == null ? null : request.parentId());
        String name = normalizeRequiredText(request == null ? null : request.name(), "分类名称", MAX_NAME_LENGTH);
        String code = normalizeRequiredText(request == null ? null : request.code(), "分类编码", MAX_CODE_LENGTH);
        int sortOrder = normalizeSortOrder(request == null ? null : request.sortOrder());
        String iconUrlsJson = normalizeIconUrls(request == null ? null : request.iconUrls());
        String description = normalizeDescription(request == null ? null : request.description());
        String status = normalizeStatus(request == null ? null : request.status(), STATUS_ACTIVE);

        int level = 1;
        String parentPath = "";
        if (parentId > 0) {
            Map<String, Object> parent = findRequiredCategory(parentId);
            int parentLevel = toInt(value(parent, "level"), 1);
            int parentProductCount = toInt(value(parent, "productCount"), 0);
            if (parentProductCount > 0) {
                throw new AdminServiceException(
                        "ADMIN_PRODUCT_CATEGORY_PARENT_HAS_PRODUCTS",
                        "该分类下已存在商品，不能继续添加子分类，请先移动或删除该分类下的商品。",
                        HttpStatus.BAD_REQUEST);
            }
            if (STATUS_ACTIVE.equals(status) && !STATUS_ACTIVE.equals(toText(value(parent, "status")))) {
                throw new AdminServiceException(
                        "ADMIN_PRODUCT_CATEGORY_PARENT_DISABLED",
                        "父分类为禁用状态，不能添加启用子分类。",
                        HttpStatus.BAD_REQUEST);
            }
            level = parentLevel + 1;
            parentPath = normalizeExistingPath(toText(value(parent, "path")));
        }

        Long id = snowflakeIdWorker.nextId();
        String path = parentId == 0 ? "/" + id + "/" : parentPath + id + "/";
        try {
            productCategoryMapper.insertCategory(
                    id,
                    parentId,
                    name,
                    code,
                    level,
                    path,
                    sortOrder,
                    iconUrlsJson,
                    description,
                    status);
            if (parentId > 0) {
                productCategoryMapper.markParentAsNonLeaf(parentId);
            }
        } catch (DuplicateKeyException e) {
            throw duplicateCategoryException();
        }
        publicProductCategoryRelationService.evictAfterCommit();
        categoryIndexService.syncCategoriesAfterCommit(parentId > 0 ? List.of(id, parentId) : List.of(id));
        if (STATUS_ACTIVE.equals(status)) {
            categoryBloomService.addActiveCategoryIdsAfterCommit(List.of(id));
        }
        return findTreeNodeById(id);
    }

    @Transactional
    public AdminProductCategoryTreeNodeResponse update(Long id, AdminProductCategoryUpdateRequest request) {
        Long categoryId = normalizeId(id);
        Map<String, Object> existing = findRequiredCategory(categoryId);
        validateNoParentMove(request == null ? null : request.parentId(), toLong(value(existing, "parentId"), 0L));

        String name = normalizeRequiredText(request == null ? null : request.name(), "分类名称", MAX_NAME_LENGTH);
        String code = normalizeRequiredText(request == null ? null : request.code(), "分类编码", MAX_CODE_LENGTH);
        int sortOrder = normalizeSortOrder(request == null ? null : request.sortOrder());
        String iconUrlsJson = normalizeIconUrls(request == null ? null : request.iconUrls());
        String description = normalizeDescription(request == null ? null : request.description());

        try {
            int updatedRows = productCategoryMapper.updateCategoryContent(
                    categoryId,
                    name,
                    code,
                    sortOrder,
                    iconUrlsJson,
                    description);
            if (updatedRows == 0) {
                throw categoryNotFoundException();
            }
        } catch (DuplicateKeyException e) {
            throw duplicateCategoryException();
        }

        productIndexService.syncProductsByCategoryIdsAfterCommit(List.of(categoryId));
        publicProductDetailCacheService.invalidateByCategoryIdsAfterCommit(List.of(categoryId));
        String requestedStatus = normalizeOptionalStatus(request == null ? null : request.status());
        if (requestedStatus != null && !requestedStatus.equals(toText(value(existing, "status")))) {
            return changeStatus(categoryId, new AdminProductCategoryStatusRequest(requestedStatus));
        }
        publicProductCategoryRelationService.evictAfterCommit();
        categoryIndexService.syncCategoriesAfterCommit(List.of(categoryId));
        return findTreeNodeById(categoryId);
    }

    @Transactional
    public AdminProductCategoryTreeNodeResponse changeStatus(Long id, AdminProductCategoryStatusRequest request) {
        Long categoryId = normalizeId(id);
        String targetStatus = normalizeStatus(request == null ? null : request.status(), "");
        if (STATUS_DISABLED.equals(targetStatus)) {
            disableCategories(List.of(categoryId));
            return findTreeNodeById(categoryId);
        }

        Map<String, Object> category = findRequiredCategory(categoryId);
        Long parentId = toLong(value(category, "parentId"), 0L);
        if (parentId > 0) {
            Map<String, Object> parent = findRequiredCategory(parentId);
            if (!STATUS_ACTIVE.equals(toText(value(parent, "status")))) {
                throw new AdminServiceException(
                        "ADMIN_PRODUCT_CATEGORY_PARENT_DISABLED",
                        "父分类为禁用状态，子分类不能单独启用。",
                        HttpStatus.BAD_REQUEST);
            }
        }
        int updatedRows = productCategoryMapper.enableCategory(categoryId);
        if (updatedRows == 0) {
            throw categoryNotFoundException();
        }
        publicProductCategoryRelationService.evictAfterCommit();
        categoryIndexService.syncCategoriesAfterCommit(List.of(categoryId));
        categoryBloomService.addActiveCategoryIdsAfterCommit(List.of(categoryId));
        publicProductDetailCacheService.invalidateByCategoryIdsAfterCommit(List.of(categoryId));
        return findTreeNodeById(categoryId);
    }

    @Transactional
    public AdminProductCategoryBatchDisableResponse batchDisable(AdminProductCategoryBatchDisableRequest request) {
        List<Long> categoryIds = normalizeIds(request == null ? null : request.ids());
        return disableCategories(categoryIds);
    }

    @Transactional
    public void delete(Long id) {
        Long categoryId = normalizeId(id);
        Map<String, Object> result = productCategoryMapper.deleteCategoryIfAllowed(categoryId);
        if (!toBoolean(value(result, "categoryExists"))) {
            throw categoryNotFoundException();
        }
        int childCount = toInt(value(result, "childCount"), 0);
        if (childCount > 0) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_CATEGORY_HAS_CHILDREN",
                    "该分类下存在子分类，请先处理子分类后再删除。",
                    HttpStatus.BAD_REQUEST);
        }
        int activeProductCount = toInt(value(result, "activeProductCount"), 0);
        if (activeProductCount > 0) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_CATEGORY_HAS_ACTIVE_PRODUCTS",
                    "该分类下存在启用商品，请先将商品禁用后再删除分类。",
                    HttpStatus.BAD_REQUEST);
        }
        if (!toBoolean(value(result, "deleted"))) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_CATEGORY_DELETE_FAILED",
                    "分类删除失败，请刷新后重试。",
                    HttpStatus.CONFLICT);
        }
        publicProductCategoryRelationService.evictAfterCommit();
        categoryBloomService.removeActiveCategoryIdsAfterCommit(List.of(categoryId));
        categoryIndexService.deleteCategoriesAfterCommit(List.of(categoryId));
        Long parentId = toLong(value(result, "parentId"), 0L);
        if (parentId > 0) {
            categoryIndexService.syncCategoriesAfterCommit(List.of(parentId));
        }
        productIndexService.syncProductsByCategoryIdsAfterCommit(List.of(categoryId));
        publicProductDetailCacheService.invalidateByCategoryIdsAfterCommit(List.of(categoryId));
    }

    private AdminProductCategoryBatchDisableResponse disableCategories(List<Long> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_CATEGORY_BATCH_EMPTY",
                    "请选择需要禁用的商品分类。",
                    HttpStatus.BAD_REQUEST);
        }
        Map<String, Object> result = productCategoryMapper.disableSubtreesIfAllowed(categoryIds);
        int requestedCount = toInt(value(result, "requestedCount"), categoryIds.size());
        int rootCount = toInt(value(result, "rootCount"), 0);
        if (rootCount != requestedCount) {
            throw categoryNotFoundException();
        }
        int activeProductCount = toInt(value(result, "activeProductCount"), 0);
        if (activeProductCount > 0) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_CATEGORY_HAS_ACTIVE_PRODUCTS",
                    "选中的分类或子分类下存在启用商品，请先将商品禁用后再批量禁用分类。",
                    HttpStatus.BAD_REQUEST);
        }
        AdminProductCategoryBatchDisableResponse response = new AdminProductCategoryBatchDisableResponse(
                requestedCount,
                rootCount,
                toInt(value(result, "subtreeCount"), 0),
                toInt(value(result, "affectedCount"), 0));
        publicProductCategoryRelationService.evictAfterCommit();
        List<Long> affectedIds = parseLongList(value(result, "affectedIdsJson"));
        categoryBloomService.removeActiveCategoryIdsAfterCommit(affectedIds);
        categoryIndexService.syncCategoriesAfterCommit(affectedIds);
        publicProductDetailCacheService.invalidateByCategoryIdsAfterCommit(affectedIds);
        return response;
    }

    private AdminProductCategoryTreeNodeResponse findTreeNodeById(Long id) {
        Map<String, Object> row = productCategoryMapper.findCategoryTreeRowById(id);
        if (row == null || row.isEmpty()) {
            throw categoryNotFoundException();
        }
        return toResponse(toNode(row));
    }

    private Map<String, Object> findRequiredCategory(Long id) {
        Map<String, Object> row = productCategoryMapper.findCategoryById(id);
        if (row == null || row.isEmpty()) {
            throw categoryNotFoundException();
        }
        return row;
    }

    private CategoryNode toNode(Map<String, Object> row) {
        return toNode(row, null);
    }

    private CategoryNode toNode(Map<String, Object> row, String nameHighlight) {
        int childCount = toInt(value(row, "childCount"), 0);
        return new CategoryNode(
                toLong(value(row, "id"), 0L),
                toLong(value(row, "parentId"), 0L),
                toText(value(row, "name")),
                toText(value(row, "code")),
                toInt(value(row, "level"), 1),
                toText(value(row, "path")),
                toInt(value(row, "sortOrder"), 0),
                parseIconUrls(toText(value(row, "iconUrlsJson"))),
                toText(value(row, "description")),
                toText(value(row, "status")),
                childCount == 0,
                childCount,
                toInt(value(row, "productCount"), 0),
                toInt(value(row, "activeProductCount"), 0),
                normalizeText(nameHighlight).isEmpty() ? null : nameHighlight,
                new ArrayList<>());
    }

    private AdminProductCategoryTreeNodeResponse toResponse(CategoryNode node) {
        return new AdminProductCategoryTreeNodeResponse(
                node.id(),
                node.parentId(),
                node.name(),
                node.code(),
                node.level(),
                node.path(),
                node.sortOrder(),
                node.iconUrls(),
                node.description(),
                node.status(),
                node.isLeaf(),
                node.childCount(),
                node.productCount(),
                node.activeProductCount(),
                node.nameHighlight(),
                node.children().stream()
                        .map(this::toResponse)
                        .toList());
    }

    private Long normalizeId(Long id) {
        if (id == null || id <= 0) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_CATEGORY_ID_INVALID",
                    "分类 ID 无效。",
                    HttpStatus.BAD_REQUEST);
        }
        return id;
    }

    private List<Long> normalizeIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        for (Long id : ids) {
            if (id == null || id <= 0) {
                throw new AdminServiceException(
                        "ADMIN_PRODUCT_CATEGORY_ID_INVALID",
                        "分类 ID 无效。",
                        HttpStatus.BAD_REQUEST);
            }
        }
        return ids.stream()
                .distinct()
                .toList();
    }

    private Long normalizeParentId(Long parentId) {
        if (parentId == null) {
            return 0L;
        }
        if (parentId < 0) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_CATEGORY_PARENT_INVALID",
                    "父分类 ID 无效。",
                    HttpStatus.BAD_REQUEST);
        }
        return parentId;
    }

    private String normalizeRequiredText(String raw, String label, int maxLength) {
        String value = normalizeText(raw);
        if (value.isEmpty()) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_CATEGORY_REQUIRED",
                    label + "不能为空。",
                    HttpStatus.BAD_REQUEST);
        }
        if (value.length() > maxLength) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_CATEGORY_TEXT_TOO_LONG",
                    label + "不能超过 " + maxLength + " 个字符。",
                    HttpStatus.BAD_REQUEST);
        }
        return value;
    }

    private int normalizeSortOrder(Integer sortOrder) {
        return sortOrder == null ? 0 : sortOrder;
    }

    private String normalizeDescription(String raw) {
        String value = normalizeText(raw);
        if (value.length() > MAX_DESCRIPTION_LENGTH) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_CATEGORY_DESCRIPTION_TOO_LONG",
                    "分类描述不能超过 " + MAX_DESCRIPTION_LENGTH + " 个字符。",
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
                    "ADMIN_PRODUCT_CATEGORY_STATUS_INVALID",
                    "分类状态只能是 ACTIVE 或 DISABLED。",
                    HttpStatus.BAD_REQUEST);
        }
        return status;
    }

    private String normalizeOptionalStatus(String raw) {
        String value = normalizeText(raw);
        if (value.isEmpty()) {
            return null;
        }
        return normalizeStatus(value, "");
    }

    private String normalizeSearchKeyword(String raw) {
        String value = normalizeText(raw);
        if (value.length() > MAX_NAME_LENGTH) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_CATEGORY_SEARCH_TOO_LONG",
                    "Category search keyword cannot exceed " + MAX_NAME_LENGTH + " characters.",
                    HttpStatus.BAD_REQUEST);
        }
        return value;
    }

    private String normalizeIconUrls(JsonNode iconUrls) {
        JsonNode normalized = iconUrls;
        if (normalized == null || normalized.isNull() || normalized.isMissingNode()) {
            normalized = objectMapper.createArrayNode();
        } else if (normalized.isTextual()) {
            String raw = normalized.asText("").trim();
            if (raw.isEmpty()) {
                normalized = objectMapper.createArrayNode();
            } else {
                try {
                    normalized = objectMapper.readTree(raw);
                } catch (JsonProcessingException e) {
                    throw iconUrlsInvalidException();
                }
            }
        }
        if (!normalized.isArray()) {
            throw iconUrlsInvalidException();
        }
        try {
            return objectMapper.writeValueAsString(normalized);
        } catch (JsonProcessingException e) {
            throw iconUrlsInvalidException();
        }
    }

    private JsonNode parseIconUrls(String raw) {
        String value = normalizeText(raw);
        if (value.isEmpty()) {
            return objectMapper.createArrayNode();
        }
        try {
            JsonNode node = objectMapper.readTree(value);
            return node != null && node.isArray() ? node : objectMapper.createArrayNode();
        } catch (JsonProcessingException e) {
            return objectMapper.createArrayNode();
        }
    }

    private List<Long> parseLongList(Object raw) {
        String value = toText(raw);
        if (value.isEmpty()) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(value);
            if (node == null || !node.isArray()) {
                return List.of();
            }
            List<Long> ids = new ArrayList<>();
            for (JsonNode item : node) {
                long id = item.isIntegralNumber() ? item.longValue() : Long.parseLong(item.asText(""));
                if (id > 0) {
                    ids.add(id);
                }
            }
            return ids.stream().distinct().toList();
        } catch (JsonProcessingException | NumberFormatException e) {
            return List.of();
        }
    }

    private void validateNoParentMove(Long requestedParentId, Long existingParentId) {
        if (requestedParentId == null) {
            return;
        }
        Long normalizedParentId = normalizeParentId(requestedParentId);
        if (!Objects.equals(normalizedParentId, existingParentId)) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_CATEGORY_MOVE_UNSUPPORTED",
                    "当前版本不支持移动分类父级。",
                    HttpStatus.BAD_REQUEST);
        }
    }

    private String normalizeExistingPath(String path) {
        String normalized = normalizeText(path);
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        if (!normalized.endsWith("/")) {
            normalized = normalized + "/";
        }
        return normalized;
    }

    private AdminServiceException duplicateCategoryException() {
        return new AdminServiceException(
                "ADMIN_PRODUCT_CATEGORY_DUPLICATE",
                "同级分类名称或分类编码已存在。",
                HttpStatus.BAD_REQUEST);
    }

    private AdminServiceException categoryNotFoundException() {
        return new AdminServiceException(
                "ADMIN_PRODUCT_CATEGORY_NOT_FOUND",
                "分类不存在。",
                HttpStatus.NOT_FOUND);
    }

    private AdminServiceException iconUrlsInvalidException() {
        return new AdminServiceException(
                "ADMIN_PRODUCT_CATEGORY_ICON_URLS_INVALID",
                "分类图标必须是 JSON 数组。",
                HttpStatus.BAD_REQUEST);
    }

    private Object value(Map<String, Object> row, String key) {
        if (row == null || key == null) {
            return null;
        }
        if (row.containsKey(key)) {
            return row.get(key);
        }
        String snakeKey = toSnakeCase(key);
        if (row.containsKey(snakeKey)) {
            return row.get(snakeKey);
        }
        return null;
    }

    private String toSnakeCase(String key) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < key.length(); index += 1) {
            char ch = key.charAt(index);
            if (Character.isUpperCase(ch)) {
                builder.append('_').append(Character.toLowerCase(ch));
            } else {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private String normalizeText(String raw) {
        return raw == null ? "" : raw.trim();
    }

    private String toText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private Long toLong(Object value, Long defaultValue) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = toText(value);
        if (text.isEmpty()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private int toInt(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = toText(value);
        if (text.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private boolean toBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(toText(value));
    }

    private record CategoryNode(Long id,
                                Long parentId,
                                String name,
                                String code,
                                Integer level,
                                String path,
                                Integer sortOrder,
                                JsonNode iconUrls,
                                String description,
                                String status,
                                Boolean isLeaf,
                                Integer childCount,
                                Integer productCount,
                                Integer activeProductCount,
                                String nameHighlight,
                                List<CategoryNode> children) {
    }
}
