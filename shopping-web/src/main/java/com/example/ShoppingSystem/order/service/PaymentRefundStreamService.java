package com.example.ShoppingSystem.order.service;
import java.util.List;
public interface PaymentRefundStreamService {
    public record EnqueueSummary(int requestedCount,
                                     int enqueuedCount) {
        }

    public void initGroup();

    public EnqueueSummary enqueueBatch(List<String> refundNos);

    public List<PaymentRefundStreamRecord> readBatch();

    public long ackAndDelete(List<String> streamMessageIds);
}
