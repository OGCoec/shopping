package com.example.ShoppingSystem.mapper.order;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface PaymentCallbackInboxMapper {

    Map<String, Object> upsertCallbackIdempotent(@Param("callbackNo") String callbackNo,
                                                 @Param("orderNo") String orderNo,
                                                 @Param("externalTradeNo") String externalTradeNo,
                                                 @Param("paymentProvider") String paymentProvider,
                                                 @Param("paidAt") OffsetDateTime paidAt,
                                                 @Param("paidAmountYuan") BigDecimal paidAmountYuan,
                                                 @Param("idempotencyKey") String idempotencyKey,
                                                 @Param("rawPayloadJson") String rawPayloadJson);

    List<Map<String, Object>> claimDispatchBatch(@Param("limit") int limit,
                                                 @Param("maxRetry") int maxRetry);

    List<Map<String, Object>> batchUpsertAndClaimStreamCallbacks(@Param("callbacksJson") String callbacksJson,
                                                                 @Param("maxRetry") int maxRetry,
                                                                 @Param("processingTimeoutMs") long processingTimeoutMs);

    int batchWriteResults(@Param("resultsJson") String resultsJson);

    Map<String, Object> findByCallbackNo(@Param("callbackNo") String callbackNo);

    List<Map<String, Object>> pageForAdmin(@Param("status") String status,
                                           @Param("orderNo") String orderNo,
                                           @Param("callbackNo") String callbackNo,
                                           @Param("externalTradeNo") String externalTradeNo,
                                           @Param("resultOutcome") String resultOutcome,
                                           @Param("limit") int limit,
                                           @Param("offset") long offset);

    long countForAdmin(@Param("status") String status,
                       @Param("orderNo") String orderNo,
                       @Param("callbackNo") String callbackNo,
                       @Param("externalTradeNo") String externalTradeNo,
                       @Param("resultOutcome") String resultOutcome);
}
