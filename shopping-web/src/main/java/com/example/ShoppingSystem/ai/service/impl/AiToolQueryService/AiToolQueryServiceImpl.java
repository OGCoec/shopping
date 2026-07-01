package com.example.ShoppingSystem.ai.service.impl.AiToolQueryService;

import com.example.ShoppingSystem.Utils.HybridIdCodec;
import com.example.ShoppingSystem.Utils.ProductSkuIdCodec;
import com.example.ShoppingSystem.ai.dto.AiToolIntent;
import com.example.ShoppingSystem.ai.dto.AiToolIntentType;
import com.example.ShoppingSystem.ai.dto.AiToolResult;
import com.example.ShoppingSystem.ai.service.AiToolQueryService;
import com.example.ShoppingSystem.config.datasource.ProductReadReplicaQueryExecutor;
import com.example.ShoppingSystem.coupon.dto.UserCouponTemplateCardResponse;
import com.example.ShoppingSystem.coupon.dto.UserCouponTemplateDetailResponse;
import com.example.ShoppingSystem.coupon.dto.UserCouponTemplatePageResponse;
import com.example.ShoppingSystem.coupon.service.CouponRedisKeys;
import com.example.ShoppingSystem.coupon.service.UserCouponQueryService;
import com.example.ShoppingSystem.mapper.product.ProductHotSkuMapper;
import com.example.ShoppingSystem.mapper.product.ProductSpuMapper;
import com.example.ShoppingSystem.product.dto.PublicProductCategoryTreeNodeResponse;
import com.example.ShoppingSystem.product.dto.PublicProductDetailResponse;
import com.example.ShoppingSystem.product.dto.PublicProductSearchResponse;
import com.example.ShoppingSystem.product.dto.PublicProductSkuResponse;
import com.example.ShoppingSystem.product.dto.PublicProductSummaryResponse;
import com.example.ShoppingSystem.product.service.PublicProductCategoryBrowseService;
import com.example.ShoppingSystem.product.service.PublicProductDetailService;
import com.example.ShoppingSystem.product.service.PublicProductSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AiToolQueryServiceImpl implements AiToolQueryService {

    private static final int TOOL_PAGE = 1;
    private static final int TOOL_PAGE_SIZE = 10;
    private static final int MAX_EXACT_NAME_LENGTH = 128;
    private static final String HOT_SKU_STOCK_KEY_PREFIX = "shopping:product:hot-sku:stock:";

    private final PublicProductCategoryBrowseService categoryBrowseService;
    private final PublicProductSearchService productSearchService;
    private final PublicProductDetailService productDetailService;
    private final ProductSpuMapper productSpuMapper;
    private final ProductHotSkuMapper productHotSkuMapper;
    private final ProductReadReplicaQueryExecutor productReadReplicaQueryExecutor;
    private final UserCouponQueryService userCouponQueryService;
    private final StringRedisTemplate stringRedisTemplate;

    public AiToolQueryServiceImpl(PublicProductCategoryBrowseService categoryBrowseService,
                                  PublicProductSearchService productSearchService,
                                  PublicProductDetailService productDetailService,
                                  ProductSpuMapper productSpuMapper,
                                  ProductHotSkuMapper productHotSkuMapper,
                                  ProductReadReplicaQueryExecutor productReadReplicaQueryExecutor,
                                  UserCouponQueryService userCouponQueryService,
                                  StringRedisTemplate stringRedisTemplate) {
        this.categoryBrowseService = categoryBrowseService;
        this.productSearchService = productSearchService;
        this.productDetailService = productDetailService;
        this.productSpuMapper = productSpuMapper;
        this.productHotSkuMapper = productHotSkuMapper;
        this.productReadReplicaQueryExecutor = productReadReplicaQueryExecutor;
        this.userCouponQueryService = userCouponQueryService;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public AiToolResult execute(Long userId, AiToolIntent intent) {
        AiToolIntent safeIntent = intent == null
                ? new AiToolIntent(AiToolIntentType.CLARIFY, "", null, "", "", null, TOOL_PAGE, TOOL_PAGE_SIZE)
                : intent;
        return switch (safeIntent.intent()) {
            case LIST_CATEGORIES -> listCategories(safeIntent);
            case SEARCH_PRODUCTS -> searchProducts(safeIntent);
            case FIND_PRODUCT_EXACT -> findProductExact(safeIntent);
            case GET_PRODUCT_DETAIL -> getProductDetail(safeIntent);
            case LIST_HOT_SKUS -> listHotSkus();
            case GET_HOT_SKU_DETAIL -> getHotSkuDetail(safeIntent);
            case SEARCH_COUPONS -> searchCoupons(userId, safeIntent);
            case GET_COUPON_DETAIL -> getCouponDetail(userId, safeIntent);
            case GET_COUPON_STOCK -> getCouponStock(userId, safeIntent);
            case UNSUPPORTED -> new AiToolResult(AiToolIntentType.UNSUPPORTED, "unsupported",
                    "这个 AI 助手 v1 只支持商品、热点商品和优惠券的只读查询，不能领券、下单、退款或修改数据。", Map.of());
            case CLARIFY -> new AiToolResult(AiToolIntentType.CLARIFY, "clarify",
                    "请补充你要查询的商品名、商品 ID、SKU ID 或优惠券名称/ID。", Map.of());
        };
    }

    private AiToolResult listCategories(AiToolIntent intent) {
        String keyword = normalizeText(intent.query());
        List<PublicProductCategoryTreeNodeResponse> categories = keyword.isBlank()
                ? categoryBrowseService.tree()
                : categoryBrowseService.search(keyword);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("keyword", keyword);
        data.put("categories", compactCategories(categories, 60));
        return new AiToolResult(AiToolIntentType.LIST_CATEGORIES, "ok",
                categories.isEmpty() ? "未找到匹配的商品分类。" : "已查询到商品分类。", data);
    }

    private AiToolResult searchProducts(AiToolIntent intent) {
        String keyword = normalizeText(intent.query());
        PublicProductSearchResponse response = productSearchService.search(
                keyword,
                normalizePositiveLong(intent.categoryId()),
                normalizePage(intent.page()),
                normalizePageSize(intent.pageSize()));
        List<Map<String, Object>> records = response.records().stream()
                .limit(TOOL_PAGE_SIZE)
                .map(this::simpleProduct)
                .toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("keyword", keyword);
        data.put("total", response.total());
        data.put("page", response.page());
        data.put("pageSize", response.pageSize());
        data.put("records", records);
        return new AiToolResult(AiToolIntentType.SEARCH_PRODUCTS, response.total() <= 0 ? "not_found" : "ok",
                response.total() <= 0 ? "未找到匹配商品。" : "已查询到商品列表。", data);
    }

    private AiToolResult findProductExact(AiToolIntent intent) {
        String name = normalizeExactName(intent.query());
        if (name.isBlank()) {
            return new AiToolResult(AiToolIntentType.CLARIFY, "clarify", "请提供要精确查询的商品名称。", Map.of());
        }
        List<Map<String, Object>> rows = productReadReplicaQueryExecutor.query(() ->
                productSpuMapper.listActivePublicSpuCandidatesByExactName(name, TOOL_PAGE_SIZE));
        if (rows == null || rows.isEmpty()) {
            return new AiToolResult(AiToolIntentType.FIND_PRODUCT_EXACT, "not_found",
                    "没有找到这个精确名称的在售商品。", Map.of("name", name));
        }
        List<Map<String, Object>> candidates = rows.stream()
                .map(this::simpleProductCandidate)
                .toList();
        if (candidates.size() > 1) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("name", name);
            data.put("candidates", candidates);
            return new AiToolResult(AiToolIntentType.FIND_PRODUCT_EXACT, "clarify",
                    "找到多个同名商品，请让用户根据商品 ID、分类或副标题选择一个。", data);
        }
        Long productId = toLong(value(rows.get(0), "id"), 0L);
        PublicProductDetailResponse detail = productDetailService.detail(String.valueOf(productId));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", name);
        data.put("detail", simpleProductDetail(detail));
        return new AiToolResult(AiToolIntentType.FIND_PRODUCT_EXACT, "ok", "已精确查询到商品详情。", data);
    }

    private AiToolResult getProductDetail(AiToolIntent intent) {
        Long productId = normalizePositiveLong(intent.productId());
        if (productId == null) {
            return new AiToolResult(AiToolIntentType.CLARIFY, "clarify", "请提供明确的商品 ID。", Map.of());
        }
        PublicProductDetailResponse detail = productDetailService.detail(String.valueOf(productId));
        return new AiToolResult(AiToolIntentType.GET_PRODUCT_DETAIL, "ok",
                "已查询到商品详情。", Map.of("detail", simpleProductDetail(detail)));
    }

    private AiToolResult listHotSkus() {
        OffsetDateTime now = OffsetDateTime.now();
        List<Map<String, Object>> rows = productReadReplicaQueryExecutor.query(() ->
                productHotSkuMapper.listActiveHotSkus(now, TOOL_PAGE_SIZE, 0));
        List<Map<String, Object>> hotSkus = withHotRemaining(rows);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", hotSkus);
        data.put("totalReturned", hotSkus.size());
        return new AiToolResult(AiToolIntentType.LIST_HOT_SKUS, hotSkus.isEmpty() ? "not_found" : "ok",
                hotSkus.isEmpty() ? "当前没有可展示的热点商品。" : "已查询到当前热点商品。", data);
    }

    private AiToolResult getHotSkuDetail(AiToolIntent intent) {
        String skuId = normalizeText(intent.skuId());
        if (!skuId.matches(ProductSkuIdCodec.BASE62_PATTERN)) {
            return new AiToolResult(AiToolIntentType.CLARIFY, "clarify", "请提供明确的 SKU ID。", Map.of());
        }
        byte[] skuIdBytes;
        try {
            skuIdBytes = ProductSkuIdCodec.fromBase62(skuId);
        } catch (IllegalArgumentException e) {
            return new AiToolResult(AiToolIntentType.CLARIFY, "clarify", "SKU ID 格式无效，请重新确认。", Map.of());
        }
        OffsetDateTime now = OffsetDateTime.now();
        Map<String, Object> row = productReadReplicaQueryExecutor.query(() ->
                productHotSkuMapper.findActiveHotSkuBySkuId(skuIdBytes, now));
        if (row == null || row.isEmpty()) {
            return new AiToolResult(AiToolIntentType.GET_HOT_SKU_DETAIL, "not_found",
                    "没有找到这个 SKU 对应的当前有效热点商品。", Map.of("skuId", skuId));
        }
        List<Map<String, Object>> records = withHotRemaining(List.of(row));
        return new AiToolResult(AiToolIntentType.GET_HOT_SKU_DETAIL, "ok",
                "已查询到热点 SKU 详情。", Map.of("detail", records.getFirst()));
    }

    private AiToolResult searchCoupons(Long userId, AiToolIntent intent) {
        UserCouponTemplatePageResponse page = userCouponQueryService.receivablePage(
                userId,
                normalizePage(intent.page()),
                normalizePageSize(intent.pageSize()),
                normalizeText(intent.query()));
        List<Map<String, Object>> records = couponCardsWithRuntimeStock(page.records());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("keyword", normalizeText(intent.query()));
        data.put("total", page.total());
        data.put("page", page.page());
        data.put("pageSize", page.pageSize());
        data.put("records", records);
        return new AiToolResult(AiToolIntentType.SEARCH_COUPONS, records.isEmpty() ? "not_found" : "ok",
                records.isEmpty() ? "未找到可领取优惠券。" : "已查询到可领取优惠券。", data);
    }

    private AiToolResult getCouponDetail(Long userId, AiToolIntent intent) {
        String couponTemplateId = normalizeText(intent.couponTemplateId());
        if (couponTemplateId.isBlank()) {
            return new AiToolResult(AiToolIntentType.CLARIFY, "clarify", "请提供明确的优惠券 ID。", Map.of());
        }
        try {
            UserCouponTemplateDetailResponse detail = userCouponQueryService.receivableDetail(userId, couponTemplateId);
            return new AiToolResult(AiToolIntentType.GET_COUPON_DETAIL, "ok",
                    "已查询到优惠券详情。", Map.of("detail", couponDetailWithRuntimeStock(detail)));
        } catch (ResponseStatusException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND || e.getStatusCode() == HttpStatus.BAD_REQUEST) {
                return new AiToolResult(AiToolIntentType.GET_COUPON_DETAIL, "not_found",
                        "没有找到这个优惠券，请确认优惠券 ID 是否正确或是否仍在领取时间内。", Map.of("couponTemplateId", couponTemplateId));
            }
            throw e;
        }
    }

    private AiToolResult getCouponStock(Long userId, AiToolIntent intent) {
        String couponTemplateId = normalizeText(intent.couponTemplateId());
        if (!couponTemplateId.isBlank()) {
            return getCouponDetail(userId, intent);
        }
        String query = normalizeText(intent.query());
        if (query.isBlank()) {
            return new AiToolResult(AiToolIntentType.CLARIFY, "clarify", "请明确是哪一个优惠券，再查询剩余数量。", Map.of());
        }
        UserCouponTemplatePageResponse page = userCouponQueryService.receivablePage(userId, TOOL_PAGE, TOOL_PAGE_SIZE, query);
        List<UserCouponTemplateCardResponse> records = page.records();
        if (records == null || records.isEmpty()) {
            return new AiToolResult(AiToolIntentType.GET_COUPON_STOCK, "not_found",
                    "没有找到这个优惠券，请换一个名称或提供优惠券 ID。", Map.of("query", query));
        }
        if (records.size() > 1) {
            return new AiToolResult(AiToolIntentType.GET_COUPON_STOCK, "clarify",
                    "找到多个匹配优惠券，请让用户明确优惠券 ID、名称或编号。",
                    Map.of("query", query, "candidates", couponCardsWithRuntimeStock(records)));
        }
        String matchedId = records.getFirst().couponTemplateId();
        return getCouponDetail(userId, new AiToolIntent(
                AiToolIntentType.GET_COUPON_DETAIL,
                query,
                null,
                "",
                matchedId,
                null,
                TOOL_PAGE,
                TOOL_PAGE_SIZE));
    }

    private List<Map<String, Object>> compactCategories(List<PublicProductCategoryTreeNodeResponse> categories, int limit) {
        if (categories == null || categories.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> output = new ArrayList<>();
        for (PublicProductCategoryTreeNodeResponse category : categories) {
            if (output.size() >= limit) {
                break;
            }
            compactCategory(category, output, 0, limit);
        }
        return List.copyOf(output);
    }

    private void compactCategory(PublicProductCategoryTreeNodeResponse category,
                                 List<Map<String, Object>> output,
                                 int depth,
                                 int limit) {
        if (category == null || output.size() >= limit || depth > 3) {
            return;
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", category.id());
        item.put("name", category.name());
        item.put("level", category.level());
        output.add(item);
        if (category.children() == null || category.children().isEmpty()) {
            return;
        }
        for (PublicProductCategoryTreeNodeResponse child : category.children()) {
            if (output.size() >= limit) {
                break;
            }
            compactCategory(child, output, depth + 1, limit);
        }
    }

    private Map<String, Object> simpleProduct(PublicProductSummaryResponse product) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", product.id());
        item.put("categoryId", product.categoryId());
        item.put("categoryName", product.categoryName());
        item.put("name", cleanHighlight(product.nameHighlight(), product.name()));
        item.put("subtitle", product.subtitle());
        item.put("brandName", product.brandName());
        return item;
    }

    private Map<String, Object> simpleProductCandidate(Map<String, Object> row) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", toLong(value(row, "id"), 0L));
        item.put("categoryId", toLong(value(row, "categoryId"), 0L));
        item.put("categoryName", normalizeText(value(row, "categoryName")));
        item.put("name", normalizeText(value(row, "name")));
        item.put("subtitle", normalizeText(value(row, "subtitle")));
        item.put("brandName", normalizeText(value(row, "brandName")));
        return item;
    }

    private Map<String, Object> simpleProductDetail(PublicProductDetailResponse detail) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", detail.id());
        item.put("categoryId", detail.categoryId());
        item.put("categoryName", detail.categoryName());
        item.put("name", detail.name());
        item.put("subtitle", detail.subtitle());
        item.put("brandName", detail.brandName());
        item.put("description", detail.description());
        item.put("afterSale", detail.afterSale());
        List<Map<String, Object>> skus = new ArrayList<>();
        if (detail.skus() != null) {
            for (PublicProductSkuResponse sku : detail.skus()) {
                if (skus.size() >= TOOL_PAGE_SIZE) {
                    break;
                }
                Map<String, Object> skuItem = new LinkedHashMap<>();
                skuItem.put("skuId", ProductSkuIdCodec.toBase62FromDatabaseValue(sku.id()));
                skuItem.put("skuName", sku.skuName());
                skuItem.put("priceYuan", sku.priceYuan());
                skuItem.put("originalPriceYuan", sku.originalPriceYuan());
                skuItem.put("stockQuantity", sku.stockQuantity());
                skus.add(skuItem);
            }
        }
        item.put("skus", skus);
        return item;
    }

    private List<Map<String, Object>> withHotRemaining(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<String> skuIds = rows.stream()
                .map(row -> ProductSkuIdCodec.toBase62FromDatabaseValue(value(row, "skuId")))
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
        Map<String, Integer> redisRemainingBySkuId = readHotRemainingBySkuIds(skuIds);
        List<Map<String, Object>> records = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            String skuId = ProductSkuIdCodec.toBase62FromDatabaseValue(value(row, "skuId"));
            Integer dbRemaining = toInteger(value(row, "remainingQuantity"), null);
            Integer runtimeRemaining = redisRemainingBySkuId.getOrDefault(skuId, dbRemaining);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("hotSkuId", HybridIdCodec.toBase62FromDatabaseValue(value(row, "id")));
            item.put("spuId", toLong(value(row, "spuId"), 0L));
            item.put("spuName", normalizeText(value(row, "spuName")));
            item.put("spuSubtitle", normalizeText(value(row, "spuSubtitle")));
            item.put("brandName", normalizeText(value(row, "brandName")));
            item.put("categoryId", toLong(value(row, "categoryId"), 0L));
            item.put("categoryName", normalizeText(value(row, "categoryName")));
            item.put("skuId", skuId);
            item.put("skuCode", normalizeText(value(row, "skuCode")));
            item.put("skuName", normalizeText(value(row, "skuName")));
            item.put("stockQuantity", toInteger(value(row, "stockQuantity"), 0));
            item.put("remainingQuantity", runtimeRemaining);
            item.put("startAt", toOffsetDateTime(value(row, "startAt")));
            item.put("endAt", toOffsetDateTime(value(row, "endAt")));
            records.add(item);
        }
        return List.copyOf(records);
    }

    private Map<String, Integer> readHotRemainingBySkuIds(List<String> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return Map.of();
        }
        List<String> keys = skuIds.stream()
                .map(this::hotSkuStockKey)
                .toList();
        List<String> values;
        try {
            values = stringRedisTemplate.opsForValue().multiGet(keys);
        } catch (Exception e) {
            log.warn("[AI Tool] Redis hot SKU stock batch read failed, count={}", keys.size(), e);
            return Map.of();
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        for (int index = 0; index < skuIds.size(); index += 1) {
            String value = values == null || index >= values.size() ? null : values.get(index);
            Integer remaining = parseNonNegativeInteger(value);
            if (remaining != null) {
                result.put(skuIds.get(index), remaining);
            }
        }
        return result;
    }

    private List<Map<String, Object>> couponCardsWithRuntimeStock(List<UserCouponTemplateCardResponse> records) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        List<String> couponIds = records.stream()
                .map(UserCouponTemplateCardResponse::couponTemplateId)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
        Map<String, Integer> runtimeStockByCouponId = readCouponRemainingByIds(couponIds);
        List<Map<String, Object>> output = new ArrayList<>(records.size());
        for (UserCouponTemplateCardResponse record : records) {
            output.add(simpleCouponCard(record, runtimeStockByCouponId.get(record.couponTemplateId())));
        }
        return List.copyOf(output);
    }

    private Map<String, Object> simpleCouponCard(UserCouponTemplateCardResponse record, Integer runtimeRemaining) {
        Integer remaining = runtimeRemaining == null ? record.remainingQuantity() : runtimeRemaining;
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("couponTemplateId", record.couponTemplateId());
        item.put("couponCode", record.couponCode());
        item.put("name", record.name());
        item.put("discountType", record.discountType());
        item.put("thresholdAmountYuan", record.thresholdAmountYuan());
        item.put("discountAmountYuan", record.discountAmountYuan());
        item.put("discountRate", record.discountRate());
        item.put("maxDiscountAmountYuan", record.maxDiscountAmountYuan());
        item.put("totalQuantity", record.totalQuantity());
        item.put("remainingQuantity", remaining);
        item.put("perUserLimit", record.perUserLimit());
        item.put("receiveStartAt", record.receiveStartAt());
        item.put("receiveEndAt", record.receiveEndAt());
        item.put("validStartAt", record.validStartAt());
        item.put("validEndAt", record.validEndAt());
        item.put("claimed", record.claimed());
        item.put("canClaim", !record.claimed() && remaining != null && remaining > 0);
        return item;
    }

    private Map<String, Object> couponDetailWithRuntimeStock(UserCouponTemplateDetailResponse detail) {
        Integer runtimeRemaining = readCouponRemainingByIds(List.of(detail.couponTemplateId()))
                .get(detail.couponTemplateId());
        Integer remaining = runtimeRemaining == null ? detail.remainingQuantity() : runtimeRemaining;
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("couponTemplateId", detail.couponTemplateId());
        item.put("couponCode", detail.couponCode());
        item.put("name", detail.name());
        item.put("discountType", detail.discountType());
        item.put("thresholdAmountYuan", detail.thresholdAmountYuan());
        item.put("discountAmountYuan", detail.discountAmountYuan());
        item.put("discountRate", detail.discountRate());
        item.put("maxDiscountAmountYuan", detail.maxDiscountAmountYuan());
        item.put("totalQuantity", detail.totalQuantity());
        item.put("remainingQuantity", remaining);
        item.put("perUserLimit", detail.perUserLimit());
        item.put("scopeType", detail.scopeType());
        item.put("targetIds", detail.targetIds());
        item.put("receiveStartAt", detail.receiveStartAt());
        item.put("receiveEndAt", detail.receiveEndAt());
        item.put("validStartAt", detail.validStartAt());
        item.put("validEndAt", detail.validEndAt());
        item.put("claimed", detail.claimed());
        item.put("canClaim", !detail.claimed() && remaining != null && remaining > 0);
        return item;
    }

    private Map<String, Integer> readCouponRemainingByIds(List<String> couponIds) {
        if (couponIds == null || couponIds.isEmpty()) {
            return Map.of();
        }
        List<String> keys = couponIds.stream()
                .map(CouponRedisKeys::stockKey)
                .toList();
        List<String> values;
        try {
            values = stringRedisTemplate.opsForValue().multiGet(keys);
        } catch (Exception e) {
            log.warn("[AI Tool] Redis coupon stock batch read failed, count={}", keys.size(), e);
            return Map.of();
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        for (int index = 0; index < couponIds.size(); index += 1) {
            String value = values == null || index >= values.size() ? null : values.get(index);
            Integer remaining = parseNonNegativeInteger(value);
            if (remaining != null) {
                result.put(couponIds.get(index), remaining);
            }
        }
        return result;
    }

    private String normalizeExactName(String rawName) {
        String name = normalizeText(rawName);
        return name.length() <= MAX_EXACT_NAME_LENGTH ? name : name.substring(0, MAX_EXACT_NAME_LENGTH);
    }

    private int normalizePage(Integer page) {
        return page == null || page <= 0 ? TOOL_PAGE : page;
    }

    private int normalizePageSize(Integer pageSize) {
        return pageSize == null || pageSize <= 0 ? TOOL_PAGE_SIZE : Math.min(pageSize, TOOL_PAGE_SIZE);
    }

    private Long normalizePositiveLong(Long value) {
        return value == null || value <= 0 ? null : value;
    }

    private Integer parseNonNegativeInteger(String value) {
        String text = normalizeText(value);
        if (text.isEmpty()) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(text);
            return parsed >= 0 ? parsed : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String hotSkuStockKey(String skuId) {
        return HOT_SKU_STOCK_KEY_PREFIX + skuId;
    }

    private String cleanHighlight(String highlighted, String fallback) {
        String value = normalizeText(highlighted).isBlank() ? normalizeText(fallback) : normalizeText(highlighted);
        return value.replace("[[HL]]", "").replace("[[/HL]]", "");
    }

    private Object value(Map<String, Object> row, String key) {
        if (row == null || key == null) {
            return null;
        }
        if (row.containsKey(key)) {
            return row.get(key);
        }
        String snakeKey = toSnakeCase(key);
        return row.get(snakeKey);
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

    private Long toLong(Object raw, Long defaultValue) {
        if (raw instanceof Number number) {
            return number.longValue();
        }
        String text = normalizeText(raw);
        if (text.isEmpty()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private Integer toInteger(Object raw, Integer defaultValue) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        String text = normalizeText(raw);
        if (text.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private OffsetDateTime toOffsetDateTime(Object raw) {
        if (raw instanceof OffsetDateTime time) {
            return time;
        }
        if (raw instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant().atZone(ZoneId.systemDefault()).toOffsetDateTime();
        }
        if (raw instanceof java.util.Date date) {
            return date.toInstant().atZone(ZoneId.systemDefault()).toOffsetDateTime();
        }
        String text = normalizeText(raw);
        if (text.isEmpty()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(text);
        } catch (DateTimeParseException ignored) {
            try {
                return Instant.parse(text).atZone(ZoneId.systemDefault()).toOffsetDateTime();
            } catch (DateTimeParseException e) {
                return null;
            }
        }
    }

    private String normalizeText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
