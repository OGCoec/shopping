package com.example.ShoppingSystem.service.user.auth.passwordreset;

import com.example.ShoppingSystem.service.user.auth.passwordreset.model.PasswordResetCryptoKey;
import com.example.ShoppingSystem.service.user.auth.passwordreset.model.PasswordResetResult;

public interface PasswordResetService {

    PasswordResetResult issueCryptoKey();

    PasswordResetResult sendResetLink(String email,
                                      String preAuthToken,
                                      String deviceFingerprint,
                                      String riskLevel,
                                      boolean wafResumeRequest,
                                      String baseUrl);

    PasswordResetResult sendEmailCode(String email,
                                      String preAuthToken,
                                      String deviceFingerprint,
                                      String riskLevel,
                                      boolean wafResumeRequest,
                                      String baseUrl);

    PasswordResetResult resetByLink(String token,
                                    String deviceFingerprint,
                                    String clientIp,
                                    String kid,
                                    String payloadCipher,
                                    String nonce,
                                    Long timestamp);

    PasswordResetResult verifyEmailCode(String email,
                                        String code,
                                        String preAuthToken,
                                        String deviceFingerprint);

    PasswordResetResult resetByVerifiedCode(String token,
                                            String preAuthToken,
                                            String deviceFingerprint,
                                            String clientIp,
                                            String kid,
                                            String payloadCipher,
                                            String nonce,
                                            Long timestamp);

    void markWafVerified(String preAuthToken);

    boolean consumeWafVerified(String preAuthToken);

    boolean isResetLinkTokenUsable(String token);

    boolean isVerifiedCodeTokenUsable(String token, String preAuthToken);
}
