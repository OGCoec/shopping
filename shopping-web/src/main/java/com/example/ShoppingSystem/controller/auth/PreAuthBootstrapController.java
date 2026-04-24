package com.example.ShoppingSystem.controller.auth;

import com.example.ShoppingSystem.controller.auth.dto.PreAuthBootstrapResponse;
import com.example.ShoppingSystem.controller.auth.dto.PreAuthPhoneCountryResponse;
import com.example.ShoppingSystem.filter.preauth.PreAuthBindingService;
import com.example.ShoppingSystem.filter.preauth.PreAuthHeaders;
import com.example.ShoppingSystem.quota.IpCountryQueryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pre-auth bootstrap endpoint.
 */
@RestController
@RequestMapping("/shopping/auth/preauth")
public class PreAuthBootstrapController {

    private final PreAuthBindingService preAuthBindingService;
    private final IpCountryQueryService ipCountryQueryService;

    public PreAuthBootstrapController(PreAuthBindingService preAuthBindingService,
                                      IpCountryQueryService ipCountryQueryService) {
        this.preAuthBindingService = preAuthBindingService;
        this.ipCountryQueryService = ipCountryQueryService;
    }

    @PostMapping("/bootstrap")
    public PreAuthBootstrapResponse bootstrap(
            @RequestHeader(value = PreAuthHeaders.HEADER_DEVICE_FINGERPRINT, required = false) String fingerprint,
            HttpServletRequest request,
            HttpServletResponse response) {
        String incomingToken = preAuthBindingService.resolveIncomingToken(request);
        PreAuthBindingService.PreAuthSnapshot snapshot = preAuthBindingService.bootstrap(incomingToken, fingerprint, request);
        if (preAuthBindingService.isEnabled()) {
            response.addHeader("Set-Cookie", preAuthBindingService.buildTokenCookie(snapshot.token(), request).toString());
        }
        return new PreAuthBootstrapResponse(
                true,
                "ok",
                null,
                snapshot.expiresAtEpochMillis(),
                snapshot.riskLevel(),
                snapshot.challengeRequired(),
                snapshot.blocked()
        );
    }

    @GetMapping("/phone-country")
    public PreAuthPhoneCountryResponse resolvePhoneCountry(HttpServletRequest request) {
        String clientIp = resolveClientIp(request);
        IpCountryQueryService.CountryQueryResult result = ipCountryQueryService.queryCountry(clientIp);
        if (result.success()) {
            return new PreAuthPhoneCountryResponse(true, "ok", result.country(), result.source());
        }
        return new PreAuthPhoneCountryResponse(false, result.reason(), null, result.source());
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
