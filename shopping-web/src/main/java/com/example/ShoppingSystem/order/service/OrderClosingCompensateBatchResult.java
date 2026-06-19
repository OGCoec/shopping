package com.example.ShoppingSystem.order.service;

import java.util.List;

public record OrderClosingCompensateBatchResult(int claimedCount,
                                                int changedCount,
                                                int staleMissingCount,
                                                int staleTerminalCount,
                                                int skippedNonClosingCount,
                                                int skippedNotDueCount,
                                                List<OrderRedisSnapshot> changedSnapshots) {

    public static OrderClosingCompensateBatchResult empty() {
        return new OrderClosingCompensateBatchResult(0, 0, 0, 0, 0, 0, List.of());
    }

    public int skippedCount() {
        return skippedNonClosingCount + skippedNotDueCount;
    }
}
