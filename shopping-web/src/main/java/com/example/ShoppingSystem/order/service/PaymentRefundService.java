package com.example.ShoppingSystem.order.service;

import com.example.ShoppingSystem.Utils.HybridIdCodec;
import com.example.ShoppingSystem.Utils.HybridSemaphoreIdWorker;
import com.example.ShoppingSystem.admin.service.common.AdminServiceException;
import com.example.ShoppingSystem.config.datasource.OrderReadReplicaQueryExecutor;
import com.example.ShoppingSystem.mapper.order.OrderMapper;
import com.example.ShoppingSystem.mapper.order.PaymentRefundMapper;
import com.example.ShoppingSystem.order.dto.PaymentRefundApplyRequest;
import com.example.ShoppingSystem.order.dto.PaymentRefundPageResponse;
import com.example.ShoppingSystem.order.dto.PaymentRefundResponse;
import com.example.ShoppingSystem.order.rabbit.PaymentRefundMessagePublisher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface PaymentRefundService {
    public PaymentRefundResponse createFromPaymentAbnormal(String orderNo,
                                                           String externalTradeNo,
                                                           OffsetDateTime paidAt,
                                                           BigDecimal paidAmountYuan,
                                                           String paymentProvider,
                                                           Map<String, Object> order,
                                                           String reasonCode);

    public PaymentRefundResponse applyForUser(Long userId,
                                              String orderNo,
                                              PaymentRefundApplyRequest request);

    public PaymentRefundPageResponse pageForUserOrder(Long userId,
                                                      String orderNo,
                                                      Integer rawPage,
                                                      Integer rawPageSize);

    public PaymentRefundPageResponse pageForAdmin(Integer rawPage,
                                                  Integer rawPageSize,
                                                  String rawStatus,
                                                  String rawOrderNo,
                                                  String rawRefundNo,
                                                  String rawSource,
                                                  String rawReasonCode);

    public PaymentRefundResponse detailForAdmin(String refundNo);

    public PaymentRefundResponse approve(String refundNo,
                                         Long version,
                                         Long adminId,
                                         String adminRemark,
                                         String userMessage);

    public PaymentRefundResponse reject(String refundNo,
                                        Long version,
                                        Long adminId,
                                        String rejectReason,
                                        String adminRemark);

    public PaymentRefundResponse markRefunded(String refundNo,
                                              Long version,
                                              Long adminId,
                                              String refundProofNo,
                                              String refundProofUrl,
                                              String adminRemark);
}
