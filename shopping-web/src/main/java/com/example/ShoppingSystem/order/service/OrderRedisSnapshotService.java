package com.example.ShoppingSystem.order.service;

import com.example.ShoppingSystem.Utils.HybridIdCodec;
import com.example.ShoppingSystem.order.dto.OrderCreateResponse;
import com.example.ShoppingSystem.order.redis.OrderRedisKeys;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class OrderRedisSnapshotService {

    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<Map<String, Object>>> ITEM_LIST_TYPE = new TypeReference<>() {
    };

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final DefaultRedisScript<List> createScript;
    private final DefaultRedisScript<List> cancelScript;
    private final DefaultRedisScript<List> expireScript;
    private final DefaultRedisScript<List> finalizeClosingScript;
    private final DefaultRedisScript<List> closingCompensateBatchScript;
    private final DefaultRedisScript<List> markPaidScript;
    private final DefaultRedisScript<String> markPaidBatchScript;
    private final DefaultRedisScript<List> claimScript;
    private final DefaultRedisScript<List> completeCleanupScript;
    private final DefaultRedisScript<List> recoverScript;
    private final DefaultRedisScript<Long> unlockScript;
    private final DefaultRedisScript<Long> requeueScript;

    public OrderRedisSnapshotService(StringRedisTemplate stringRedisTemplate,
                                     ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.createScript = redisScript("lua/order_snapshot_create.lua");
        this.cancelScript = redisScript("lua/order_snapshot_cancel.lua");
        this.expireScript = redisScript("lua/order_snapshot_expire.lua");
        this.finalizeClosingScript = redisScript("lua/order_snapshot_finalize_closing.lua");
        this.closingCompensateBatchScript = redisScript("lua/order_closing_compensate_batch.lua");
        this.markPaidScript = redisScript("lua/order_snapshot_mark_paid.lua");
        this.markPaidBatchScript = stringRedisScript("lua/order_snapshot_mark_paid_batch.lua");
        this.claimScript = redisScript("lua/order_persist_claim.lua");
        this.completeCleanupScript = redisScript("lua/order_persist_complete_cleanup.lua");
        this.recoverScript = redisScript("lua/order_persist_recover.lua");
        this.unlockScript = longRedisScript("lua/order_persist_unlock.lua");
        this.requeueScript = longRedisScript("lua/order_persist_requeue.lua");
    }

    public void saveCreatedOrder(OrderCreateContext context,
                                 LockedOrderCoupon lockedCoupon,
                                 BigDecimal totalAmount,
                                 BigDecimal discountAmount,
                                 BigDecimal payAmount,
                                 long requiredPoints) {
        Map<String, Object> order = orderMap(context, lockedCoupon, totalAmount, discountAmount, payAmount, requiredPoints);
        List<Map<String, Object>> items = List.of(itemMap(context, totalAmount));
        try {
            stringRedisTemplate.execute(
                    createScript,
                    List.of(
                            OrderRedisKeys.orderDetailKey(context.orderNo()),
                            OrderRedisKeys.orderItemKey(context.orderNo()),
                            OrderRedisKeys.userOrderKey(context.userId()),
                            OrderRedisKeys.ORDER_EXPIRE_ZSET_KEY,
                            OrderRedisKeys.ORDER_ALL_ZSET_KEY
                    ),
                    objectMapper.writeValueAsString(order),
                    objectMapper.writeValueAsString(items),
                    context.orderNo(),
                    String.valueOf(context.now().toInstant().toEpochMilli()),
                    String.valueOf(context.expireAt().toInstant().toEpochMilli())
            );
        } catch (JsonProcessingException e) {
            throw new OrderServiceException("ORDER_REDIS_PAYLOAD_INVALID", "Order Redis payload is invalid.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public Optional<OrderRedisSnapshot> findSnapshot(String orderNo) {
        String normalizedOrderNo = normalizeOrderNo(orderNo);
        if (normalizedOrderNo.isEmpty()) {
            return Optional.empty();
        }
        List<String> values = stringRedisTemplate.opsForValue().multiGet(List.of(
                OrderRedisKeys.orderDetailKey(normalizedOrderNo),
                OrderRedisKeys.orderItemKey(normalizedOrderNo)
        ));
        if (values == null || values.isEmpty() || values.get(0) == null || values.get(0).isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new OrderRedisSnapshot(readMap(values.get(0)), readItems(values.size() > 1 ? values.get(1) : null)));
    }

    public Optional<OrderRedisSnapshot> findSnapshotForUser(String orderNo, Long userId) {
        return findSnapshot(orderNo)
                .filter(snapshot -> Objects.equals(OrderRowMapper.longValue(snapshot.order(), "userId"), userId));
    }

    public Optional<OrderCreateResponse> findCreateResponse(Long userId, String orderNo) {
        return findSnapshotForUser(orderNo, userId).map(snapshot -> new OrderCreateResponse(
                OrderRowMapper.text(snapshot.order(), "orderNo"),
                OrderRowMapper.text(snapshot.order(), "status"),
                OrderRowMapper.offsetDateTime(snapshot.order(), "expireAt"),
                OrderAmountCalculator.money(OrderRowMapper.decimal(snapshot.order(), "payAmountYuan"))
        ));
    }

    public List<OrderRedisSnapshot> listUserSnapshots(Long userId, int limit) {
        if (userId == null || limit <= 0) {
            return List.of();
        }
        Collection<String> orderNos = stringRedisTemplate.opsForZSet()
                .reverseRange(OrderRedisKeys.userOrderKey(userId), 0, limit - 1L);
        if (orderNos == null || orderNos.isEmpty()) {
            return List.of();
        }
        List<OrderRedisSnapshot> snapshots = loadSnapshots(new ArrayList<>(orderNos));
        return snapshots.stream()
                .filter(snapshot -> Objects.equals(OrderRowMapper.longValue(snapshot.order(), "userId"), userId))
                .sorted(Comparator.comparing(
                        snapshot -> OrderRowMapper.offsetDateTime(snapshot.order(), "createdAt"),
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .toList();
    }

    public List<OrderRedisSnapshot> listAllSnapshots(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        Collection<String> orderNos = stringRedisTemplate.opsForZSet()
                .reverseRange(OrderRedisKeys.ORDER_ALL_ZSET_KEY, 0, limit - 1L);
        if (orderNos == null || orderNos.isEmpty()) {
            return List.of();
        }
        return loadSnapshots(new ArrayList<>(orderNos)).stream()
                .sorted(Comparator.comparing(
                        snapshot -> OrderRowMapper.offsetDateTime(snapshot.order(), "createdAt"),
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .toList();
    }

    public OrderRedisStateChangeResult cancelPending(Long userId, String orderNo, OffsetDateTime now) {
        List<?> result = stringRedisTemplate.execute(
                cancelScript,
                List.of(
                        OrderRedisKeys.orderDetailKey(orderNo),
                        OrderRedisKeys.orderItemKey(orderNo),
                        OrderRedisKeys.ORDER_PERSIST_DIRTY_ZSET_KEY
                ),
                String.valueOf(userId),
                now.toString(),
                String.valueOf(now.toInstant().toEpochMilli()),
                orderNo
        );
        return stateChangeResult(result, "ORDER_CANCEL");
    }

    public OrderRedisStateChangeResult startClosingExpired(String orderNo,
                                                           OffsetDateTime now,
                                                           OffsetDateTime closingDeadline) {
        List<?> result = stringRedisTemplate.execute(
                expireScript,
                List.of(
                        OrderRedisKeys.orderDetailKey(orderNo),
                        OrderRedisKeys.ORDER_EXPIRE_ZSET_KEY,
                        OrderRedisKeys.ORDER_CLOSING_ZSET_KEY
                ),
                now.toString(),
                String.valueOf(now.toInstant().toEpochMilli()),
                orderNo,
                closingDeadline.toString(),
                String.valueOf(closingDeadline.toInstant().toEpochMilli())
        );
        return stateChangeResult(result, "ORDER_EXPIRE");
    }

    public OrderRedisStateChangeResult finalizeClosing(String orderNo, OffsetDateTime now) {
        List<?> result = stringRedisTemplate.execute(
                finalizeClosingScript,
                List.of(
                        OrderRedisKeys.orderDetailKey(orderNo),
                        OrderRedisKeys.orderItemKey(orderNo),
                        OrderRedisKeys.ORDER_PERSIST_DIRTY_ZSET_KEY,
                        OrderRedisKeys.ORDER_CLOSING_ZSET_KEY
                ),
                now.toString(),
                String.valueOf(now.toInstant().toEpochMilli()),
                orderNo
        );
        return stateChangeResult(result, "ORDER_FINALIZE_CLOSING");
    }

    public OrderClosingCompensateBatchResult compensateDueClosing(OffsetDateTime now, int batchSize) {
        OffsetDateTime runAt = now == null ? OffsetDateTime.now() : now;
        int limit = Math.max(1, batchSize);
        List<?> result = stringRedisTemplate.execute(
                closingCompensateBatchScript,
                List.of(
                        OrderRedisKeys.ORDER_CLOSING_ZSET_KEY,
                        OrderRedisKeys.ORDER_PERSIST_DIRTY_ZSET_KEY
                ),
                runAt.toString(),
                String.valueOf(runAt.toInstant().toEpochMilli()),
                String.valueOf(limit),
                OrderRedisKeys.orderDetailKey(""),
                OrderRedisKeys.orderItemKey("")
        );
        return closingCompensateResult(result);
    }

    public OrderRedisStateChangeResult markPaid(String orderNo,
                                                OffsetDateTime paidAt,
                                                String externalTradeNo) {
        return markPaid(orderNo, paidAt, externalTradeNo, null, true);
    }

    public OrderRedisStateChangeResult markPendingPaidForUser(String orderNo,
                                                              Long userId,
                                                              OffsetDateTime paidAt,
                                                              String externalTradeNo) {
        return markPaid(orderNo, paidAt, externalTradeNo, userId, false);
    }

    private OrderRedisStateChangeResult markPaid(String orderNo,
                                                 OffsetDateTime paidAt,
                                                 String externalTradeNo,
                                                 Long expectedUserId,
                                                 boolean allowClosing) {
        List<?> result = stringRedisTemplate.execute(
                markPaidScript,
                List.of(
                        OrderRedisKeys.orderDetailKey(orderNo),
                        OrderRedisKeys.ORDER_PERSIST_DIRTY_ZSET_KEY,
                        OrderRedisKeys.ORDER_EXPIRE_ZSET_KEY,
                        OrderRedisKeys.ORDER_CLOSING_ZSET_KEY
                ),
                paidAt.toString(),
                String.valueOf(paidAt.toInstant().toEpochMilli()),
                orderNo,
                externalTradeNo == null ? "" : externalTradeNo.trim(),
                expectedUserId == null ? "" : String.valueOf(expectedUserId),
                allowClosing ? "1" : "0"
        );
        return paymentStateChangeResult(result);
    }

    public List<Map<String, Object>> markPaidBatch(List<PaymentCallbackEvent> callbacks) {
        if (callbacks == null || callbacks.isEmpty()) {
            return List.of();
        }
        List<String> keys = new ArrayList<>(3 + callbacks.size());
        keys.add(OrderRedisKeys.ORDER_PERSIST_DIRTY_ZSET_KEY);
        keys.add(OrderRedisKeys.ORDER_EXPIRE_ZSET_KEY);
        keys.add(OrderRedisKeys.ORDER_CLOSING_ZSET_KEY);
        keys.addAll(callbacks.stream()
                .map(PaymentCallbackEvent::orderNo)
                .map(OrderRedisKeys::orderDetailKey)
                .toList());
        List<Map<String, Object>> payload = callbacks.stream()
                .map(this::paymentCallbackPayload)
                .toList();
        try {
            String json = stringRedisTemplate.execute(
                    markPaidBatchScript,
                    keys,
                    objectMapper.writeValueAsString(payload)
            );
            if (json == null || json.isBlank()) {
                return List.of();
            }
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {
            });
        } catch (JsonProcessingException e) {
            throw new OrderServiceException("ORDER_REDIS_PAYLOAD_INVALID", "Order Redis payload is invalid.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private Map<String, Object> paymentCallbackPayload(PaymentCallbackEvent callback) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("callbackNo", callback.callbackNo());
        row.put("orderNo", callback.orderNo());
        row.put("externalTradeNo", callback.externalTradeNo() == null ? "" : callback.externalTradeNo().trim());
        row.put("paymentProvider", callback.paymentProvider() == null ? "" : callback.paymentProvider().trim());
        row.put("paidAt", callback.paidAt().toString());
        row.put("paidAtEpochMs", callback.paidAt().toInstant().toEpochMilli());
        row.put("paidAmountYuan", callback.paidAmountYuan());
        return row;
    }

    public List<String> claimDirty(int batchSize, long nowEpochMs) {
        List<?> result = stringRedisTemplate.execute(
                claimScript,
                List.of(OrderRedisKeys.ORDER_PERSIST_DIRTY_ZSET_KEY, OrderRedisKeys.ORDER_PERSIST_PROCESSING_ZSET_KEY),
                String.valueOf(nowEpochMs),
                String.valueOf(batchSize)
        );
        return textList(result);
    }

    public List<String> recoverTimedOutProcessing(Duration timeout, int batchSize, long nowEpochMs) {
        long cutoff = nowEpochMs - Math.max(1L, timeout.toMillis());
        List<?> result = stringRedisTemplate.execute(
                recoverScript,
                List.of(OrderRedisKeys.ORDER_PERSIST_PROCESSING_ZSET_KEY, OrderRedisKeys.ORDER_PERSIST_DIRTY_ZSET_KEY),
                String.valueOf(cutoff),
                String.valueOf(nowEpochMs),
                String.valueOf(batchSize),
                OrderRedisKeys.orderDetailKey("")
        );
        return textList(result);
    }

    public boolean acquirePersistLock(String lockValue, Duration ttl) {
        return acquireLock(OrderRedisKeys.ORDER_PERSIST_LOCK_KEY, lockValue, ttl);
    }

    public void releasePersistLock(String lockValue) {
        releaseLock(OrderRedisKeys.ORDER_PERSIST_LOCK_KEY, lockValue);
    }

    public boolean acquireClosingCompensateLock(String lockValue, Duration ttl) {
        return acquireLock(OrderRedisKeys.ORDER_CLOSING_COMPENSATE_LOCK_KEY, lockValue, ttl);
    }

    public void releaseClosingCompensateLock(String lockValue) {
        releaseLock(OrderRedisKeys.ORDER_CLOSING_COMPENSATE_LOCK_KEY, lockValue);
    }

    private boolean acquireLock(String key, String lockValue, Duration ttl) {
        if (key == null || key.isBlank() || lockValue == null || lockValue.isBlank()) {
            return false;
        }
        Duration lockTtl = ttl == null || ttl.isNegative() || ttl.isZero() ? Duration.ofSeconds(30) : ttl;
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(
                key,
                lockValue,
                lockTtl
        );
        return Boolean.TRUE.equals(acquired);
    }

    private void releaseLock(String key, String lockValue) {
        if (lockValue == null || lockValue.isBlank()) {
            return;
        }
        stringRedisTemplate.execute(
                unlockScript,
                List.of(key),
                lockValue
        );
    }

    public List<OrderRedisSnapshot> loadSnapshots(List<String> orderNos) {
        if (orderNos == null || orderNos.isEmpty()) {
            return List.of();
        }
        List<String> detailKeys = orderNos.stream().map(OrderRedisKeys::orderDetailKey).toList();
        List<String> itemKeys = orderNos.stream().map(OrderRedisKeys::orderItemKey).toList();
        List<String> detailValues = stringRedisTemplate.opsForValue().multiGet(detailKeys);
        List<String> itemValues = stringRedisTemplate.opsForValue().multiGet(itemKeys);
        List<OrderRedisSnapshot> snapshots = new ArrayList<>(orderNos.size());
        for (int index = 0; index < orderNos.size(); index += 1) {
            String detailJson = valueAt(detailValues, index);
            if (detailJson == null || detailJson.isBlank()) {
                continue;
            }
            snapshots.add(new OrderRedisSnapshot(
                    readMap(detailJson),
                    readItems(valueAt(itemValues, index))
            ));
        }
        return snapshots;
    }

    public void completePersistedAndCleanup(Collection<String> orderNos, List<OrderRedisSnapshot> snapshots) {
        if (orderNos == null || orderNos.isEmpty()) {
            return;
        }
        List<OrderRedisSnapshot> terminalSnapshots = snapshots == null
                ? List.of()
                : snapshots.stream()
                .filter(snapshot -> isTerminal(OrderRowMapper.text(snapshot.order(), "status")))
                .toList();
        List<Object> args = new ArrayList<>(2 + orderNos.size() + terminalSnapshots.size() * 4);
        args.add(String.valueOf(orderNos.size()));
        args.addAll(orderNos);
        args.add(String.valueOf(terminalSnapshots.size()));
        for (OrderRedisSnapshot snapshot : terminalSnapshots) {
            String orderNo = OrderRowMapper.text(snapshot.order(), "orderNo");
            Long userId = OrderRowMapper.longValue(snapshot.order(), "userId");
            args.add(orderNo);
            args.add(OrderRedisKeys.orderDetailKey(orderNo));
            args.add(OrderRedisKeys.orderItemKey(orderNo));
            args.add(userId == null ? "" : OrderRedisKeys.userOrderKey(userId));
        }
        stringRedisTemplate.execute(
                completeCleanupScript,
                List.of(
                        OrderRedisKeys.ORDER_PERSIST_DIRTY_ZSET_KEY,
                        OrderRedisKeys.ORDER_PERSIST_PROCESSING_ZSET_KEY,
                        OrderRedisKeys.ORDER_EXPIRE_ZSET_KEY,
                        OrderRedisKeys.ORDER_CLOSING_ZSET_KEY,
                        OrderRedisKeys.ORDER_ALL_ZSET_KEY
                ),
                args.toArray(new Object[0])
        );
    }

    public void requeueProcessing(Collection<String> orderNos,
                                  List<OrderRedisSnapshot> snapshots,
                                  long fallbackEpochMs) {
        if (orderNos == null || orderNos.isEmpty()) {
            return;
        }
        Map<String, Long> scores = createdAtScores(snapshots);
        List<Object> args = new ArrayList<>(1 + orderNos.size() * 2);
        args.add(String.valueOf(orderNos.size()));
        for (String orderNo : orderNos) {
            args.add(orderNo);
            args.add(String.valueOf(scores.getOrDefault(orderNo, fallbackEpochMs)));
        }
        stringRedisTemplate.execute(
                requeueScript,
                List.of(OrderRedisKeys.ORDER_PERSIST_PROCESSING_ZSET_KEY, OrderRedisKeys.ORDER_PERSIST_DIRTY_ZSET_KEY),
                args.toArray(new Object[0])
        );
    }

    private Map<String, Object> orderMap(OrderCreateContext context,
                                         LockedOrderCoupon lockedCoupon,
                                         BigDecimal totalAmount,
                                         BigDecimal discountAmount,
                                         BigDecimal payAmount,
                                         long requiredPoints) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("orderNo", context.orderNo());
        row.put("userId", String.valueOf(context.userId()));
        row.put("status", OrderStatus.PENDING_PAYMENT);
        row.put("totalAmountYuan", moneyText(totalAmount));
        row.put("discountAmountYuan", moneyText(discountAmount));
        row.put("payAmountYuan", moneyText(payAmount));
        row.put("requiredPoints", Math.max(0L, requiredPoints));
        row.put("paymentType", OrderPaymentType.UNPAID);
        row.put("usedPoints", 0L);
        row.put("userCouponId", lockedCoupon == null ? null : lockedCoupon.userCouponIdText());
        row.put("userCouponIdHex", lockedCoupon == null ? null : HybridIdCodec.toHex(lockedCoupon.userCouponId()));
        row.put("idempotencyKey", context.idempotencyKey());
        putTime(row, "expireAt", context.expireAt());
        row.put("paidAt", null);
        row.put("paidAtEpochMs", null);
        row.put("closingAt", null);
        row.put("closingAtEpochMs", null);
        row.put("closingDeadlineAt", null);
        row.put("closingDeadlineAtEpochMs", null);
        row.put("cancelledAt", null);
        row.put("cancelledAtEpochMs", null);
        row.put("closedAt", null);
        row.put("closedAtEpochMs", null);
        putTime(row, "createdAt", context.now());
        putTime(row, "updatedAt", context.now());
        row.put("version", 1L);
        row.put("inventoryType", context.inventoryType().name());
        return row;
    }

    private Map<String, Object> itemMap(OrderCreateContext context, BigDecimal lineAmount) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("orderNo", context.orderNo());
        row.put("userId", String.valueOf(context.userId()));
        row.put("spuId", context.sku().spuId());
        row.put("skuId", HybridIdCodec.toHex(context.sku().skuId()));
        row.put("skuCode", context.sku().skuCode());
        row.put("skuName", context.sku().skuName());
        row.put("specJson", context.sku().specJson());
        row.put("skuImageUrl", context.sku().skuImageUrl());
        row.put("quantity", context.quantity());
        row.put("salePriceYuan", moneyText(context.sku().priceYuan()));
        row.put("lineAmountYuan", moneyText(lineAmount));
        row.put("pointExchangeEnabled", context.sku().pointExchangeEnabled());
        row.put("pointExchangePoints", pointExchangePoints(context.sku()));
        row.put("linePoints", linePoints(context.sku(), context.quantity()));
        row.put("hotSku", context.sku().hotSku());
        putTime(row, "createdAt", context.now());
        return row;
    }

    private long pointExchangePoints(OrderSkuSnapshot sku) {
        Long points = sku == null ? null : sku.pointExchangePoints();
        return points == null || points < 0L ? 0L : points;
    }

    private long linePoints(OrderSkuSnapshot sku, int quantity) {
        if (sku == null || !sku.pointExchangeEnabled()) {
            return 0L;
        }
        return pointExchangePoints(sku) * Math.max(0, quantity);
    }

    private void putTime(Map<String, Object> row, String key, OffsetDateTime value) {
        row.put(key, value == null ? null : value.toString());
        row.put(key + "EpochMs", value == null ? null : value.toInstant().toEpochMilli());
    }

    private String moneyText(BigDecimal value) {
        return OrderAmountCalculator.money(value).toPlainString();
    }

    private OrderRedisStateChangeResult stateChangeResult(List<?> result, String prefix) {
        int code = resultCode(result);
        if (code == 0) {
            return OrderRedisStateChangeResult.changed(
                    readMap(String.valueOf(result.get(1))),
                    readItems(String.valueOf(result.get(2)))
            );
        }
        return OrderRedisStateChangeResult.unchanged(prefix + "_" + code);
    }

    private OrderRedisStateChangeResult paymentStateChangeResult(List<?> result) {
        int code = resultCode(result);
        if (code == 0) {
            return OrderRedisStateChangeResult.changed(
                    readMap(String.valueOf(result.get(1))),
                    readItems(String.valueOf(result.get(2)))
            );
        }
        if (code == 4) {
            return OrderRedisStateChangeResult.changed(
                    "ORDER_PAY_4",
                    readMap(String.valueOf(result.get(1))),
                    readItems(String.valueOf(result.get(2)))
            );
        }
        return OrderRedisStateChangeResult.unchanged("ORDER_PAY_" + code);
    }

    private OrderClosingCompensateBatchResult closingCompensateResult(List<?> result) {
        if (result == null || result.isEmpty()) {
            return OrderClosingCompensateBatchResult.empty();
        }
        int claimedCount = intAt(result, 0);
        int changedCount = intAt(result, 1);
        int staleMissingCount = intAt(result, 2);
        int staleTerminalCount = intAt(result, 3);
        int skippedNonClosingCount = intAt(result, 4);
        int skippedNotDueCount = intAt(result, 5);
        int expectedSize = 6 + changedCount * 3;
        if (result.size() < expectedSize) {
            throw new OrderServiceException("ORDER_REDIS_PAYLOAD_INVALID", "Order Redis payload is invalid.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        List<OrderRedisSnapshot> changedSnapshots = new ArrayList<>(changedCount);
        for (int index = 0; index < changedCount; index += 1) {
            int offset = 6 + index * 3;
            String orderJson = stringAt(result, offset + 1);
            String itemJson = stringAt(result, offset + 2);
            if (!orderJson.isBlank()) {
                changedSnapshots.add(new OrderRedisSnapshot(readMap(orderJson), readItems(itemJson)));
            }
        }
        return new OrderClosingCompensateBatchResult(
                claimedCount,
                changedCount,
                staleMissingCount,
                staleTerminalCount,
                skippedNonClosingCount,
                skippedNotDueCount,
                List.copyOf(changedSnapshots)
        );
    }

    private Map<String, Object> readMap(String json) {
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            throw new OrderServiceException("ORDER_REDIS_PAYLOAD_INVALID", "Order Redis payload is invalid.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private List<Map<String, Object>> readItems(String json) {
        String value = json == null || json.isBlank() ? "[]" : json;
        try {
            return objectMapper.readValue(value, ITEM_LIST_TYPE);
        } catch (Exception e) {
            throw new OrderServiceException("ORDER_REDIS_PAYLOAD_INVALID", "Order item Redis payload is invalid.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private int resultCode(List<?> result) {
        if (result == null || result.isEmpty() || result.get(0) == null) {
            return -1;
        }
        Object value = result.get(0);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private int intAt(List<?> result, int index) {
        if (result == null || result.size() <= index || result.get(index) == null) {
            return 0;
        }
        Object value = result.get(index);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String stringAt(List<?> result, int index) {
        if (result == null || result.size() <= index || result.get(index) == null) {
            return "";
        }
        return String.valueOf(result.get(index));
    }

    private List<String> textList(List<?> result) {
        if (result == null || result.isEmpty()) {
            return List.of();
        }
        return result.stream()
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .filter(value -> !value.isBlank())
                .toList();
    }

    private String valueAt(List<String> values, int index) {
        return values == null || values.size() <= index ? null : values.get(index);
    }

    private boolean isTerminal(String status) {
        return OrderStatus.PAID.equals(status) || OrderStatus.CANCELLED.equals(status) || OrderStatus.CLOSED.equals(status);
    }

    private Map<String, Long> createdAtScores(List<OrderRedisSnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return Map.of();
        }
        Map<String, Long> scores = new HashMap<>(snapshots.size());
        for (OrderRedisSnapshot snapshot : snapshots) {
            String orderNo = OrderRowMapper.text(snapshot.order(), "orderNo");
            Long createdAt = OrderRowMapper.longValue(snapshot.order(), "createdAtEpochMs");
            if (!orderNo.isBlank() && createdAt != null) {
                scores.put(orderNo, createdAt);
            }
        }
        return scores;
    }

    private String normalizeOrderNo(String orderNo) {
        return orderNo == null ? "" : orderNo.trim();
    }

    private DefaultRedisScript<List> redisScript(String location) {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource(location)));
        script.setResultType(List.class);
        return script;
    }

    private DefaultRedisScript<Long> longRedisScript(String location) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource(location)));
        script.setResultType(Long.class);
        return script;
    }

    private DefaultRedisScript<String> stringRedisScript(String location) {
        DefaultRedisScript<String> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource(location)));
        script.setResultType(String.class);
        return script;
    }
}
