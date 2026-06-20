package com.example.ShoppingSystem.outbox;

import com.example.ShoppingSystem.common.datasource.DataSourceRoute;
import com.example.ShoppingSystem.common.datasource.RoutedTransactionExecutor;
import com.example.ShoppingSystem.mapper.common.InboxEventMapper;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
public class InboxEventService {

    private final InboxEventMapper inboxEventMapper;
    private final RoutedTransactionExecutor routedTransactionExecutor;

    public InboxEventService(InboxEventMapper inboxEventMapper,
                             RoutedTransactionExecutor routedTransactionExecutor) {
        this.inboxEventMapper = inboxEventMapper;
        this.routedTransactionExecutor = routedTransactionExecutor;
    }

    /**
     * 幂等抢占处理: 没记录则插入 PROCESSING 并返回 true; 已 PROCESSED 或 PROCESSING 未超时返回 false;
     * 已 FAILED 或 PROCESSING 超时则抢回并返回 true。
     */
    public boolean tryStartProcessing(DataSourceRoute route,
                                      String eventId,
                                      String consumerName,
                                      long processingTimeoutMs) {
        long timeout = Math.max(1000L, processingTimeoutMs);
        return routedTransactionExecutor.execute(
                route,
                () -> inboxEventMapper.tryStartProcessing(
                        requireText(eventId, "eventId"),
                        requireText(consumerName, "consumerName"),
                        timeout,
                        OffsetDateTime.now()
                ) > 0
        );
    }

    public boolean markProcessing(DataSourceRoute route, String eventId, String consumerName) {
        return routedTransactionExecutor.execute(
                route,
                () -> inboxEventMapper.insertProcessing(
                        requireText(eventId, "eventId"),
                        requireText(consumerName, "consumerName"),
                        OffsetDateTime.now()
                ) > 0
        );
    }

    public void markProcessed(DataSourceRoute route, String eventId, String consumerName) {
        routedTransactionExecutor.executeWithoutResult(
                route,
                () -> inboxEventMapper.markProcessed(
                        requireText(eventId, "eventId"),
                        requireText(consumerName, "consumerName"),
                        OffsetDateTime.now()
                )
        );
    }

    public void markFailed(DataSourceRoute route, String eventId, String consumerName, String lastError) {
        routedTransactionExecutor.executeWithoutResult(
                route,
                () -> inboxEventMapper.markFailed(
                        requireText(eventId, "eventId"),
                        requireText(consumerName, "consumerName"),
                        trimToLimit(lastError, 2000)
                )
        );
    }

    private String requireText(String value, String field) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Inbox " + field + " is required.");
        }
        return normalized;
    }

    private String trimToLimit(String value, int limit) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() <= limit) {
            return normalized;
        }
        return normalized.substring(0, limit);
    }
}
