package com.example.ShoppingSystem.order.rabbit;

import com.example.ShoppingSystem.order.service.OrderExpireService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class OrderExpireDeadLetterConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderExpireDeadLetterConsumer.class);

    private final OrderExpireService orderExpireService;

    public OrderExpireDeadLetterConsumer(OrderExpireService orderExpireService) {
        this.orderExpireService = orderExpireService;
    }

    @RabbitListener(
            queues = "${app.rabbitmq.order-expire.dead-letter-queue:order.expire.dlq}",
            containerFactory = "orderExpireRabbitListenerContainerFactory"
    )
    public void consume(OrderExpireMessage message) {
        if (message == null || message.orderNo() == null || message.orderNo().isBlank()) {
            log.warn("[Order] invalid expire message skipped, message={}", message);
            return;
        }
        String phase = OrderExpireMessage.normalizePhase(message.phase());
        if (OrderExpireMessage.PHASE_CLOSING_FINALIZE.equals(phase)) {
            orderExpireService.finalizeClosing(message.orderNo());
            return;
        }
        orderExpireService.startClosing(message.orderNo());
    }
}
