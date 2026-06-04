package com.example.ShoppingSystem.admin.dto;

import java.util.List;

public record AdminOpenAiMailStatusCheckRequest(List<String> credentialLines,
                                                Integer threadPoolSize) {
}
