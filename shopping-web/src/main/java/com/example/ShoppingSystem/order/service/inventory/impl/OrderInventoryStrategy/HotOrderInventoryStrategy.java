package com.example.ShoppingSystem.order.service.inventory.impl.OrderInventoryStrategy;

import com.example.ShoppingSystem.order.redis.OrderRedisKeys;
import com.example.ShoppingSystem.order.service.OrderCreateContext;
import com.example.ShoppingSystem.order.service.OrderInventoryItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Stream;

import com.example.ShoppingSystem.order.service.inventory.OrderInventoryDeductResult;
import com.example.ShoppingSystem.order.service.inventory.OrderInventoryStrategy;
import com.example.ShoppingSystem.order.service.inventory.OrderInventoryType;
@Component
public class HotOrderInventoryStrategy implements OrderInventoryStrategy {

    private static final Logger log = LoggerFactory.getLogger(HotOrderInventoryStrategy.class);

    private static final int LUA_OK = 0;
    private static final int LUA_CACHE_MISSING = 1;
    private static final int LUA_STATUS_NOT_ACTIVE = 2;
    private static final int LUA_NOT_STARTED = 3;
    private static final int LUA_ENDED = 4;
    private static final int LUA_ALREADY_ORDERED = 5;
    private static final int LUA_QUANTITY_INVALID = 6;
    private static final int LUA_SOLD_OUT = 7;
    private static final Duration PENDING_USER_GRACE = Duration.ofSeconds(60);
    private static final Duration DEFAULT_PENDING_USER_TTL = Duration.ofMinutes(30);
    private static final Duration HOLD_TTL = Duration.ofHours(24);

    private final StringRedisTemplate stringRedisTemplate;
    private final DefaultRedisScript<List> deductScript;
    private final DefaultRedisScript<List> compensateScript;
    private final DefaultRedisScript<List> batchCompensateScript;

    public HotOrderInventoryStrategy(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.deductScript = redisScript("lua/order_hot_sku_deduct.lua");
        this.compensateScript = redisScript("lua/order_hot_sku_compensate.lua");
        this.batchCompensateScript = redisScript("lua/order_hot_sku_compensate_batch.lua");
    }

    @Override
    public OrderInventoryType type() {
        return OrderInventoryType.HOT;
    }

    @Override
    public OrderInventoryDeductResult deduct(OrderCreateContext context) {
        List<?> result = stringRedisTemplate.execute(
                deductScript,
                List.of(
                        OrderRedisKeys.hotSkuMetaKey(context.sku().skuIdText()),
                        OrderRedisKeys.hotSkuStockKey(context.sku().skuIdText()),
                        OrderRedisKeys.hotSkuPendingUserKey(context.sku().skuIdText(), context.userId()),
                        OrderRedisKeys.hotSkuHoldKey(context.orderNo()),
                        OrderRedisKeys.HOT_SKU_STOCK_DIRTY_KEY
                ),
                String.valueOf(context.now().toInstant().toEpochMilli()),
                String.valueOf(context.userId()),
                context.orderNo(),
                context.sku().skuIdText(),
                String.valueOf(context.quantity()),
                String.valueOf(pendingUserTtlMillis(context.now(), context.expireAt())),
                String.valueOf(HOLD_TTL.toMillis())
        );
        int code = resultCode(result);
        if (code == LUA_OK) {
            return OrderInventoryDeductResult.success(resultInt(result, 1));
        }
        return switch (code) {
            case LUA_CACHE_MISSING -> OrderInventoryDeductResult.fail("ORDER_HOT_SKU_CACHE_MISSING", "Hot SKU cache is missing.");
            case LUA_STATUS_NOT_ACTIVE -> OrderInventoryDeductResult.fail("ORDER_HOT_SKU_NOT_ACTIVE", "Hot SKU is not active.");
            case LUA_NOT_STARTED -> OrderInventoryDeductResult.fail("ORDER_HOT_SKU_NOT_STARTED", "Hot SKU has not started.");
            case LUA_ENDED -> OrderInventoryDeductResult.fail("ORDER_HOT_SKU_ENDED", "Hot SKU has ended.");
            case LUA_ALREADY_ORDERED -> OrderInventoryDeductResult.fail("ORDER_ALREADY_CREATED", "Current user already created this hot SKU order.");
            case LUA_QUANTITY_INVALID -> OrderInventoryDeductResult.fail("ORDER_QUANTITY_INVALID", "Quantity is invalid.");
            case LUA_SOLD_OUT -> OrderInventoryDeductResult.fail("ORDER_STOCK_NOT_ENOUGH", "Hot SKU stock is not enough.");
            default -> OrderInventoryDeductResult.fail("ORDER_HOT_SKU_DEDUCT_FAILED", "Hot SKU stock deduct failed.");
        };
    }

