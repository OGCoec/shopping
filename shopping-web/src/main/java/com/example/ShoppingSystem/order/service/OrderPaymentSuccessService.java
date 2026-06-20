package com.example.ShoppingSystem.order.service;

import com.example.ShoppingSystem.mapper.order.OrderMapper;
import com.example.ShoppingSystem.order.dto.PaymentRefundResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

public interface OrderPaymentSuccessService {
    public OrderPaymentMarkResult markPaid(String orderNo,
                                           OffsetDateTime paidAt,
                                           String externalTradeNo,
                                           BigDecimal paidAmountYuan,
                                           String paymentProvider);

    public boolean markPendingPaidForUser(Long userId, String orderNo, OffsetDateTime paidAt, String externalTradeNo);
}
