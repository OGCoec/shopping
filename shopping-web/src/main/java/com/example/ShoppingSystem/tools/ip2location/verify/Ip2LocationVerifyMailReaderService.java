package com.example.ShoppingSystem.tools.ip2location.verify;

public interface Ip2LocationVerifyMailReaderService {
    public record RegistrationMailCheckResult(boolean success,
                                                  String reason,
                                                  String email,
                                                  String folderName,
                                                  String sender,
                                                  String subject,
                                                  String receivedAt) {
            public static RegistrationMailCheckResult succeeded(String email,
                                                                String folderName,
                                                                String sender,
                                                                String subject,
                                                                String receivedAt) {
                return new RegistrationMailCheckResult(
                        true,
                        "ok",
                        email,
                        folderName,
                        sender,
                        subject,
                        receivedAt
                );
            }

            public static RegistrationMailCheckResult failed(String reason) {
                return new RegistrationMailCheckResult(
                        false,
                        reason,
                        null,
                        null,
                        null,
                        null,
                        null
                );
            }
        }

    public record VerifyLinkReadResult(boolean success,
                                           String reason,
                                           String email,
                                           String folderName,
                                           String sender,
                                           String subject,
                                           String receivedAt,
                                           String verifyUrl,
                                           String verifyToken) {
            public static VerifyLinkReadResult succeeded(String email,
                                                         String folderName,
                                                         String sender,
                                                         String subject,
                                                         String receivedAt,
                                                         String verifyUrl,
                                                         String verifyToken) {
                return new VerifyLinkReadResult(
                        true,
                        "ok",
                        email,
                        folderName,
                        sender,
                        subject,
                        receivedAt,
                        verifyUrl,
                        verifyToken
                );
            }

            public static VerifyLinkReadResult failed(String reason) {
                return new VerifyLinkReadResult(
                        false,
                        reason,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                );
            }
        }

    public VerifyLinkReadResult readLatestVerifyLinkFromCredentials(String credentials);

    public VerifyLinkReadResult readLatestVerifyLink(String email, String clientId, String refreshToken);

    public RegistrationMailCheckResult checkRegistrationMailTraceFromCredentials(String credentials);

    public RegistrationMailCheckResult checkRegistrationMailTrace(String email, String clientId, String refreshToken);
}
