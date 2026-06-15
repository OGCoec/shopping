package com.example.ShoppingSystem.order.rabbit;

import com.example.ShoppingSystem.order.service.OrderClosingFinalizeCheckService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class OrderClosingFinalizeCheckConsumer {

    private final OrderClosingFinalizeCheckService orderClosingFinalizeCheckService;

    public OrderClosingFinalizeCheckConsumer(OrderClosingFinalizeCheckService orderClosingFinalizeCheckService) {
        this.orderClosingFinalizeCheckService = orderClosingFinalizeCheckService;
    }

    @RabbitListener(
            queues = "${app.rabbitmq.order-expire.closing-finalize-queue:order.closing.finalize.check.queue}",
            containerFactory = "orderExpireRabbitListenerContainerFactory"
    )
    public void consume(OrderClosingFinalizeCheckMessage message) {
        orderClosingFinalizeCheckService.check(message);
    }
}
