package com.example.ShoppingSystem.order.service;

import com.example.ShoppingSystem.mapper.order.PaymentRefundMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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
