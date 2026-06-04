package com.example.ShoppingSystem.order.service;

import com.example.ShoppingSystem.Utils.HybridIdCodec;
import com.example.ShoppingSystem.Utils.HybridSemaphoreIdWorker;
import com.example.ShoppingSystem.mapper.order.OrderCardSecretDeliveryMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class OrderCardSecretDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(OrderCardSecretDeliveryService.class);

    private static final long LOCK_WAIT_SECONDS = 2L;
    private static final long LOCK_LEASE_SECONDS = 30L;
    private static final String ORDER_LOCK_PREFIX = "card-secret:deliver:order:";
    private static final String SKU_LOCK_PREFIX = "card-secret:deliver:sku:";

    private final OrderCardSecretDeliveryMapper deliveryMapper;
    private final OrderRedisSnapshotService orderRedisSnapshotService;
    private final RedissonClient redissonClient;
    private final HybridSemaphoreIdWorker hybridSemaphoreIdWorker;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public OrderCardSecretDeliveryService(OrderCardSecretDeliveryMapper deliveryMapper,
                                          OrderRedisSnapshotService orderRedisSnapshotService,
                                          RedissonClient redissonClient,
                                          HybridSemaphoreIdWorker hybridSemaphoreIdWorker,
                                          ObjectMapper objectMapper,
                                          TransactionTemplate transactionTemplate) {
        this.deliveryMapper = deliveryMapper;
        this.orderRedisSnapshotService = orderRedisSnapshotService;
        this.redissonClient = redissonClient;
        this.hybridSemaphoreIdWorker = hybridSemaphoreIdWorker;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
    }

    public DeliveryBatchResult deliverPaidOrder(String orderNo,
                                                Long userId,
                                                List<Map<String, Object>> items) {
        String normalizedOrderNo = normalizeOrderNo(orderNo);
        if (normalizedOrderNo.isBlank() || userId == null || userId <= 0) {
            return DeliveryBatchResult.empty();
        }
        List<Map<String, Object>> itemRows = normalizeProvidedItems(normalizedOrderNo, userId, items);
        if (itemRows.isEmpty()) {
            itemRows = loadRedisItemRows(List.of(orderRow(normalizedOrderNo, userId)));
        }
        if (itemRows.isEmpty()) {
            itemRows = deliveryMapper.listPaidOrderItemsForDelivery(toJson(List.of(orderRow(normalizedOrderNo, userId))));
        }
        return deliverItemRows(itemRows);
    }

    public DeliveryBatchResult deliverPaidOrdersFromRows(List<Map<String, Object>> paidRows) {
        List<Map<String, Object>> orders = normalizePaidOrderRows(paidRows);
        if (orders.isEmpty()) {
            return DeliveryBatchResult.empty();
        }
        List<Map<String, Object>> itemRows = loadRedisItemRows(orders);
        Set<String> loadedKeys = itemRows.stream()
                .map(row -> orderKey(OrderRowMapper.text(row, "orderNo"), OrderRowMapper.longValue(row, "userId")))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<Map<String, Object>> missingOrders = orders.stream()
                .filter(row -> !loadedKeys.contains(orderKey(OrderRowMapper.text(row, "orderNo"), OrderRowMapper.longValue(row, "userId"))))
                .toList();
        if (!missingOrders.isEmpty()) {
            List<Map<String, Object>> mergedRows = new ArrayList<>(itemRows);
            mergedRows.addAll(deliveryMapper.listPaidOrderItemsForDelivery(toJson(missingOrders)));
            itemRows = mergedRows;
        }
        return deliverItemRows(itemRows);
    }

    private DeliveryBatchResult deliverItemRows(List<Map<String, Object>> itemRows) {
        List<DeliveryUnit> units = buildUnits(itemRows);
        if (units.isEmpty()) {
            return DeliveryBatchResult.empty();
        }
        List<String> lockKeys = deliveryLockKeys(units);
        RLock lock = multiLock(lockKeys);
        boolean locked = false;
        try {
            locked = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                log.warn("[OrderCardSecret] delivery skipped because lock is busy, orders={}, units={}",
                        orderCount(units), units.size());
                return new DeliveryBatchResult(units.size(), 0, units.size(), true);
            }
            List<Map<String, Object>> result = transactionTemplate.execute(status ->
                    deliveryMapper.deliverPaidOrderCardSecrets(toJson(units.stream().map(this::unitRow).toList()))
            );
            DeliveryBatchResult summary = summarize(units.size(), result == null ? List.of() : result);
            if (summary.shortageCount() > 0) {
                log.warn("[OrderCardSecret] delivery shortage, units={}, delivered={}, shortage={}",
                        summary.requiredCount(), summary.deliveredCount(), summary.shortageCount());
            } else {
                log.info("[OrderCardSecret] delivery finished, units={}, delivered={}",
                        summary.requiredCount(), summary.deliveredCount());
            }
            return summary;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("[OrderCardSecret] delivery interrupted, orders={}, units={}", orderCount(units), units.size());
            return new DeliveryBatchResult(units.size(), 0, units.size(), true);
        } catch (Exception ex) {
            log.warn("[OrderCardSecret] delivery failed, orders={}, units={}", orderCount(units), units.size(), ex);
            return new DeliveryBatchResult(units.size(), 0, units.size(), false);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private List<Map<String, Object>> normalizeProvidedItems(String orderNo,
                                                             Long userId,
                                                             List<Map<String, Object>> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> rows = new ArrayList<>(items.size());
        for (Map<String, Object> item : items) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("orderNo", orderNo);
            row.put("userId", userId);
            row.put("orderStatus", OrderStatus.PAID);
            row.put("skuId", OrderRowMapper.text(item, "skuId"));
            row.put("skuName", OrderRowMapper.text(item, "skuName"));
            row.put("quantity", OrderRowMapper.intValue(item, "quantity", 0));
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> normalizePaidOrderRows(List<Map<String, Object>> paidRows) {
        if (paidRows == null || paidRows.isEmpty()) {
            return List.of();
        }
        Map<String, Map<String, Object>> unique = new LinkedHashMap<>();
        for (Map<String, Object> row : paidRows) {
            String orderNo = normalizeOrderNo(OrderRowMapper.text(row, "orderNo"));
            Long userId = OrderRowMapper.longValue(row, "userId");
            if (orderNo.isBlank() || userId == null || userId <= 0) {
                continue;
            }
            unique.putIfAbsent(orderKey(orderNo, userId), orderRow(orderNo, userId));
        }
        return List.copyOf(unique.values());
    }

    private List<Map<String, Object>> loadRedisItemRows(List<Map<String, Object>> orders) {
        List<String> orderNos = orders.stream()
                .map(row -> OrderRowMapper.text(row, "orderNo"))
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
        if (orderNos.isEmpty()) {
            return List.of();
        }
        Map<String, Long> expectedUserByOrderNo = orders.stream()
                .collect(Collectors.toMap(
                        row -> OrderRowMapper.text(row, "orderNo"),
                        row -> OrderRowMapper.longValue(row, "userId"),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (OrderRedisSnapshot snapshot : orderRedisSnapshotService.loadSnapshots(orderNos)) {
            Map<String, Object> order = snapshot.order();
            String orderNo = OrderRowMapper.text(order, "orderNo");
            Long userId = OrderRowMapper.longValue(order, "userId");
            if (!OrderStatus.PAID.equals(OrderRowMapper.text(order, "status"))
                    || !expectedUserByOrderNo.containsKey(orderNo)
                    || !expectedUserByOrderNo.get(orderNo).equals(userId)) {
                continue;
            }
            rows.addAll(normalizeProvidedItems(orderNo, userId, snapshot.items()));
        }
        return rows;
    }

    private List<DeliveryUnit> buildUnits(List<Map<String, Object>> itemRows) {
        if (itemRows == null || itemRows.isEmpty()) {
            return List.of();
        }
        List<DeliveryUnit> units = new ArrayList<>();
        for (Map<String, Object> row : itemRows) {
            if (!OrderStatus.PAID.equals(OrderRowMapper.text(row, "orderStatus"))) {
                continue;
            }
            String orderNo = normalizeOrderNo(OrderRowMapper.text(row, "orderNo"));
            Long userId = OrderRowMapper.longValue(row, "userId");
            String skuIdHex = normalizeHex(OrderRowMapper.text(row, "skuId"));
            int quantity = OrderRowMapper.intValue(row, "quantity", 0);
            if (orderNo.isBlank() || userId == null || userId <= 0 || skuIdHex.isBlank() || quantity <= 0) {
                continue;
            }
            String skuName = OrderRowMapper.text(row, "skuName");
            for (int index = 1; index <= quantity; index += 1) {
                units.add(new DeliveryUnit(
                        HybridIdCodec.toHex(hybridSemaphoreIdWorker.nextId()),
                        orderNo,
                        userId,
                        skuIdHex,
                        skuName,
                        index
                ));
            }
        }
        return units;
    }

    private List<String> deliveryLockKeys(List<DeliveryUnit> units) {
        Set<String> keys = new LinkedHashSet<>();
        for (DeliveryUnit unit : units) {
            keys.add(ORDER_LOCK_PREFIX + unit.orderNo());
            keys.add(SKU_LOCK_PREFIX + HybridIdCodec.hexToBase62(unit.skuIdHex()));
        }
        return keys.stream().sorted().toList();
    }

    private RLock multiLock(List<String> lockKeys) {
        RLock[] locks = lockKeys.stream()
                .map(redissonClient::getLock)
                .toArray(RLock[]::new);
        if (locks.length == 1) {
            return locks[0];
        }
        return redissonClient.getMultiLock(locks);
    }

    private DeliveryBatchResult summarize(int fallbackRequiredCount, List<Map<String, Object>> result) {
        if (result.isEmpty()) {
            return new DeliveryBatchResult(fallbackRequiredCount, 0, fallbackRequiredCount, false);
        }
        int required = 0;
        int delivered = 0;
        int shortage = 0;
        for (Map<String, Object> row : result) {
            required += OrderRowMapper.intValue(row, "requiredCount", 0);
            delivered += OrderRowMapper.intValue(row, "deliveredCount", 0);
            shortage += OrderRowMapper.intValue(row, "shortageCount", 0);
        }
        return new DeliveryBatchResult(required, delivered, shortage, false);
    }

    private int orderCount(List<DeliveryUnit> units) {
        return (int) units.stream()
                .map(unit -> orderKey(unit.orderNo(), unit.userId()))
                .distinct()
                .count();
    }

    private Map<String, Object> unitRow(DeliveryUnit unit) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("delivery_id_hex", unit.deliveryIdHex());
        row.put("order_no", unit.orderNo());
        row.put("user_id", unit.userId());
        row.put("sku_id_hex", unit.skuIdHex());
        row.put("sku_name", unit.skuName());
        row.put("unit_index", unit.unitIndex());
        return row;
    }

    private Map<String, Object> orderRow(String orderNo, Long userId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("order_no", orderNo);
        row.put("orderNo", orderNo);
        row.put("user_id", userId);
        row.put("userId", userId);
        return row;
    }

    private String orderKey(String orderNo, Long userId) {
        return (orderNo == null ? "" : orderNo.trim()) + ":" + (userId == null ? "" : userId);
    }

    private String normalizeOrderNo(String orderNo) {
        String value = orderNo == null ? "" : orderNo.trim();
        return value.length() > 64 ? "" : value;
    }

    private String normalizeHex(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.startsWith("\\x") || normalized.startsWith("\\X")) {
            normalized = normalized.substring(2);
        }
        return normalized.matches("^[0-9A-Fa-f]{32}$") ? normalized.toLowerCase() : "";
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new OrderServiceException(
                    "ORDER_CARD_SECRET_PAYLOAD_INVALID",
                    "Order card secret payload is invalid.",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    public record DeliveryBatchResult(int requiredCount,
                                      int deliveredCount,
                                      int shortageCount,
                                      boolean lockBusy) {
        static DeliveryBatchResult empty() {
            return new DeliveryBatchResult(0, 0, 0, false);
        }
    }

    private record DeliveryUnit(String deliveryIdHex,
                                String orderNo,
                                Long userId,
                                String skuIdHex,
                                String skuName,
                                int unitIndex) {
    }
}
