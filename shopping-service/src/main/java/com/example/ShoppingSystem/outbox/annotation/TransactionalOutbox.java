package com.example.ShoppingSystem.outbox.annotation;

import com.example.ShoppingSystem.common.datasource.DataSourceRoute;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明式跨库最终一致注解。
 * 标注在业务方法上后，切面会在指定本地库路由上开启本地事务，执行业务写，
 * 并把业务方法运行期间通过 OutboxEventCollector.register(...) 登记的所有事件
 * 写入同一本地库的 outbox_event，与业务写一起原子提交/回滚。
 * 跨库部分由 OutboxEventDispatcher 投递、消费端 Inbox 幂等补写，最终一致。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TransactionalOutbox {

    /** 业务写与 outbox 写所在的本地库路由。 */
    DataSourceRoute value();
}