package com.example.ShoppingSystem.order.service;

import com.example.ShoppingSystem.mapper.order.OrderMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class OrderRedisPersistScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderRedisPersistScheduler.class);

    private final OrderRedisSnapshotService orderRedisSnapshotService;
    private final OrderMapper orderMapper;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final int batchSize;
    private final int maxBatchesPerRun;
    private final Duration processingTimeout;
    private final Duration persistLockTtl;

    public OrderRedisPersistScheduler(OrderRedisSnapshotService orderRedisSnapshotService,
                                      OrderMapper orderMapper,
                                      ObjectMapper objectMapper,
                                      TransactionTemplate transactionTemplate,
                                      @Value("${shopping.order.redis-persist-batch-size:100}") int batchSize,
                                      @Value("${shopping.order.redis-persist-max-batches-per-run:20}") int maxBatchesPerRun,
                                      @Value("${shopping.order.redis-persist-processing-timeout-ms:60000}") long processingTimeoutMs,
                                      @Value("${shopping.order.redis-persist-lock-ttl-ms:30000}") long persistLockTtlMs) {
        this.orderRedisSnapshotService = orderRedisSnapshotService;
        this.orderMapper = orderMapper;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
        this.batchSize = batchSize <= 0 ? 100 : batchSize;
        this.maxBatchesPerRun = Math.max(1, maxBatchesPerRun);
        this.processingTimeout = Duration.ofMillis(Math.max(1000L, processingTimeoutMs));
        this.persistLockTtl = Duration.ofMillis(Math.max(1000L, persistLockTtlMs));
    }

    @Scheduled(fixedDelayString = "${shopping.order.redis-persist-delay-ms:5000}")
    public void persistDirtyOrders() {
        String lockValue = UUID.randomUUID().toString();
        if (!orderRedisSnapshotService.acquirePersistLock(lockValue, persistLockTtl)) {
            return;
        }
        try {
            persistDirtyOrdersWithLock();
        } finally {
            orderRedisSnapshotService.releasePersistLock(lockValue);
        }
    }

    private void persistDirtyOrdersWithLock() {
        long nowMs = Instant.now().toEpochMilli();
        List<String> recovered = orderRedisSnapshotService.recoverTimedOutProcessing(processingTimeout, batchSize, nowMs);
        if (!recovered.isEmpty()) {
            log.info("[Order] recovered timed-out Redis persist orders, count={}", recovered.size());
        }

        int batchCount = 0;
        int totalClaimed = 0;
        int totalPersisted = 0;
        boolean failed = false;
        while (batchCount < maxBatchesPerRun) {
            List<String> orderNos = orderRedisSnapshotService.claimDirty(batchSize, nowMs);
            if (orderNos.isEmpty()) {
                break;
            }
            boolean shouldContinue = orderNos.size() >= batchSize;
            totalClaimed += orderNos.size();
            List<OrderRedisSnapshot> snapshots = orderRedisSnapshotService.loadSnapshots(orderNos);
            if (snapshots.isEmpty()) {
                orderRedisSnapshotService.completePersistedAndCleanup(orderNos, List.of());
                batchCount++;
                if (!shouldContinue) {
                    break;
                }
                continue;
            }

            BatchRange batchRange = batchRange(snapshots);
            try {
                persistSnapshots(snapshots);
                orderRedisSnapshotService.completePersistedAndCleanup(orderNos, snapshots);
                batchCount++;
                totalPersisted += snapshots.size();
                log.info(
                        "[Order] Redis order persist batch finished, batch={}, count={}, firstOrderNo={}, firstCreatedAtMs={}, lastOrderNo={}, lastCreatedAtMs={}",
                        batchCount,
                        snapshots.size(),
                        batchRange.firstOrderNo(),
                        batchRange.firstCreatedAtMs(),
                        batchRange.lastOrderNo(),
                        batchRange.lastCreatedAtMs()
                );
            } catch (Exception e) {
                failed = true;
                orderRedisSnapshotService.requeueProcessing(orderNos, snapshots, Instant.now().toEpochMilli());
                log.warn(
                        "[Order] Redis order persist batch failed, batch={}, count={}, firstOrderNo={}, firstCreatedAtMs={}, lastOrderNo={}, lastCreatedAtMs={}",
                        batchCount + 1,
                        orderNos.size(),
                        batchRange.firstOrderNo(),
                        batchRange.firstCreatedAtMs(),
                        batchRange.lastOrderNo(),
                        batchRange.lastCreatedAtMs(),
                        e
                );
                break;
            }
            if (!shouldContinue) {
                break;
            }
        }
        if (batchCount > 0 || failed) {
            log.info("[Order] Redis order persist run finished, batches={}, claimed={}, persisted={}, batchSize={}, maxBatches={}, failed={}",
                    batchCount, totalClaimed, totalPersisted, batchSize, maxBatchesPerRun, failed);
        }
    }

    private void persistSnapshots(List<OrderRedisSnapshot> snapshots) throws JsonProcessingException {
        List<OrderRedisSnapshot> persistableSnapshots = snapshots.stream()
                .filter(this::isPersistableSnapshot)
                .toList();
        if (persistableSnapshots.isEmpty()) {
            return;
        }
        List<Map<String, Object>> orderRows = new ArrayList<>(persistableSnapshots.size());
        List<Map<String, Object>> itemRows = new ArrayList<>();
        for (OrderRedisSnapshot snapshot : persistableSnapshots) {
            orderRows.add(orderRow(snapshot.order()));
            String orderNo = OrderRowMapper.text(snapshot.order(), "orderNo");
            for (Map<String, Object> item : snapshot.items()) {
                itemRows.add(itemRow(item, orderNo));
            }
        }
        String ordersJson = objectMapper.writeValueAsString(orderRows);
        String itemsJson = objectMapper.writeValueAsString(itemRows);
        transactionTemplate.executeWithoutResult(status -> {
            orderMapper.batchUpsertOrders(ordersJson);
            if (!itemRows.isEmpty()) {
                orderMapper.batchInsertOrderItems(itemsJson);
            }
        });
    }

    private boolean isPersistableSnapshot(OrderRedisSnapshot snapshot) {
        if (snapshot == null) {
            return false;
        }
        String status = OrderRowMapper.text(snapshot.order(), "status");
        return OrderStatus.PAID.equals(status)
                || OrderStatus.CANCELLED.equals(status)
                || OrderStatus.CLOSED.equals(status);
    }

    private Map<String, Object> orderRow(Map<String, Object> order) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("order_no", OrderRowMapper.text(order, "orderNo"));
        row.put("user_id", OrderRowMapper.longValue(order, "userId"));
        row.put("status", OrderRowMapper.text(order, "status"));
        row.put("total_amount_yuan", OrderAmountCalculator.money(OrderRowMapper.decimal(order, "totalAmountYuan")));
        row.put("discount_amount_yuan", OrderAmountCalculator.money(OrderRowMapper.decimal(order, "discountAmountYuan")));
        row.put("pay_amount_yuan", OrderAmountCalculator.money(OrderRowMapper.decimal(order, "payAmountYuan")));
        row.put("user_coupon_id_hex", OrderRowMapper.text(order, "userCouponIdHex"));
        row.put("idempotency_key", OrderRowMapper.text(order, "idempotencyKey"));
        row.put("expire_at_epoch_ms", OrderRowMapper.longValue(order, "expireAtEpochMs"));
        row.put("paid_at_epoch_ms", OrderRowMapper.longValue(order, "paidAtEpochMs"));
        row.put("closing_at_epoch_ms", OrderRowMapper.longValue(order, "closingAtEpochMs"));
        row.put("closing_deadline_at_epoch_ms", OrderRowMapper.longValue(order, "closingDeadlineAtEpochMs"));
        row.put("cancelled_at_epoch_ms", OrderRowMapper.longValue(order, "cancelledAtEpochMs"));
        row.put("closed_at_epoch_ms", OrderRowMapper.longValue(order, "closedAtEpochMs"));
        row.put("created_at_epoch_ms", OrderRowMapper.longValue(order, "createdAtEpochMs"));
        row.put("updated_at_epoch_ms", OrderRowMapper.longValue(order, "updatedAtEpochMs"));
        row.put("version", Math.max(1L, OrderRowMapper.longValue(order, "version") == null ? 1L : OrderRowMapper.longValue(order, "version")));
        return row;
    }

    private Map<String, Object> itemRow(Map<String, Object> item, String orderNo) {
        Map<String, Object> row = new LinkedHashMap<>();
        String itemOrderNo = OrderRowMapper.text(item, "orderNo");
        row.put("order_no", itemOrderNo.isBlank() ? orderNo : itemOrderNo);
        row.put("user_id", OrderRowMapper.longValue(item, "userId"));
        row.put("spu_id", OrderRowMapper.longValue(item, "spuId"));
        row.put("sku_id_hex", OrderRowMapper.text(item, "skuId"));
        row.put("sku_code", OrderRowMapper.text(item, "skuCode"));
        row.put("sku_name", OrderRowMapper.text(item, "skuName"));
        row.put("spec_json", OrderRowMapper.text(item, "specJson"));
        row.put("sku_image_url", OrderRowMapper.text(item, "skuImageUrl"));
        row.put("quantity", OrderRowMapper.intValue(item, "quantity", 0));
        row.put("sale_price_yuan", OrderAmountCalculator.money(OrderRowMapper.decimal(item, "salePriceYuan")));
        row.put("line_amount_yuan", OrderAmountCalculator.money(OrderRowMapper.decimal(item, "lineAmountYuan")));
        row.put("is_hot_sku", OrderRowMapper.boolValue(item, "hotSku"));
        row.put("created_at_epoch_ms", OrderRowMapper.longValue(item, "createdAtEpochMs"));
        return row;
    }

    private BatchRange batchRange(List<OrderRedisSnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return new BatchRange("", null, "", null);
        }
        OrderRedisSnapshot first = snapshots.get(0);
        OrderRedisSnapshot last = snapshots.get(snapshots.size() - 1);
        return new BatchRange(
                OrderRowMapper.text(first.order(), "orderNo"),
                OrderRowMapper.longValue(first.order(), "createdAtEpochMs"),
                OrderRowMapper.text(last.order(), "orderNo"),
                OrderRowMapper.longValue(last.order(), "createdAtEpochMs")
        );
    }

    private record BatchRange(String firstOrderNo,
                              Long firstCreatedAtMs,
                              String lastOrderNo,
                              Long lastCreatedAtMs) {
    }
}
