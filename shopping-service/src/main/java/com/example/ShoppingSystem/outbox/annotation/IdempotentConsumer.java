package com.example.ShoppingSystem.outbox.annotation;

import com.example.ShoppingSystem.common.datasource.DataSourceRoute;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 消费端数据库级幂等注解。
 * 标注在 @RabbitListener 消费方法上后，切面通过 InboxIdempotentConsumerExecutor
 * 在目标业务库 inbox_event 上执行 tryStartProcessing -> 业务 -> markProcessed/markFailed。
 * 重复事件只生效一次，失败 markFailed 后向上抛出，交由监听容器投递死信。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface IdempotentConsumer {

    /** 目标业务库路由（写哪个库就用哪个库做 inbox 幂等）。 */
    DataSourceRoute route();

    /** 消费者名，与 eventId 共同构成 inbox 幂等唯一键。 */
    String consumer();

    /** 取 eventId 的 SpEL 表达式，引用方法入参名，例如 "#message.eventId"。 */
    String eventId();
}