package com.example.ShoppingSystem.filter.preauth;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.filter.preauth.domain.PreAuthBindingFactory;
import com.example.ShoppingSystem.filter.preauth.domain.PreAuthIpChangePenaltyService;
import com.example.ShoppingSystem.filter.preauth.domain.PreAuthRiskService;
import com.example.ShoppingSystem.filter.preauth.domain.PreAuthRiskStateSyncService;
import com.example.ShoppingSystem.filter.preauth.model.PreAuthBinding;
import com.example.ShoppingSystem.filter.preauth.model.PreAuthBootstrapOutcome;
import com.example.ShoppingSystem.filter.preauth.model.PreAuthRiskProfile;
import com.example.ShoppingSystem.filter.preauth.model.PreAuthSnapshot;
import com.example.ShoppingSystem.filter.preauth.model.PreAuthValidationError;
import com.example.ShoppingSystem.filter.preauth.model.PreAuthValidationOutcome;
import com.example.ShoppingSystem.filter.preauth.store.PreAuthBindingRepository;
import com.example.ShoppingSystem.filter.preauth.support.PreAuthCookieFactory;
import com.example.ShoppingSystem.filter.preauth.support.PreAuthHashingService;
import com.example.ShoppingSystem.filter.preauth.support.PreAuthProperties;
import com.example.ShoppingSystem.filter.preauth.support.PreAuthRequestResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
