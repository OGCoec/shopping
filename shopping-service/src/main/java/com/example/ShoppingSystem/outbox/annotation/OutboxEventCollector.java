package com.example.ShoppingSystem.outbox.annotation;

import com.example.ShoppingSystem.outbox.OutboxEventRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 线程级 outbox 事件收集器。
 * 业务方法在 @TransactionalOutbox 切面内通过 register(...) 登记待发事件，
 * 仅写入当前线程内存，不触库；切面在同一本地事务内统一写入 outbox_event。
 * 使用栈结构支持 @TransactionalOutbox 方法的嵌套调用，内外层事件互不串扰。
 */
@Component
public class OutboxEventCollector {

    private static final ThreadLocal<Deque<List<OutboxEventRequest>>> STACK =
            ThreadLocal.withInitial(ArrayDeque::new);

    /** 切面进入时压入一层事件列表。 */
    void begin() {
        STACK.get().push(new ArrayList<>());
    }

    /** 业务代码调用：登记一条待发事件。必须在 @TransactionalOutbox 方法内调用。 */
    public void register(OutboxEventRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Outbox event request is required.");
        }
        Deque<List<OutboxEventRequest>> stack = STACK.get();
        if (stack.isEmpty()) {
            throw new IllegalStateException(
                    "OutboxEventCollector.register(...) must be called inside a @TransactionalOutbox method.");
        }
        stack.peek().add(request);
    }

    /** 切面读取当前层登记的事件列表。 */
    List<OutboxEventRequest> drain() {
        Deque<List<OutboxEventRequest>> stack = STACK.get();
        if (stack.isEmpty()) {
            return List.of();
        }
        return stack.peek();
    }

    /** 切面退出时弹出当前层，全部弹空后清理 ThreadLocal 避免泄漏。 */
    void end() {
        Deque<List<OutboxEventRequest>> stack = STACK.get();
        if (!stack.isEmpty()) {
            stack.pop();
        }
        if (stack.isEmpty()) {
            STACK.remove();
        }
    }
}