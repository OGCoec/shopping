package com.example.ShoppingSystem.order.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record OrderDetailResponse(String orderNo,
                                  String status,
                                  BigDecimal totalAmountYuan,
                                  BigDecimal discountAmountYuan,
                                  BigDecimal payAmountYuan,
                                  String userCouponId,
                                  OffsetDateTime expireAt,
                                  OffsetDateTime paidAt,
                                  OffsetDateTime closingAt,
                                  OffsetDateTime closingDeadlineAt,
                                  OffsetDateTime cancelledAt,
                                  OffsetDateTime closedAt,
                                  List<OrderItemResponse> items) {
}
