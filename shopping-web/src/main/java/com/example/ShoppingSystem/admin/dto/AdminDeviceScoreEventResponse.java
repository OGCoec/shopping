package com.example.ShoppingSystem.admin.dto;

public record AdminDeviceScoreEventResponse(int scoreBefore,
                                            int penaltyScore,
                                            int scoreAfter,
                                            String reason,
                                            String createdAt) {
}
