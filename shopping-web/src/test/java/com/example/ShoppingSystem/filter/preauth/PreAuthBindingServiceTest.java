package com.example.ShoppingSystem.filter.preauth;

import com.example.ShoppingSystem.filter.preauth.domain.PreAuthBindingFactory;
import com.example.ShoppingSystem.filter.preauth.domain.PreAuthIpChangePenaltyService;
import com.example.ShoppingSystem.filter.preauth.domain.PreAuthRiskService;
import com.example.ShoppingSystem.filter.preauth.domain.PreAuthRiskStateSyncService;
import com.example.ShoppingSystem.filter.preauth.model.PreAuthBinding;
import com.example.ShoppingSystem.filter.preauth.model.PreAuthValidationError;
import com.example.ShoppingSystem.filter.preauth.model.PreAuthValidationOutcome;
import com.example.ShoppingSystem.filter.preauth.store.PreAuthBindingRepository;
import com.example.ShoppingSystem.filter.preauth.support.PreAuthCookieFactory;
import com.example.ShoppingSystem.filter.preauth.support.PreAuthHashingService;
import com.example.ShoppingSystem.filter.preauth.support.PreAuthProperties;
import com.example.ShoppingSystem.filter.preauth.support.PreAuthRequestResolver;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PreAuthBindingServiceTest {

    private static final String TOKEN = "preauth-token";
    private static final String IP = "203.0.113.10";

    private final PreAuthProperties properties = new PreAuthProperties();
    private final PreAuthRequestResolver requestResolver = new PreAuthRequestResolver(properties);
    private final PreAuthHashingService hashingService = new PreAuthHashingService();
    private final PreAuthBindingRepository bindingRepository = mock(PreAuthBindingRepository.class);
    private final PreAuthBindingService service = new PreAuthBindingService(
            properties,
            requestResolver,
            mock(PreAuthCookieFactory.class),
            hashingService,
            bindingRepository,
            mock(PreAuthRiskService.class),
            mock(PreAuthBindingFactory.class),
            mock(PreAuthIpChangePenaltyService.class),
            mock(PreAuthRiskStateSyncService.class)
    );

    @Test
    void fingerprintMismatchDoesNotDeleteExistingBinding() {
        when(bindingRepository.load(TOKEN)).thenReturn(binding("old-fingerprint", "stable-ua"));

        PreAuthValidationOutcome outcome = service.validateAndTouch(
                TOKEN,
                "new-fingerprint",
                request("stable-ua")
        );

        assertFalse(outcome.valid());
        assertEquals(PreAuthValidationError.FINGERPRINT_MISMATCH, outcome.error());
        verify(bindingRepository, never()).delete(anyString());
        verify(bindingRepository, never()).save(any());
    }

    @Test
    void userAgentMismatchDoesNotDeleteExistingBinding() {
        when(bindingRepository.load(TOKEN)).thenReturn(binding("stable-fingerprint", "old-ua"));

        PreAuthValidationOutcome outcome = service.validateAndTouch(
                TOKEN,
                "stable-fingerprint",
                request("new-ua")
        );

        assertFalse(outcome.valid());
        assertEquals(PreAuthValidationError.USER_AGENT_MISMATCH, outcome.error());
        verify(bindingRepository, never()).delete(anyString());
        verify(bindingRepository, never()).save(any());
    }

    private PreAuthBinding binding(String fingerprint, String userAgent) {
        return new PreAuthBinding(
                TOKEN,
                hashingService.sha256(fingerprint),
                hashingService.sha256(userAgent),
                IP,
                List.of(IP),
                0,
                6000,
                7000,
                6000,
                "L1",
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

    private MockHttpServletRequest request(String userAgent) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", userAgent);
        request.setRemoteAddr(IP);
        return request;
    }
}
