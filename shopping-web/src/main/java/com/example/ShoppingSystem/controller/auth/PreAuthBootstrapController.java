package com.example.ShoppingSystem.controller.auth;

import com.example.ShoppingSystem.controller.auth.dto.PreAuthBootstrapResponse;
import com.example.ShoppingSystem.controller.auth.dto.PreAuthPhoneCountryResponse;
import com.example.ShoppingSystem.controller.auth.dto.PreAuthPhoneValidationRequest;
import com.example.ShoppingSystem.controller.auth.dto.PreAuthPhoneValidationResponse;
import com.example.ShoppingSystem.controller.auth.dto.RegisterPasswordCryptoKeyResponse;
import com.example.ShoppingSystem.filter.preauth.PreAuthBindingService;
import com.example.ShoppingSystem.filter.preauth.PreAuthHeaders;
import com.example.ShoppingSystem.filter.preauth.model.PreAuthBootstrapOutcome;
import com.example.ShoppingSystem.filter.preauth.model.PreAuthSnapshot;
import com.example.ShoppingSystem.filter.preauth.model.PreAuthValidationError;
import com.example.ShoppingSystem.phone.PhoneNumberValidationService;
import com.example.ShoppingSystem.quota.IpCountryQueryService;
import com.example.ShoppingSystem.security.RegisterPasswordCryptoService;
import com.example.ShoppingSystem.service.user.auth.phone.PhoneBoundCountingBloomService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 妫板嫮娅ヨぐ鏇礄PreAuth閿涘绱╃€靛吋甯堕崚璺烘珤閵? */
@RestController
@RequestMapping("/shopping/auth/preauth")
public class PreAuthBootstrapController {

    private static final String PHONE_VALIDATION_PURPOSE_BIND_PHONE = "BIND_PHONE";
    private static final String REASON_PHONE_ALREADY_BOUND = "PHONE_ALREADY_BOUND";
    private static final String REASON_PHONE_BOUND_BLOOM_UNAVAILABLE = "PHONE_BOUND_BLOOM_UNAVAILABLE";

    private final PreAuthBindingService preAuthBindingService;
    private final IpCountryQueryService ipCountryQueryService;
    private final RegisterPasswordCryptoService registerPasswordCryptoService;
    private final PhoneNumberValidationService phoneNumberValidationService;
    private final PhoneBoundCountingBloomService phoneBoundCountingBloomService;

    public PreAuthBootstrapController(PreAuthBindingService preAuthBindingService,
                                      IpCountryQueryService ipCountryQueryService,
                                      RegisterPasswordCryptoService registerPasswordCryptoService,
                                      PhoneNumberValidationService phoneNumberValidationService,
                                      PhoneBoundCountingBloomService phoneBoundCountingBloomService) {
        this.preAuthBindingService = preAuthBindingService;
        this.ipCountryQueryService = ipCountryQueryService;
        this.registerPasswordCryptoService = registerPasswordCryptoService;
        this.phoneNumberValidationService = phoneNumberValidationService;
        this.phoneBoundCountingBloomService = phoneBoundCountingBloomService;
    }

