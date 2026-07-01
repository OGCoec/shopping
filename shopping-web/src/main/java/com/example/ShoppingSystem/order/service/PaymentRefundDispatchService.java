package com.example.ShoppingSystem.order.service;
import java.util.List;
public interface PaymentRefundDispatchService {
    public record DispatchSummary(int claimedCount,
                                      int writtenCount) {
        }

    public record StreamDispatchSummary(int claimedCount,
                                            int writtenCount,
                                            List<String> ackStreamMessageIds) {
            public static StreamDispatchSummary empty() {
                return new StreamDispatchSummary(0, 0, List.of());
            }
        }

    public DispatchSummary dispatchAvailable(Integer rawLimit);

    public StreamDispatchSummary dispatchStreamRecords(List<PaymentRefundStreamRecord> records);
}
