package com.example.ShoppingSystem.outbox;

import com.example.ShoppingSystem.common.datasource.DataSourceRoute;
import com.example.ShoppingSystem.common.datasource.RoutedTransactionExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 消费端数据库级幂等包装器。
 * 统一封装 tryStartProcessing -> 业务逻辑 -> markProcessed/markFailed 的状态机，
 * route 必须是目标业务库（写 CORE 用 CORE，写 RISK 用 RISK，写用户券用 TRADE）。
 */
@Component
public class InboxIdempotentConsumerExecutor {

    private static final Logger log = LoggerFactory.getLogger(InboxIdempotentConsumerExecutor.class);

    private final InboxEventService inboxEventService;
    private final RoutedTransactionExecutor routedTransactionExecutor;
    private final long processingTimeoutMs;

    public InboxIdempotentConsumerExecutor(
            InboxEventService inboxEventService,
            RoutedTransactionExecutor routedTransactionExecutor,
            @Value("${shopping.inbox.processing-timeout-ms:60000}") long processingTimeoutMs) {
        this.inboxEventService = inboxEventService;
        this.routedTransactionExecutor = routedTransactionExecutor;
        this.processingTimeoutMs = Math.max(1000L, processingTimeoutMs);
    }

    /**
     * 业务逻辑回调，允许抛出受检异常，失败时交由调用方原有 retry/dead-letter 逻辑处理。
     */
    @FunctionalInterface
    public interface InboxWork {
        void run() throws Exception;
    }

    /**
     * 执行幂等消费。
     *
     * @return true 表示本次真正执行了业务（已 markProcessed）；false 表示重复或处理中被跳过。
     * @throws Exception 业务执行失败时已 markFailed，并向上抛出，交由调用方原有 retry/dead-letter 逻辑处理。
     */
    public boolean execute(DataSourceRoute route,
                           String eventId,
                           String consumerName,
                           InboxWork work) throws Exception {
        return execute(route, eventId, consumerName, false, work);
    }

    public boolean execute(DataSourceRoute route,
                           String eventId,
                           String consumerName,
                           boolean transactional,
                           InboxWork work) throws Exception {
        if (route == null) {
            throw new IllegalArgumentException("Inbox route is required.");
        }
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("Inbox eventId is required.");
        }
        if (consumerName == null || consumerName.isBlank()) {
            throw new IllegalArgumentException("Inbox consumerName is required.");
        }
        if (!inboxEventService.tryStartProcessing(route, eventId, consumerName, processingTimeoutMs)) {
            log.debug("[Inbox] skip duplicate/in-progress event, route={}, eventId={}, consumer={}",
                    route, eventId, consumerName);
            return false;
        }
        try {
            if (transactional) {
                executeTransactional(route, eventId, consumerName, work);
            } else {
                work.run();
                inboxEventService.markProcessed(route, eventId, consumerName);
            }
        } catch (Throwable t) {
            inboxEventService.markFailed(route, eventId, consumerName, t.getMessage());
            throwThrowable(t);
        }
        return true;
    }

    private void executeTransactional(DataSourceRoute route,
                                      String eventId,
                                      String consumerName,
                                      InboxWork work) throws Exception {
        try {
            routedTransactionExecutor.executeWithoutResult(route, () -> {
                try {
                    work.run();
                } catch (Exception e) {
                    throw new InboxWorkFailedException(e);
                }
                inboxEventService.markProcessedInCurrentTransaction(eventId, consumerName);
            });
        } catch (InboxWorkFailedException e) {
            throw e.getCauseException();
        }
    }

    private void throwThrowable(Throwable t) throws Exception {
        if (t instanceof Exception e) {
            throw e;
        }
        if (t instanceof Error e) {
            throw e;
        }
        throw new IllegalStateException(t);
    }

    private static final class InboxWorkFailedException extends RuntimeException {

        private InboxWorkFailedException(Exception cause) {
            super(cause);
        }

        private Exception getCauseException() {
            return (Exception) getCause();
        }
    }
}