    /**
     * 妫板嫮娅ヨぐ鏇炵穿鐎靛吋甯撮崣锝冣偓?     * 瑜版挻顥呭ù瀣煂閸?token 閹稿洨姹?UA 娑撯偓閼风繝绲?IP 閸欐ê瀵叉稉鏃€婀€瑰本鍨?WAF 妤犲矁鐦夐弮璁圭礉鏉╂柨娲?409 + verifyUrl閵?     */
    @PostMapping("/bootstrap")
    public ResponseEntity<?> bootstrap(
            @RequestHeader(value = PreAuthHeaders.HEADER_DEVICE_FINGERPRINT, required = false) String fingerprint,
            HttpServletRequest request,
            HttpServletResponse response) {
        String incomingToken = preAuthBindingService.resolveIncomingToken(request);
        PreAuthBootstrapOutcome outcome = preAuthBindingService.bootstrap(incomingToken, fingerprint, request);

        if (!outcome.allowed()) {
            if (outcome.error() == PreAuthValidationError.IP_CHANGED_WAF_REQUIRED) {
                response.addHeader("Set-Cookie", preAuthBindingService.buildWafRequiredCookie(request).toString());
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(buildWafRequiredBody(request));
            }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(buildBootstrapErrorBody(request, "PREAUTH_INVALID", "PREAUTH_INVALID", HttpServletResponse.SC_UNAUTHORIZED));
        }

        PreAuthSnapshot snapshot = outcome.snapshot();
        if (preAuthBindingService.isEnabled()) {
            response.addHeader("Set-Cookie", preAuthBindingService.buildTokenCookie(snapshot.token(), request).toString());
        }

        RegisterPasswordCryptoService.PasswordCryptoKey passwordCryptoKey = registerPasswordCryptoService.issuePasswordCryptoKey();
        return ResponseEntity.ok(new PreAuthBootstrapResponse(
                true,
                "ok",
                null,
                snapshot.expiresAtEpochMillis(),
                snapshot.riskLevel(),
                snapshot.challengeRequired(),
                snapshot.blocked(),
                new RegisterPasswordCryptoKeyResponse(
                        passwordCryptoKey.kid(),
                        passwordCryptoKey.alg(),
                        passwordCryptoKey.publicKeyJwk(),
                        passwordCryptoKey.expiresAtEpochMillis())
        ));
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

    @PostMapping("/phone-validate")
    public PreAuthPhoneValidationResponse validatePhoneNumber(@RequestBody PreAuthPhoneValidationRequest request) {
        PhoneNumberValidationService.ValidationResult result =
                phoneNumberValidationService.validateMobileLikeNumber(request.dialCode(), request.phoneNumber());
        if (result.allowed()) {
            if (shouldRejectAlreadyBoundPhone(request)) {
                PhoneBoundCountingBloomService.PhoneBoundLookupResult lookupResult =
                        phoneBoundCountingBloomService.lookupVerifiedPhone(result.normalizedE164());
                if (!lookupResult.available()) {
                    return new PreAuthPhoneValidationResponse(
                            false,
                            resolvePhoneValidationMessage(REASON_PHONE_BOUND_BLOOM_UNAVAILABLE),
                            REASON_PHONE_BOUND_BLOOM_UNAVAILABLE,
                            result.phoneType(),
                            result.normalizedE164());
                }
                if (lookupResult.mightContain()) {
                    return new PreAuthPhoneValidationResponse(
                            false,
                            resolvePhoneValidationMessage(REASON_PHONE_ALREADY_BOUND),
                            REASON_PHONE_ALREADY_BOUND,
                            result.phoneType(),
                            result.normalizedE164());
                }
            }
            return new PreAuthPhoneValidationResponse(
                    true,
                    "ok",
                    result.reasonCode(),
                    result.phoneType(),
                    result.normalizedE164());
        }
        return new PreAuthPhoneValidationResponse(
                false,
                resolvePhoneValidationMessage(result.reasonCode()),
                result.reasonCode(),
                result.phoneType(),
                result.normalizedE164());
    }

    private Map<String, Object> buildWafRequiredBody(HttpServletRequest request) {
        Map<String, Object> body = buildBootstrapErrorBody(
                request,
                "PREAUTH_IP_CHANGED_WAF_REQUIRED",
                "Network changed, please complete WAF verification before retry",
                HttpServletResponse.SC_CONFLICT);
        body.put("verifyUrl", buildBootstrapWafVerifyUrl(request));
        return body;
    }

    private Map<String, Object> buildBootstrapErrorBody(HttpServletRequest request,
                                                        String errorCode,
                                                        String message,
                                                        int status) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("status", status);
        body.put("error", errorCode);
        body.put("message", message);
        body.put("path", request.getRequestURI());
        body.put("timestamp", OffsetDateTime.now().toString());
        return body;
    }

    private String buildBootstrapWafVerifyUrl(HttpServletRequest request) {
        String returnPath = buildReturnPathFromReferer(request);
        if (returnPath == null || returnPath.isBlank()) {
            returnPath = "/shopping/user/log-in";
        }
        return "/shopping/auth/waf/verify?return="
                + URLEncoder.encode(returnPath, StandardCharsets.UTF_8);
    }

    private String buildReturnPathFromReferer(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        if (referer == null || referer.isBlank()) {
            return null;
        }
        try {
            URI refererUri = URI.create(referer.trim());
            String path = refererUri.getPath();
            if (path == null || path.isBlank() || !path.startsWith("/") || path.startsWith("//")) {
                return null;
            }
            if (path.startsWith("/shopping/auth/waf/verify")) {
                return null;
            }
            String query = refererUri.getQuery();
            return (query == null || query.isBlank()) ? path : path + "?" + query;
        } catch (Exception ignored) {
            return null;
        }
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

    private String resolvePhoneValidationMessage(String reasonCode) {
        if (reasonCode == null) {
            return "phone validation failed";
        }
        return switch (reasonCode) {
            case PhoneNumberValidationService.REASON_VOIP_NOT_ALLOWED -> "voip phone number is not allowed";
            case PhoneNumberValidationService.REASON_FIXED_LINE_NOT_ALLOWED -> "fixed-line phone number is not allowed";
            case PhoneNumberValidationService.REASON_TYPE_NOT_ALLOWED -> "only mobile phone numbers are allowed";
            case PhoneNumberValidationService.REASON_INVALID_DIAL_CODE -> "invalid dial code";
            case PhoneNumberValidationService.REASON_INVALID_PHONE -> "invalid phone number";
            case REASON_PHONE_ALREADY_BOUND -> "phone number is already in use";
            case REASON_PHONE_BOUND_BLOOM_UNAVAILABLE -> "phone existence filter is temporarily unavailable";
            default -> "phone validation failed";
        };
    }

    private boolean shouldRejectAlreadyBoundPhone(PreAuthPhoneValidationRequest request) {
        return request != null
                && request.purpose() != null
                && PHONE_VALIDATION_PURPOSE_BIND_PHONE.equalsIgnoreCase(request.purpose().trim());
    }
}
