package com.example.ShoppingSystem.admin.dto;

public record AdminOpenAiMailStatusResultItem(int lineNumber,
                                              String email,
                                              String status,
                                              boolean openaiMailFound,
                                              String folderName,
                                              String sender,
                                              String subject,
                                              String receivedAt,
                                              String evidencePhrase,
                                              String imapRoute,
                                              String reason) {
}
