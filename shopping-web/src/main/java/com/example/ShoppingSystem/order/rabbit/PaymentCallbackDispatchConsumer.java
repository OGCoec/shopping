package com.example.ShoppingSystem.order.rabbit;

import com.example.ShoppingSystem.order.service.PaymentCallbackDispatchProperties;
import com.example.ShoppingSystem.order.service.PaymentCallbackDispatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentCallbackDispatchConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentCallbackDispatchConsumer.class);

    private final PaymentCallbackDispatchService paymentCallbackDispatchService;
    private final PaymentCallbackDispatchProperties properties;

    public PaymentCallbackDispatchConsumer(PaymentCallbackDispatchService paymentCallbackDispatchService,
                                           PaymentCallbackDispatchProperties properties) {
        this.paymentCallbackDispatchService = paymentCallbackDispatchService;
        this.properties = properties;
    }

    @RabbitListener(
            queues = "${app.rabbitmq.payment-callback.queue:payment.callback.dispatch.queue}",
            containerFactory = "paymentCallbackRabbitListenerContainerFactory"
    )
    public void consume(PaymentCallbackDispatchMessage message) {
        if (message == null || message.callbackNo() == null || message.callbackNo().isBlank()) {
            log.warn("[PaymentCallback] invalid dispatch message skipped, message={}", message);
            return;
        }
        paymentCallbackDispatchService.dispatchAvailable(properties.getConsumerBatchSize());
    }
}
