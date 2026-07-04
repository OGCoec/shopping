package com.example.ShoppingSystem.order.service.impl.OrderCreateService;

import com.example.ShoppingSystem.Utils.HybridIdCodec;
import com.example.ShoppingSystem.Utils.HybridSemaphoreIdWorker;
import com.example.ShoppingSystem.mapper.order.OrderMapper;
import com.example.ShoppingSystem.order.dto.OrderCreateRequest;
import com.example.ShoppingSystem.order.dto.OrderCreateResponse;
import com.example.ShoppingSystem.order.rabbit.OrderExpireRabbitProperties;
import com.example.ShoppingSystem.order.redis.OrderRedisKeys;
import com.example.ShoppingSystem.order.service.OrderAmountCalculator;
import com.example.ShoppingSystem.order.service.OrderCreateContext;
import com.example.ShoppingSystem.order.service.OrderCreateService;
import com.example.ShoppingSystem.order.service.OrderCreateTradeWriter;
import com.example.ShoppingSystem.order.service.OrderInventoryItem;
import com.example.ShoppingSystem.order.service.OrderRedisSnapshotService;
import com.example.ShoppingSystem.order.service.OrderRowMapper;
import com.example.ShoppingSystem.order.service.OrderServiceException;
import com.example.ShoppingSystem.order.service.OrderSkuService;
import com.example.ShoppingSystem.order.service.OrderSkuSnapshot;
import com.example.ShoppingSystem.order.service.OrderStatus;
import com.example.ShoppingSystem.order.service.inventory.OrderInventoryDeductResult;
import com.example.ShoppingSystem.order.service.inventory.OrderInventoryStrategy;
import com.example.ShoppingSystem.order.service.inventory.OrderInventoryType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OrderCreateServiceImpl implements OrderCreateService {

    private static final Logger log = LoggerFactory.getLogger(OrderCreateServiceImpl.class);

    private static final Duration IDEMPOTENCY_TTL = Duration.ofMinutes(10);
    private static final Set<String> REUSABLE_ORDER_STATUSES = Set.of(
            OrderStatus.STOCK_CONFIRMING,
            OrderStatus.PENDING_PAYMENT
    );

    private final OrderMapper orderMapper;
    private final OrderSkuService orderSkuService;
    private final HybridSemaphoreIdWorker hybridSemaphoreIdWorker;
    private final OrderCreateTradeWriter orderCreateTradeWriter;
    private final OrderRedisSnapshotService orderRedisSnapshotService;
    private final Map<OrderInventoryType, OrderInventoryStrategy> inventoryStrategies;
    private final StringRedisTemplate stringRedisTemplate;
    private final OrderExpireRabbitProperties orderExpireRabbitProperties;
    private final Duration createUserSkuLockTtl;
    private final DefaultRedisScript<Long> unlockScript;
    private final boolean orderLoadtestBypassGuards;

    public OrderCreateServiceImpl(OrderMapper orderMapper,
                                  OrderSkuService orderSkuService,
                                  HybridSemaphoreIdWorker hybridSemaphoreIdWorker,
                                  OrderCreateTradeWriter orderCreateTradeWriter,
                                  OrderRedisSnapshotService orderRedisSnapshotService,
                                  List<OrderInventoryStrategy> inventoryStrategies,
                                  StringRedisTemplate stringRedisTemplate,
                                  OrderExpireRabbitProperties orderExpireRabbitProperties,
                                  @Value("${shopping.order.create-user-sku-lock-ttl-ms:5000}") long createUserSkuLockTtlMs,
                                  @Value("${app.order.loadtest.bypass-guards:false}") boolean orderLoadtestBypassGuards) {
        this.orderMapper = orderMapper;
        this.orderSkuService = orderSkuService;
        this.hybridSemaphoreIdWorker = hybridSemaphoreIdWorker;
        this.orderCreateTradeWriter = orderCreateTradeWriter;
        this.orderRedisSnapshotService = orderRedisSnapshotService;
        this.inventoryStrategies = inventoryStrategies.stream()
                .collect(Collectors.toUnmodifiableMap(OrderInventoryStrategy::type, Function.identity()));
        this.stringRedisTemplate = stringRedisTemplate;
        this.orderExpireRabbitProperties = orderExpireRabbitProperties;
        this.createUserSkuLockTtl = Duration.ofMillis(Math.max(1000L, createUserSkuLockTtlMs));
        this.unlockScript = longRedisScript("lua/order_persist_unlock.lua");
        this.orderLoadtestBypassGuards = orderLoadtestBypassGuards;
    }

    public OrderCreateResponse create(Long userId, OrderCreateRequest request) {
        String rawIdempotencyKey = normalizeIdempotencyKey(request == null ? null : request.idempotencyKey());
        String idempotencyKey = userId + ":" + rawIdempotencyKey;
        String redisIdempotencyKey = OrderRedisKeys.idempotencyKey(userId, rawIdempotencyKey);
        OrderCreateResponse existing = existingByIdempotency(userId, idempotencyKey, redisIdempotencyKey);
        if (existing != null) {
            return existing;
        }

        OffsetDateTime now = OffsetDateTime.now();
        int quantity = normalizeQuantity(request == null ? null : request.quantity());
        OrderSkuSnapshot sku = orderSkuService.loadActiveSku(request == null ? null : request.skuId(), now);
        String createLockKey = OrderRedisKeys.orderCreateUserSkuLockKey(userId, sku.skuIdText());
        String createLockValue = UUID.randomUUID().toString();
        if (!acquireCreateLock(createLockKey, createLockValue)) {
            OrderCreateResponse replay = existingByIdempotency(userId, idempotencyKey, redisIdempotencyKey);
            if (replay != null) {
                return replay;
            }
            OrderCreateResponse reusableOrder = reusableOrderBySku(userId, sku, now);
            if (reusableOrder != null) {
                return reusableOrder;
            }
            throw new OrderServiceException("ORDER_CREATE_PENDING", "Order creation is pending.", HttpStatus.CONFLICT);
        }

        OrderCreateContext context = null;
        boolean inventoryDeducted = false;
        try {
            OrderCreateResponse replay = existingByIdempotency(userId, idempotencyKey, redisIdempotencyKey);
            if (replay != null) {
                return replay;
            }
            OrderCreateResponse reusableOrder = reusableOrderBySku(userId, sku, now);
            if (reusableOrder != null) {
                writeIdempotencyBestEffort(redisIdempotencyKey, reusableOrder.orderNo());
                return reusableOrder;
            }

            String orderNo = HybridIdCodec.toBase62(hybridSemaphoreIdWorker.nextId());
            OffsetDateTime expireAt = now.plus(Duration.ofMillis(paymentCheckWindowMillis()));
            OrderInventoryType inventoryType = sku.hotSku() ? OrderInventoryType.HOT : OrderInventoryType.NORMAL;
            context = new OrderCreateContext(
                    orderNo,
                    userId,
                    sku,
                    quantity,
                    idempotencyKey,
                    request == null ? null : request.userCouponId(),
                    now,
                    expireAt,
                    inventoryType
            );

            ensureInventoryDeducted(deductInventory(context));
            inventoryDeducted = true;

            OrderCreateTradeWriter.OrderCreateDraft draft = orderCreateTradeWriter.createPendingPaymentOrder(context);
            inventoryDeducted = false;
            writeIdempotencyBestEffort(redisIdempotencyKey, orderNo);
            return new OrderCreateResponse(orderNo, OrderStatus.PENDING_PAYMENT, expireAt, draft.payAmount());
        } catch (RuntimeException e) {
            if (inventoryDeducted && context != null) {
                releaseInventory(context);
                OrderCreateResponse replay = existingByIdempotency(userId, idempotencyKey, redisIdempotencyKey);
                if (replay != null) {
                    return replay;
                }
            }
            throw e;
        } finally {
            releaseCreateLock(createLockKey, createLockValue);
        }
    }

    private OrderInventoryDeductResult deductInventory(OrderCreateContext context) {
        OrderInventoryStrategy strategy = inventoryStrategies.get(context.inventoryType());
        if (strategy == null) {
            throw new OrderServiceException("ORDER_INVENTORY_STRATEGY_NOT_FOUND",
                    "Order inventory strategy is missing.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return strategy.deduct(context);
    }

    private void releaseInventory(OrderCreateContext context) {
        try {
            OrderInventoryStrategy strategy = inventoryStrategies.get(context.inventoryType());
            if (strategy == null) {
                log.warn("[Order] inventory release skipped, strategy missing, orderNo={}, type={}",
                        context.orderNo(), context.inventoryType());
                return;
            }
            strategy.release(new OrderInventoryItem(
                    context.orderNo(),
                    context.userId(),
                    context.sku().skuId(),
                    context.sku().skuIdText(),
                    context.quantity(),
                    context.sku().hotSku()
            ));
        } catch (Exception releaseError) {
            log.warn("[Order] inventory release failed after order create failure, orderNo={}",
                    context.orderNo(), releaseError);
        }
    }

    private void ensureInventoryDeducted(OrderInventoryDeductResult result) {
        if (result != null && result.success()) {
            return;
        }
        String code = result == null || result.code() == null || result.code().isBlank()
                ? "ORDER_STOCK_DEDUCT_FAILED"
                : result.code();
        String message = result == null || result.message() == null || result.message().isBlank()
                ? "Order stock deduct failed."
                : result.message();
        throw new OrderServiceException(code, message, inventoryFailureStatus(code));
    }

    private HttpStatus inventoryFailureStatus(String code) {
        if ("ORDER_QUANTITY_INVALID".equals(code)) {
            return HttpStatus.BAD_REQUEST;
        }
        if ("ORDER_INVENTORY_STRATEGY_NOT_FOUND".equals(code)
                || "ORDER_STOCK_DEDUCT_FAILED".equals(code)) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return HttpStatus.CONFLICT;
    }

    private boolean acquireCreateLock(String key, String value) {
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(key, value, createUserSkuLockTtl);
        return Boolean.TRUE.equals(acquired);
    }

    private void releaseCreateLock(String key, String value) {
        if (key == null || key.isBlank() || value == null || value.isBlank()) {
            return;
        }
        stringRedisTemplate.execute(unlockScript, List.of(key), value);
    }

    private void writeIdempotencyBestEffort(String redisKey, String orderNo) {
        if (redisKey == null || redisKey.isBlank() || orderNo == null || orderNo.isBlank()) {
            return;
        }
        try {
            stringRedisTemplate.opsForValue().set(redisKey, orderNo, IDEMPOTENCY_TTL);
        } catch (Exception e) {
            log.warn("[Order] idempotency cache write failed, orderNo={}", orderNo, e);
        }
    }

    private OrderCreateResponse reusableOrderBySku(Long userId, OrderSkuSnapshot sku, OffsetDateTime now) {
        if (orderLoadtestBypassGuards || userId == null || sku == null || sku.skuId() == null) {
            return null;
        }
        Map<String, Object> row = orderMapper.findReusableOrderByUserSku(userId, sku.skuId(), now);
        OrderCreateResponse response = toCreateResponse(row);
        if (response == null || !REUSABLE_ORDER_STATUSES.contains(response.status())) {
            return null;
        }
        OffsetDateTime expireAt = response.expireAt();
        if (expireAt != null && now != null && !expireAt.isAfter(now)) {
            return null;
        }
        return response;
    }

    private OrderCreateResponse existingByIdempotency(Long userId, String idempotencyKey, String redisIdempotencyKey) {
        String orderNo = stringRedisTemplate.opsForValue().get(redisIdempotencyKey);
        if (orderNo != null && !orderNo.isBlank()) {
            OrderCreateResponse response = existingByOrderNo(userId, orderNo);
            if (response != null) {
                return response;
            }
        }
        if (orderLoadtestBypassGuards) {
            return null;
        }
        Map<String, Object> row = orderMapper.findOrderByIdempotencyKey(idempotencyKey, userId);
        return toCreateResponse(row);
    }

    private OrderCreateResponse existingByOrderNo(Long userId, String orderNo) {
        OrderCreateResponse redisResponse = orderRedisSnapshotService.findCreateResponse(userId, orderNo).orElse(null);
        if (redisResponse != null) {
            return redisResponse;
        }
        Map<String, Object> row = orderMapper.findOrderByOrderNoForUser(orderNo, userId);
        return toCreateResponse(row);
    }

    private OrderCreateResponse toCreateResponse(Map<String, Object> row) {
        if (row == null || row.isEmpty()) {
            return null;
        }
        return new OrderCreateResponse(
                OrderRowMapper.text(row, "orderNo"),
                OrderRowMapper.text(row, "status"),
                OrderRowMapper.offsetDateTime(row, "expireAt"),
                OrderAmountCalculator.money(OrderRowMapper.decimal(row, "payAmountYuan"))
        );
    }

    private long paymentCheckWindowMillis() {
        return Math.max(1L, orderExpireRabbitProperties.paymentCheckWindowMillis());
    }

    private int normalizeQuantity(Integer rawQuantity) {
        if (rawQuantity == null || rawQuantity <= 0) {
            throw new OrderServiceException("ORDER_QUANTITY_INVALID", "Quantity is invalid.", HttpStatus.BAD_REQUEST);
        }
        return rawQuantity;
    }

    private String normalizeIdempotencyKey(String rawKey) {
        String value = rawKey == null ? "" : rawKey.trim();
        if (value.isEmpty() || value.length() > 96) {
            throw new OrderServiceException("ORDER_IDEMPOTENCY_KEY_INVALID", "Idempotency key is invalid.", HttpStatus.BAD_REQUEST);
        }
        return value;
    }

    private DefaultRedisScript<Long> longRedisScript(String location) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource(location)));
        script.setResultType(Long.class);
        return script;
    }
}
