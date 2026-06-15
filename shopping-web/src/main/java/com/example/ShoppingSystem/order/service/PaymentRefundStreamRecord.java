package com.example.ShoppingSystem.order.service;

import java.util.Map;

public record PaymentRefundStreamRecord(String streamMessageId,
                                        Map<String, String> body) {
}
