package com.example.ShoppingSystem.admin.dto;

public record AdminIp2LocationVerifyLinkItem(int lineNumber,
                                             String email,
                                             String clientId,
                                             String folderName,
                                             String sender,
                                             String subject,
                                             String receivedAt,
                                             String verifyUrl,
                                             String verifyToken,
                                             String reason) {
}
