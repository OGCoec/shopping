package com.example.ShoppingSystem.outbox.annotation;

import com.example.ShoppingSystem.common.datasource.DataSourceRoute;
import com.example.ShoppingSystem.common.datasource.RoutedTransactionExecutor;
import com.example.ShoppingSystem.outbox.OutboxEventRequest;
import com.example.ShoppingSystem.outbox.OutboxEventService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.List;

/**
 * @TransactionalOutbox aspect.
 * On the local DB route declared by the annotation, opens a local transaction:
 * runs the business method, writes all events registered during the method into
 * the same DB outbox_event table, and commits them together. If the business
 * method or the outbox write throws, the whole local transaction rolls back so
 * the business write and the events leave no trace, guaranteeing same-DB atomicity.
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

    @Around("@annotation(com.example.ShoppingSystem.outbox.annotation.TransactionalOutbox)")
    public Object around(ProceedingJoinPoint joinPoint) {
        TransactionalOutbox transactionalOutbox = resolveAnnotation(joinPoint);
        DataSourceRoute route = transactionalOutbox == null ? null : transactionalOutbox.value();
        if (route == null) {
            throw new IllegalArgumentException("@TransactionalOutbox route is required.");
        }
        collector.begin();
        try {
            return routedTransactionExecutor.execute(route, () -> {
                Object result = proceed(joinPoint);
                List<OutboxEventRequest> events = collector.drain();
                outboxEventService.appendBatch(route, events);
                return result;
            });
        } finally {
            collector.end();
        }
    }

    private TransactionalOutbox resolveAnnotation(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        return method.getAnnotation(TransactionalOutbox.class);
    }

    private Object proceed(ProceedingJoinPoint joinPoint) {
        try {
            return joinPoint.proceed();
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            // Wrap checked exceptions as unchecked so TransactionTemplate triggers rollback.
            throw new IllegalStateException(t);
        }
    }
}