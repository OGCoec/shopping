package com.example.ShoppingSystem.filter.preauth;
import com.example.ShoppingSystem.filter.preauth.model.PreAuthBinding;
import com.example.ShoppingSystem.filter.preauth.model.PreAuthBootstrapOutcome;
import com.example.ShoppingSystem.filter.preauth.model.PreAuthValidationOutcome;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseCookie;

public interface PreAuthBindingService {
    public PreAuthBootstrapOutcome bootstrap(String incomingToken,
                                             String rawFingerprint,
                                             HttpServletRequest request);

    public PreAuthValidationOutcome validateAndTouch(String token,
                                                     String rawFingerprint,
                                                     HttpServletRequest request);

    public boolean isEnabled();

    public String resolveIncomingToken(HttpServletRequest request);

    public String resolveClientIp(HttpServletRequest request);

    public boolean isRawL6BloomBlocked(String rawFingerprint, HttpServletRequest request);

    public PreAuthBinding markRawL6BloomBlocked(PreAuthBinding existing,
                                                String rawFingerprint,
                                                HttpServletRequest request);

    public ResponseCookie buildTokenCookie(String token, HttpServletRequest request);

    public ResponseCookie buildExpiredTokenCookie(HttpServletRequest request);

    public ResponseCookie buildWafRequiredCookie(HttpServletRequest request);

    public ResponseCookie buildClearWafRequiredCookie(HttpServletRequest request);

    public void refreshBindingForCurrentIpAfterWaf(String token, HttpServletRequest request);

    public boolean isBlockedRisk(String riskLevel);

    public boolean isChallengeRequired(String riskLevel);
}
