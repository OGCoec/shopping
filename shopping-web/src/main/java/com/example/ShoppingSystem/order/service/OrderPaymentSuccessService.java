package com.example.ShoppingSystem.order.service;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
public interface OrderPaymentSuccessService {
    public OrderPaymentMarkResult markPaid(String orderNo,
                                           OffsetDateTime paidAt,
                                           String externalTradeNo,
                                           BigDecimal paidAmountYuan,
                                           String paymentProvider);

    public boolean markPendingPaidForUser(Long userId, String orderNo, OffsetDateTime paidAt, String externalTradeNo);
}
