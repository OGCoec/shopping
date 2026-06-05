package com.example.ShoppingSystem.admin.dto;

import java.util.List;

public record AdminKiroMailStatusCheckRequest(List<String> credentialLines,
                                               Integer threadPoolSize) {
}
