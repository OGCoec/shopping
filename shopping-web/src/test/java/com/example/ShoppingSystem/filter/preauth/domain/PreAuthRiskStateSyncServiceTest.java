package com.example.ShoppingSystem.filter.preauth.domain;

import com.example.ShoppingSystem.filter.preauth.model.PreAuthBinding;
import com.example.ShoppingSystem.filter.preauth.support.PreAuthProperties;
import com.example.ShoppingSystem.filter.preauth.support.PreAuthRequestResolver;
import com.example.ShoppingSystem.loginflow.LoginFlowCookieFactory;
import com.example.ShoppingSystem.redisdata.LoginRedisKeys;
import com.example.ShoppingSystem.redisdata.RegisterRedisKeys;
import com.example.ShoppingSystem.registerflow.RegisterFlowCookieFactory;
import com.example.ShoppingSystem.service.user.auth.login.LoginFlowProperties;
import com.example.ShoppingSystem.service.user.auth.login.LoginFlowSessionService;
import com.example.ShoppingSystem.service.user.auth.login.impl.LoginChallengePolicy;
import com.example.ShoppingSystem.service.user.auth.login.impl.LoginChallengeSessionService;
import com.example.ShoppingSystem.service.user.auth.login.model.LoginFactor;
import com.example.ShoppingSystem.service.user.auth.login.model.LoginFlowSession;
import com.example.ShoppingSystem.service.user.auth.register.RegisterFlowProperties;
import com.example.ShoppingSystem.service.user.auth.register.RegisterFlowSessionService;
import com.example.ShoppingSystem.service.user.auth.register.impl.ChallengeSessionService;
import com.example.ShoppingSystem.service.user.auth.register.model.RegisterFlowSession;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PreAuthRiskStateSyncServiceTest {

    private static final String TOKEN = "preauth-token";
    private static final String DEVICE_FINGERPRINT = "device-fingerprint";

    private final StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
    private final LoginFlowSessionService loginFlowSessionService = mock(LoginFlowSessionService.class);
    private final RegisterFlowSessionService registerFlowSessionService = mock(RegisterFlowSessionService.class);
    private final LoginChallengeSessionService loginChallengeSessionService = mock(LoginChallengeSessionService.class);
    private final ChallengeSessionService registerChallengeSessionService = mock(ChallengeSessionService.class);
    private final LoginChallengePolicy loginChallengePolicy = mock(LoginChallengePolicy.class);
    private final PreAuthRiskStateSyncService service = new PreAuthRiskStateSyncService(
            stringRedisTemplate,
            new LoginFlowCookieFactory(new LoginFlowProperties(), new PreAuthRequestResolver(new PreAuthProperties())),
            new RegisterFlowCookieFactory(new RegisterFlowProperties(), new PreAuthRequestResolver(new PreAuthProperties())),
            loginFlowSessionService,
            registerFlowSessionService,
            loginChallengeSessionService,
            registerChallengeSessionService,
            loginChallengePolicy
    );

    @Test
    void clearsLoginDerivedKeysWhenScoreChangesInsideSameRiskLevel() {
        String flowId = "login-flow";
        String email = "user@example.com";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("LOGIN_FLOW_ID", flowId));
        when(loginFlowSessionService.readFlow(flowId)).thenReturn(LoginFlowSession.builder()
                .flowId(flowId)
                .preAuthToken(TOKEN)
                .deviceFingerprint(DEVICE_FINGERPRINT)
                .email(email)
                .riskLevel("L3")
                .availableFactors(Set.of(LoginFactor.EMAIL_OTP))
                .requiredFactorCount(1)
                .build());

        service.syncAfterBindingSaved(binding(6100, "L3"), binding(6050, "L3"), request);

        verify(loginFlowSessionService, never()).updateRiskLevel(flowId, "L3", 1, false);
        verify(loginChallengeSessionService).clearPendingChallengeSelection(email, DEVICE_FINGERPRINT);
        verify(stringRedisTemplate).delete(LoginRedisKeys.EMAIL_CODE_PREFIX + flowId);
        verify(loginChallengeSessionService).consumeWafVerified(TOKEN);
    }

    @Test
    void clearsRegisterDerivedKeysWhenScoreChangesInsideSameRiskLevel() {
        String flowId = "register-flow";
        String email = "new@example.com";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("REGISTER_FLOW_ID", flowId));
        when(registerFlowSessionService.readFlow(flowId)).thenReturn(RegisterFlowSession.builder()
                .flowId(flowId)
                .preAuthToken(TOKEN)
                .deviceFingerprint(DEVICE_FINGERPRINT)
                .email(email)
                .riskLevel("L3")
                .requirePhoneBinding(true)
                .build());

        service.syncAfterBindingSaved(binding(6100, "L3"), binding(6050, "L3"), request);

        verify(registerFlowSessionService, never()).updateRiskLevel(flowId, "L3", true);
        verify(registerChallengeSessionService).clearPendingChallengeSelection(email, DEVICE_FINGERPRINT);
        verify(stringRedisTemplate).delete(RegisterRedisKeys.EMAIL_CODE_PREFIX + email);
        verify(stringRedisTemplate).delete(RegisterRedisKeys.EMAIL_CODE_META_PREFIX + email);
        verify(stringRedisTemplate).delete(RegisterRedisKeys.EMAIL_CODE_CHALLENGE_PASSED_PREFIX + flowId);
        verify(loginChallengeSessionService).consumeWafVerified(TOKEN);
    }

    private PreAuthBinding binding(int totalScore, String riskLevel) {
        return new PreAuthBinding(
                TOKEN,
                "fp-hash",
                "ua-hash",
                "203.0.113.10",
                List.of("203.0.113.10"),
                0,
                6000,
                totalScore,
                totalScore,
                riskLevel,
                System.currentTimeMillis(),
                "US",
                "California",
                "Los Angeles",
                null,
                null,
                0,
                "",
                0L,
                0,
                ""
        );
    }
}
