package com.example.ShoppingSystem.admin.dto;

public record AdminOpenAiMailStatusSummary(int notRegistered,
                                           int registeredNormal,
                                           int detectedEvidenceFound,
                                           int duplicate,
                                           int failed) {
}
