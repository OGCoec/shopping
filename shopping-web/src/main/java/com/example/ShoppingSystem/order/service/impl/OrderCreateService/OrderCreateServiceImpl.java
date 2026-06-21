package com.example.ShoppingSystem.order.service.impl.OrderCreateService;

import com.example.ShoppingSystem.Utils.HybridIdCodec;
import com.example.ShoppingSystem.Utils.HybridSemaphoreIdWorker;
import com.example.ShoppingSystem.mapper.order.OrderMapper;
import com.example.ShoppingSystem.order.dto.OrderCreateRequest;
import com.example.ShoppingSystem.order.dto.OrderCreateResponse;
import com.example.ShoppingSystem.order.rabbit.OrderExpireRabbitProperties;
import com.example.ShoppingSystem.order.redis.OrderRedisKeys;
import com.example.ShoppingSystem.order.service.inventory.OrderInventoryType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;

import com.example.ShoppingSystem.order.service.OrderAmountCalculator;
import com.example.ShoppingSystem.order.service.OrderCreateContext;
import com.example.ShoppingSystem.order.service.OrderCreateService;
import com.example.ShoppingSystem.order.service.OrderCreateTradeWriter;
import com.example.ShoppingSystem.order.service.OrderRedisSnapshotService;
import com.example.ShoppingSystem.order.service.OrderRowMapper;
import com.example.ShoppingSystem.order.service.OrderServiceException;
import com.example.ShoppingSystem.order.service.OrderSkuService;
import com.example.ShoppingSystem.order.service.OrderSkuSnapshot;
import com.example.ShoppingSystem.order.service.OrderStatus;

@Service
public class OrderCreateServiceImpl implements OrderCreateService {

    private static final Duration IDEMPOTENCY_TTL = Duration.ofMinutes(10);

    private final OrderMapper orderMapper;
    private final OrderSkuService orderSkuService;
    private final HybridSemaphoreIdWorker hybridSemaphoreIdWorker;
    private final OrderCreateTradeWriter orderCreateTradeWriter;
    private final OrderRedisSnapshotService orderRedisSnapshotService;
    private final StringRedisTemplate stringRedisTemplate;
    private final OrderExpireRabbitProperties orderExpireRabbitProperties;
    private final boolean orderLoadtestBypassGuards;

    public OrderCreateServiceImpl(OrderMapper orderMapper,
                                  OrderSkuService orderSkuService,
                                  HybridSemaphoreIdWorker hybridSemaphoreIdWorker,
                                  OrderCreateTradeWriter orderCreateTradeWriter,
                                  OrderRedisSnapshotService orderRedisSnapshotService,
                                  StringRedisTemplate stringRedisTemplate,
                                  OrderExpireRabbitProperties orderExpireRabbitProperties,
                                  @Value("${app.order.loadtest.bypass-guards:false}") boolean orderLoadtestBypassGuards) {
        this.orderMapper = orderMapper;
        this.orderSkuService = orderSkuService;
        this.hybridSemaphoreIdWorker = hybridSemaphoreIdWorker;
        this.orderCreateTradeWriter = orderCreateTradeWriter;
        this.orderRedisSnapshotService = orderRedisSnapshotService;
        this.stringRedisTemplate = stringRedisTemplate;
        this.orderExpireRabbitProperties = orderExpireRabbitProperties;
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
        OrderInventoryType inventoryType = sku.hotSku() ? OrderInventoryType.HOT : OrderInventoryType.NORMAL;

        byte[] orderNoBytes = hybridSemaphoreIdWorker.nextId();
        String orderNo = HybridIdCodec.toBase62(orderNoBytes);
        OffsetDateTime expireAt = now.plus(Duration.ofMillis(paymentCheckWindowMillis()));
        if (!acquireIdempotency(redisIdempotencyKey, orderNo)) {
            String oldOrderNo = stringRedisTemplate.opsForValue().get(redisIdempotencyKey);
            OrderCreateResponse replay = oldOrderNo == null
                    ? existingByIdempotency(userId, idempotencyKey, redisIdempotencyKey)
                    : existingByOrderNo(userId, oldOrderNo);
            if (replay != null) {
                return replay;
            }
            throw new OrderServiceException("ORDER_CREATE_PENDING", "Order creation is pending.", HttpStatus.CONFLICT);
        }

        OrderCreateContext context = new OrderCreateContext(
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
        try {
            OrderCreateTradeWriter.OrderCreateDraft draft = orderCreateTradeWriter.createAndRequestStockDeduct(context);
            return new OrderCreateResponse(orderNo, OrderStatus.STOCK_CONFIRMING, expireAt, draft.payAmount());
        } catch (RuntimeException e) {
            stringRedisTemplate.delete(redisIdempotencyKey);
            throw e;
        }
    }

    private boolean acquireIdempotency(String redisKey, String orderNo) {
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(redisKey, orderNo, IDEMPOTENCY_TTL);
        return Boolean.TRUE.equals(acquired);
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
}