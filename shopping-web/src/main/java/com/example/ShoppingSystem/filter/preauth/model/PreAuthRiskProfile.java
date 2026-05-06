package com.example.ShoppingSystem.filter.preauth.model;

/**
 * Minimal pre-auth risk snapshot used by the binding flow.
 */
public record PreAuthRiskProfile(int ipScore,
                                 int deviceScore,
                                 int score,
                                 String riskLevel) {
}
