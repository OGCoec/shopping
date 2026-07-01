package com.example.ShoppingSystem.security;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
public interface RegisterPasswordCryptoService {
    public record PasswordCryptoKey(String kid,
                                        String alg,
                                        Map<String, Object> publicKeyJwk,
                                        long expiresAtEpochMillis) {
        }

    public record DecryptOutcome(boolean success,
                                     String rawPassword,
                                     String message) {
            public static DecryptOutcome success(String rawPassword) {
                return new DecryptOutcome(true, rawPassword, "ok");
            }

            public static DecryptOutcome failed(String message) {
                return new DecryptOutcome(false, null, message);
            }
        }

    public boolean isEnabled();

    public PasswordCryptoKey issuePasswordCryptoKey();

    public DecryptOutcome decryptPasswordCipher(String kid,
                                                String passwordCipher,
                                                String nonce,
                                                Long timestamp,
                                                HttpServletRequest request);
}
