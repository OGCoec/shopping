package com.example.ShoppingSystem.order.dto;

import java.time.OffsetDateTime;

public record OrderCardSecretValueResponse(String cardSecretId,
                                           String secret,
                                           OffsetDateTime deliveredAt) {
}
