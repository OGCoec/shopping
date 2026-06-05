package com.example.ShoppingSystem.admin.dto;

public record AdminKiroMailStatusSummary(int notRegistered,
                                         int registeredNormal,
                                         int detectedEvidenceFound,
                                         int duplicate,
                                         int failed) {
}
