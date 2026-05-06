package com.example.ShoppingSystem.service.user.auth.risk;

public record AuthRiskSnapshot(int ipScore,
                               int deviceScore,
                               int totalScore,
                               String riskLevel) {
}
