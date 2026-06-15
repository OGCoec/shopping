package com.example.ShoppingSystem.order.rabbit;

import com.example.ShoppingSystem.order.service.OrderPaymentExpireCheckService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class OrderPaymentExpireCheckConsumer {

    private final OrderPaymentExpireCheckService orderPaymentExpireCheckService;

    public OrderPaymentExpireCheckConsumer(OrderPaymentExpireCheckService orderPaymentExpireCheckService) {
        this.orderPaymentExpireCheckService = orderPaymentExpireCheckService;
    }

    @RabbitListener(
            queues = "${app.rabbitmq.order-expire.payment-check-queue:order.payment.expire.check.queue}",
            containerFactory = "orderExpireRabbitListenerContainerFactory"
    )
    public void consume(OrderPaymentExpireCheckMessage message) {
        orderPaymentExpireCheckService.check(message);
    }
}
