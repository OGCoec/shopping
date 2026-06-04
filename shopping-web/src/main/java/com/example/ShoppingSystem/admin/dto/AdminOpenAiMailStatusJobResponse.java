package com.example.ShoppingSystem.admin.dto;

import java.util.List;

public record AdminOpenAiMailStatusJobResponse(String jobId,
                                               String status,
                                               int requestedCount,
                                               int processedCount,
                                               int runningCount,
                                               int queuedCount,
                                               int threadPoolSize,
                                               String startedAt,
                                               String completedAt,
                                               long elapsedMillis,
                                               AdminOpenAiMailStatusSummary summary,
                                               List<AdminOpenAiMailStatusResultItem> results) {
}
