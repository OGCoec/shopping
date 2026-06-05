package com.example.ShoppingSystem.admin.dto;

public record AdminKiroMailStatusResultItem(int lineNumber,
                                            String email,
                                            String status,
                                            boolean mailFound,
                                            String folderName,
                                            String sender,
                                            String subject,
                                            String receivedAt,
                                            String evidencePhrase,
                                            String imapRoute,
                                            String reason) {
}
