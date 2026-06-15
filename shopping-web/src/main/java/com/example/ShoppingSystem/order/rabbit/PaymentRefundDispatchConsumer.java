package com.example.ShoppingSystem.order.rabbit;

import com.example.ShoppingSystem.order.service.PaymentRefundDispatchProperties;
import com.example.ShoppingSystem.order.service.PaymentRefundDispatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.rabbitmq.refund", name = "listener-enabled", havingValue = "true")
public class PaymentRefundDispatchConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentRefundDispatchConsumer.class);

    private final PaymentRefundDispatchService paymentRefundDispatchService;
    private final PaymentRefundDispatchProperties properties;

    public PaymentRefundDispatchConsumer(PaymentRefundDispatchService paymentRefundDispatchService,
                                         PaymentRefundDispatchProperties properties) {
        this.paymentRefundDispatchService = paymentRefundDispatchService;
        this.properties = properties;
    }

    @RabbitListener(
            queues = "${app.rabbitmq.refund.queue:payment.refund.dispatch.queue}",
            containerFactory = "paymentRefundRabbitListenerContainerFactory"
    )
    public void consume(PaymentRefundDispatchMessage message) {
        if (message == null || message.refundNo() == null || message.refundNo().isBlank()) {
            log.warn("[Refund] invalid dispatch message skipped, message={}", message);
            return;
        }
        paymentRefundDispatchService.dispatchAvailable(properties.getConsumerBatchSize());
    }
}
