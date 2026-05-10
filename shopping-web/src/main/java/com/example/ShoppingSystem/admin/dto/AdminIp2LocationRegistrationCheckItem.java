package com.example.ShoppingSystem.admin.dto;

public record AdminIp2LocationRegistrationCheckItem(int lineNumber,
                                                    String email,
                                                    String clientId,
                                                    boolean registered,
                                                    String folderName,
                                                    String sender,
                                                    String subject,
                                                    String receivedAt,
                                                    String reason) {
}
