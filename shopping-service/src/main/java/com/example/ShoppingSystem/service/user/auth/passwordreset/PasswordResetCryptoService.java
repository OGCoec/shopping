package com.example.ShoppingSystem.service.user.auth.passwordreset;
import com.example.ShoppingSystem.service.user.auth.passwordreset.model.PasswordResetCryptoKey;
import com.example.ShoppingSystem.service.user.auth.passwordreset.model.PasswordResetDecryptOutcome;
public interface PasswordResetCryptoService {
    public PasswordResetCryptoKey issueKey();

    public PasswordResetDecryptOutcome decryptPayload(String kid,
                                                      String payloadCipher,
                                                      String nonce,
                                                      Long timestamp);
}
