package com.example.ShoppingSystem.order.service;

import java.util.Map;

public record PaymentCallbackStreamRecord(String streamMessageId,
                                          Map<String, String> body) {
}
