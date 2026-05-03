package com.example.ShoppingSystem.controller.login.user.dto;

public record RegisterFlowStateResponse(boolean success,
                                        String message,
                                        String email,
                                        String currentStep,
                                        String riskLevel,
                                        boolean requirePhoneBinding,
                                        boolean completed) {
}
