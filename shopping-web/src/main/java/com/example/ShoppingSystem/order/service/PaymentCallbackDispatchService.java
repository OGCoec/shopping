package com.example.ShoppingSystem.order.service;
import java.util.List;
public interface PaymentCallbackDispatchService {
    public record DispatchSummary(int claimedCount,
                                      int inboxWrittenCount,
                                      int refundCount,
                                      int failedCount) {
        }

    public record StreamDispatchSummary(int claimedCount,
                                            int inboxWrittenCount,
                                            int refundCount,
                                            int failedCount,
                                            List<String> ackStreamMessageIds) {
            public static StreamDispatchSummary empty() {
                return new StreamDispatchSummary(0, 0, 0, 0, List.of());
            }
        }

    public DispatchSummary dispatchAvailable(Integer rawLimit);

    public StreamDispatchSummary dispatchStreamRecords(List<PaymentCallbackStreamRecord> records);
}
