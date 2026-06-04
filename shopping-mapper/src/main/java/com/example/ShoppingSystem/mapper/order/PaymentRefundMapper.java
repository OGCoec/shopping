package com.example.ShoppingSystem.mapper.order;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface PaymentRefundMapper {

    Map<String, Object> insertRefundIgnore(@Param("refundNo") String refundNo,
                                           @Param("orderNo") String orderNo,
                                           @Param("userId") Long userId,
                                           @Param("paymentProvider") String paymentProvider,
                                           @Param("externalTradeNo") String externalTradeNo,
                                           @Param("paymentCallbackId") String paymentCallbackId,
                                           @Param("paidAmountYuan") BigDecimal paidAmountYuan,
                                           @Param("refundAmountYuan") BigDecimal refundAmountYuan,
                                           @Param("currency") String currency,
                                           @Param("source") String source,
                                           @Param("reasonCode") String reasonCode,
                                           @Param("reasonDetail") String reasonDetail,
                                           @Param("orderStatusWhenDetected") String orderStatusWhenDetected,
                                           @Param("detectedAt") OffsetDateTime detectedAt,
                                           @Param("detectionBatchNo") String detectionBatchNo,
                                           @Param("userMessage") String userMessage,
                                           @Param("snapshotJson") String snapshotJson,
                                           @Param("extraJson") String extraJson,
                                           @Param("idempotencyKey") String idempotencyKey);

    List<Map<String, Object>> batchInsertRefundIgnore(@Param("refundsJson") String refundsJson);

    Map<String, Object> findByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

    Map<String, Object> findByRefundNo(@Param("refundNo") String refundNo);

    List<Map<String, Object>> pageForAdmin(@Param("status") String status,
                                           @Param("orderNo") String orderNo,
                                           @Param("refundNo") String refundNo,
                                           @Param("source") String source,
                                           @Param("reasonCode") String reasonCode,
                                           @Param("limit") int limit,
                                           @Param("offset") long offset);

    long countForAdmin(@Param("status") String status,
                       @Param("orderNo") String orderNo,
                       @Param("refundNo") String refundNo,
                       @Param("source") String source,
                       @Param("reasonCode") String reasonCode);

    List<Map<String, Object>> pageForUserOrder(@Param("userId") Long userId,
                                               @Param("orderNo") String orderNo,
                                               @Param("limit") int limit,
                                               @Param("offset") long offset);

    long countForUserOrder(@Param("userId") Long userId,
                           @Param("orderNo") String orderNo);

    Map<String, Object> approve(@Param("refundNo") String refundNo,
                                @Param("adminId") Long adminId,
                                @Param("adminRemark") String adminRemark,
                                @Param("userMessage") String userMessage,
                                @Param("version") long version);

    Map<String, Object> reject(@Param("refundNo") String refundNo,
                               @Param("adminId") Long adminId,
                               @Param("rejectReason") String rejectReason,
                               @Param("adminRemark") String adminRemark,
                               @Param("version") long version);

    Map<String, Object> markRefunded(@Param("refundNo") String refundNo,
                                     @Param("adminId") Long adminId,
                                     @Param("refundProofNo") String refundProofNo,
                                     @Param("refundProofUrl") String refundProofUrl,
                                     @Param("adminRemark") String adminRemark,
                                     @Param("version") long version);

    List<Map<String, Object>> claimDispatchBatch(@Param("limit") int limit,
                                                 @Param("maxRetry") int maxRetry);

    int batchWriteDispatchResults(@Param("resultsJson") String resultsJson);
}
