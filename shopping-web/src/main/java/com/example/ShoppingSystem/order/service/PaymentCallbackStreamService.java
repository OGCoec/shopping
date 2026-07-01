package com.example.ShoppingSystem.order.service;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
public interface PaymentCallbackStreamService {
    public record EnqueueResult(String callbackNo,
                                    boolean created,
                                    String streamMessageId) {
        }

    public void initGroup();

    public EnqueueResult enqueue(String callbackNo,
                                 String orderNo,
                                 String externalTradeNo,
                                 String paymentProvider,
                                 OffsetDateTime paidAt,
                                 BigDecimal paidAmountYuan,
                                 String idempotencyKey,
                                 String rawPayloadJson,
                                 OffsetDateTime receivedAt);

    public List<PaymentCallbackStreamRecord> readBatch();

    public long ackAndDelete(List<String> streamMessageIds);
}
