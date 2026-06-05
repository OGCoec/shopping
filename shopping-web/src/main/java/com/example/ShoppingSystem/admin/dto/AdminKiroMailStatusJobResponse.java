package com.example.ShoppingSystem.admin.dto;

import java.util.List;

public record AdminKiroMailStatusJobResponse(String jobId,
                                             String status,
                                             int requestedCount,
                                             int processedCount,
                                             int runningCount,
                                             int queuedCount,
                                             int threadPoolSize,
                                             String startedAt,
                                             String completedAt,
                                             long elapsedMillis,
                                             AdminKiroMailStatusSummary summary,
                                             List<AdminKiroMailStatusResultItem> results) {
}
