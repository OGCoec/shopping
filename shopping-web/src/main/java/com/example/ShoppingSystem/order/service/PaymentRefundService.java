package com.example.ShoppingSystem.order.service;
import com.example.ShoppingSystem.order.dto.PaymentRefundApplyRequest;
import com.example.ShoppingSystem.order.dto.PaymentRefundPageResponse;
import com.example.ShoppingSystem.order.dto.PaymentRefundResponse;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
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
