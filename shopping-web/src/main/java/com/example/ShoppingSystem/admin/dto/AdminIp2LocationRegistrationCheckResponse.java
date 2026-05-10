package com.example.ShoppingSystem.admin.dto;

import java.util.List;

public record AdminIp2LocationRegistrationCheckResponse(int requestedCount,
                                                        int threadPoolSize,
                                                        List<AdminIp2LocationRegistrationCheckItem> registered,
                                                        List<AdminIp2LocationRegistrationCheckItem> unregistered,
                                                        List<AdminIp2LocationRegistrationCheckItem> failed) {
}
