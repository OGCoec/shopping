package com.example.ShoppingSystem.outbox.annotation;

import com.example.ShoppingSystem.outbox.InboxIdempotentConsumerExecutor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * @IdempotentConsumer 切面。
 * 解析 eventId SpEL（引用方法入参），经 InboxIdempotentConsumerExecutor 在目标库做数据库级幂等，
 * 业务异常已 markFailed 并向上抛出，交由监听容器（defaultRequeueRejected=false）投递死信。
 */
@Aspect
@Component
public class IdempotentConsumerAspect {

    private final InboxIdempotentConsumerExecutor inboxExecutor;
    private final ExpressionParser expressionParser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    public IdempotentConsumerAspect(InboxIdempotentConsumerExecutor inboxExecutor) {
        this.inboxExecutor = inboxExecutor;
    }

    @Around("@annotation(idempotentConsumer)")
    public Object around(ProceedingJoinPoint joinPoint, IdempotentConsumer idempotentConsumer) throws Throwable {
        String eventId = resolveEventId(idempotentConsumer.eventId(), joinPoint);
        inboxExecutor.execute(
                idempotentConsumer.route(),
                eventId,
                idempotentConsumer.consumer(),
                () -> proceed(joinPoint));
        return null;
    }

    private void proceed(ProceedingJoinPoint joinPoint) throws Exception {
        try {
            joinPoint.proceed();
        } catch (Exception e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    private String resolveEventId(String spel, ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        StandardEvaluationContext context = new StandardEvaluationContext();
        String[] names = parameterNameDiscoverer.getParameterNames(method);
        Object[] args = joinPoint.getArgs();
        if (names != null) {
            for (int i = 0; i < names.length && i < args.length; i++) {
                context.setVariable(names[i], args[i]);
            }
        }
        Expression expression = expressionParser.parseExpression(spel);
        Object value = expression.getValue(context);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalStateException(
                    "@IdempotentConsumer eventId resolved to blank, expression=" + spel);
        }
        return value.toString();
    }
}