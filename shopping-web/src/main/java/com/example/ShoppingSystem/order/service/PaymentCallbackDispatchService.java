package com.example.ShoppingSystem.order.service;

import com.example.ShoppingSystem.Utils.HybridIdCodec;
import com.example.ShoppingSystem.Utils.HybridSemaphoreIdWorker;
import com.example.ShoppingSystem.mapper.coupon.CouponUsageRecordMapper;
import com.example.ShoppingSystem.mapper.coupon.UserCouponMapper;
import com.example.ShoppingSystem.mapper.order.OrderMapper;
import com.example.ShoppingSystem.mapper.order.PaymentCallbackInboxMapper;
import com.example.ShoppingSystem.mapper.order.PaymentRefundMapper;
import com.example.ShoppingSystem.order.rabbit.PaymentRefundMessagePublisher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

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
