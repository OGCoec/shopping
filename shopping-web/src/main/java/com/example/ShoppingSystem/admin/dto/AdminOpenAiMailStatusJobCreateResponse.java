package com.example.ShoppingSystem.admin.dto;

public record AdminOpenAiMailStatusJobCreateResponse(String jobId,
                                                     String status,
                                                     int requestedCount,
                                                     int acceptedCount,
                                                     int duplicateCount,
                                                     int invalidCount,
                                                     int threadPoolSize,
                                                     int maxThreadPoolSize,
                                                     int perAccountTimeoutSeconds,
                                                     String createdAt,
                                                     long pollAfterMillis) {
}
