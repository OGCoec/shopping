package com.example.ShoppingSystem.order.service;

import com.example.ShoppingSystem.common.datasource.DataSourceRoute;
import com.example.ShoppingSystem.common.datasource.RoutedTransactionExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class OrderClosingCompensateScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderClosingCompensateScheduler.class);

    private final OrderRedisSnapshotService orderRedisSnapshotService;
    private final OrderInventoryReleaseService orderInventoryReleaseService;
    private final OrderCouponService orderCouponService;
    private final OrderCouponUsageService orderCouponUsageService;
    private final RoutedTransactionExecutor routedTransactionExecutor;
    private final boolean enabled;
    private final int batchSize;
    private final int maxBatchesPerRun;
    private final Duration lockTtl;

    public OrderClosingCompensateScheduler(OrderRedisSnapshotService orderRedisSnapshotService,
                                           OrderInventoryReleaseService orderInventoryReleaseService,
                                           OrderCouponService orderCouponService,
                                           OrderCouponUsageService orderCouponUsageService,
                                           RoutedTransactionExecutor routedTransactionExecutor,
                                           @Value("${shopping.order.closing-compensate-enabled:true}") boolean enabled,
                                           @Value("${shopping.order.closing-compensate-batch-size:100}") int batchSize,
                                           @Value("${shopping.order.closing-compensate-max-batches-per-run:20}") int maxBatchesPerRun,
                                           @Value("${shopping.order.closing-compensate-lock-ttl-ms:1800000}") long lockTtlMs) {
        this.orderRedisSnapshotService = orderRedisSnapshotService;
        this.orderInventoryReleaseService = orderInventoryReleaseService;
        this.orderCouponService = orderCouponService;
        this.orderCouponUsageService = orderCouponUsageService;
        this.routedTransactionExecutor = routedTransactionExecutor;
        this.enabled = enabled;
        this.batchSize = batchSize <= 0 ? 100 : batchSize;
        this.maxBatchesPerRun = Math.max(1, maxBatchesPerRun);
        this.lockTtl = Duration.ofMillis(Math.max(1000L, lockTtlMs));
    }

    @Scheduled(
            initialDelayString = "${shopping.order.closing-compensate-initial-delay-ms:60000}",
            fixedDelayString = "${shopping.order.closing-compensate-delay-ms:1800000}"
    )
    public void compensateClosingOrders() {
        if (!enabled) {
            return;
        }
        String lockValue = UUID.randomUUID().toString();
        if (!orderRedisSnapshotService.acquireClosingCompensateLock(lockValue, lockTtl)) {
            return;
        }
        try {
            compensateClosingOrdersWithLock();
        } finally {
            orderRedisSnapshotService.releaseClosingCompensateLock(lockValue);
        }
    }

    private void compensateClosingOrdersWithLock() {
        int batches = 0;
        int claimed = 0;
        int changed = 0;
        int staleMissing = 0;
        int staleTerminal = 0;
        int skipped = 0;
        int releasedInventoryItems = 0;
        int releasedCoupons = 0;
        boolean failed = false;

        while (batches < maxBatchesPerRun) {
            OffsetDateTime now = OffsetDateTime.now();
            OrderClosingCompensateBatchResult batch = orderRedisSnapshotService.compensateDueClosing(now, batchSize);
            if (batch.claimedCount() == 0) {
                break;
            }
            batches += 1;
            claimed += batch.claimedCount();
            changed += batch.changedCount();
            staleMissing += batch.staleMissingCount();
            staleTerminal += batch.staleTerminalCount();
            skipped += batch.skippedCount();

            ResourceReleaseResult releaseResult = new ResourceReleaseResult(0, 0);
            try {
                releaseResult = releaseResources(batch.changedSnapshots());
                if (releaseResult == null) {
                    releaseResult = new ResourceReleaseResult(0, 0);
                }
                releasedInventoryItems += releaseResult.inventoryItemCount();
                releasedCoupons += releaseResult.couponCount();
            } catch (Exception e) {
                failed = true;
                log.warn("[Order] closing compensate resource release failed, batch={}, changed={}",
                        batches, batch.changedSnapshots().size(), e);
                break;
            }

            log.info(
                    "[Order] closing compensate batch finished, batch={}, claimed={}, changed={}, staleMissing={}, staleTerminal={}, skippedNonClosing={}, skippedNotDue={}, releasedInventoryItems={}, releasedCoupons={}",
                    batches,
                    batch.claimedCount(),
                    batch.changedCount(),
                    batch.staleMissingCount(),
                    batch.staleTerminalCount(),
                    batch.skippedNonClosingCount(),
                    batch.skippedNotDueCount(),
                    releaseResult.inventoryItemCount(),
                    releaseResult.couponCount()
            );

            if (batch.changedCount() == 0
                    && batch.staleMissingCount() == 0
                    && batch.staleTerminalCount() == 0
                    && batch.skippedNotDueCount() == 0
                    && batch.skippedNonClosingCount() > 0) {
                break;
            }
            if (batch.claimedCount() < batchSize) {
                break;
            }
        }

        if (batches > 0 || failed) {
            log.info(
                    "[Order] closing compensate run finished, batches={}, claimed={}, changed={}, staleMissing={}, staleTerminal={}, skipped={}, releasedInventoryItems={}, releasedCoupons={}, batchSize={}, maxBatches={}, failed={}",
                    batches,
                    claimed,
                    changed,
                    staleMissing,
                    staleTerminal,
                    skipped,
                    releasedInventoryItems,
                    releasedCoupons,
                    batchSize,
                    maxBatchesPerRun,
                    failed
            );
        }
    }

    private ResourceReleaseResult releaseResources(List<OrderRedisSnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return new ResourceReleaseResult(0, 0);
        }
        int inventoryItemCount = routedTransactionExecutor.execute(
                DataSourceRoute.PRODUCT,
                () -> orderInventoryReleaseService.releaseAll(snapshots)
        );
        List<Map<String, Object>> released = routedTransactionExecutor.execute(DataSourceRoute.TRADE, () -> {
            List<Map<String, Object>> rows = orderCouponService.releaseLockedCoupons(snapshots);
            orderCouponUsageService.writeReleases(rows);
            return rows;
        });
        return new ResourceReleaseResult(inventoryItemCount, released.size());
    }

    private record ResourceReleaseResult(int inventoryItemCount,
                                         int couponCount) {
    }
}
