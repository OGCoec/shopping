package com.example.ShoppingSystem.outbox.annotation;

import com.example.ShoppingSystem.common.datasource.DataSourceRoute;
import com.example.ShoppingSystem.common.datasource.RoutedTransactionExecutor;
import com.example.ShoppingSystem.outbox.OutboxEventRequest;
import com.example.ShoppingSystem.outbox.OutboxEventService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @TransactionalOutbox 切面。
 * 在注解声明的本地库路由上开启本地事务：执行业务方法 -> 把业务期间登记的事件写入同库 outbox_event -> 一起提交。
 * 业务方法或 outbox 写抛出异常时整个本地事务回滚，业务写与事件不留痕，保证同库原子。
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TransactionalOutboxAspect {

    private final RoutedTransactionExecutor routedTransactionExecutor;
    private final OutboxEventService outboxEventService;
    private final OutboxEventCollector collector;

    public TransactionalOutboxAspect(RoutedTransactionExecutor routedTransactionExecutor,
                                     OutboxEventService outboxEventService,
                                     OutboxEventCollector collector) {
        this.routedTransactionExecutor = routedTransactionExecutor;
        this.outboxEventService = outboxEventService;
        this.collector = collector;
    }

    @Around("@annotation(transactionalOutbox)")
    public Object around(ProceedingJoinPoint joinPoint, TransactionalOutbox transactionalOutbox) {
        DataSourceRoute route = transactionalOutbox.value();
        if (route == null) {
            throw new IllegalArgumentException("@TransactionalOutbox route is required.");
        }
        collector.begin();
        try {
            return routedTransactionExecutor.execute(route, () -> {
                Object result = proceed(joinPoint);
                List<OutboxEventRequest> events = collector.drain();
                for (OutboxEventRequest event : events) {
                    outboxEventService.append(route, event);
                }
                return result;
            });
        } finally {
            collector.end();
        }
    }

    private Object proceed(ProceedingJoinPoint joinPoint) {
        try {
            return joinPoint.proceed();
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            // 受检异常包成 unchecked，确保 TransactionTemplate 触发回滚
            throw new IllegalStateException(t);
        }
    }
}