    @Override
    public void release(OrderInventoryItem item) {
        try {
            stringRedisTemplate.execute(
                    compensateScript,
                    List.of(
                            OrderRedisKeys.hotSkuStockKey(item.skuIdText()),
                            OrderRedisKeys.hotSkuPendingUserKey(item.skuIdText(), item.userId()),
                            OrderRedisKeys.hotSkuHoldKey(item.orderNo()),
                            OrderRedisKeys.HOT_SKU_STOCK_DIRTY_KEY
                    ),
                    String.valueOf(item.userId()),
                    item.orderNo(),
                    String.valueOf(item.quantity()),
                    item.skuIdText()
            );
        } catch (Exception e) {
            log.warn("[Order] hot SKU inventory release failed, orderNo={}, skuId={}",
                    item.orderNo(), item.skuIdText(), e);
        }
    }

    @Override
    public void releaseAll(List<OrderInventoryItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        List<OrderInventoryItem> validItems = items.stream()
                .filter(item -> item.quantity() > 0)
                .toList();
        if (validItems.isEmpty()) {
            return;
        }
        List<String> keys = Stream.concat(
                        Stream.of(OrderRedisKeys.HOT_SKU_STOCK_DIRTY_KEY),
                        validItems.stream().flatMap(item -> Stream.of(
                                OrderRedisKeys.hotSkuStockKey(item.skuIdText()),
                                OrderRedisKeys.hotSkuPendingUserKey(item.skuIdText(), item.userId()),
                                OrderRedisKeys.hotSkuHoldKey(item.orderNo())
                        ))
                )
                .toList();
        List<String> args = Stream.concat(
                        Stream.of(String.valueOf(validItems.size())),
                        validItems.stream().flatMap(item -> Stream.of(
                                String.valueOf(item.userId()),
                                item.orderNo(),
                                String.valueOf(item.quantity()),
                                item.skuIdText()
                        ))
                )
                .toList();
        try {
            stringRedisTemplate.execute(batchCompensateScript, keys, args.toArray(new Object[0]));
        } catch (Exception e) {
            log.warn("[Order] hot SKU inventory batch release failed, size={}", validItems.size(), e);
            throw new IllegalStateException("Hot SKU inventory batch release failed.", e);
        }
    }

    private int resultCode(List<?> result) {
        Integer code = resultInt(result, 0);
        return code == null ? -1 : code;
    }

    private Integer resultInt(List<?> result, int index) {
        if (result == null || result.size() <= index || result.get(index) == null) {
            return null;
        }
        Object value = result.get(index);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private long pendingUserTtlMillis(OffsetDateTime now, OffsetDateTime expireAt) {
        long ttl = expireAt == null || now == null
                ? DEFAULT_PENDING_USER_TTL.toMillis()
                : Duration.between(now, expireAt).toMillis();
        return Math.max(1_000L, ttl + PENDING_USER_GRACE.toMillis());
    }

    private DefaultRedisScript<List> redisScript(String location) {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource(location)));
        script.setResultType(List.class);
        return script;
    }
}
