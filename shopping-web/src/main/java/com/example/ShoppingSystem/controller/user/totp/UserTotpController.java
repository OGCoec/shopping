package com.example.ShoppingSystem.controller.user.totp;

import com.example.ShoppingSystem.controller.user.totp.dto.TotpSetupConfirmRequest;
import com.example.ShoppingSystem.controller.user.totp.dto.TotpSetupStartResponse;
import com.example.ShoppingSystem.controller.user.totp.dto.TotpVerifyRequest;
import com.example.ShoppingSystem.controller.user.totp.dto.TotpVerifyResponse;
import com.example.ShoppingSystem.service.user.auth.totp.UserTotpService;
import com.example.ShoppingSystem.service.user.auth.totp.model.TotpSetupStartResult;
import com.example.ShoppingSystem.service.user.auth.totp.model.TotpVerificationResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/shopping/user/totp")
public class UserTotpController {

    public static final String AUTH_USER_ID_SESSION_ATTRIBUTE = "AUTH_USER_ID";

    private final UserTotpService userTotpService;

    public UserTotpController(UserTotpService userTotpService) {
        this.userTotpService = userTotpService;
    }

    @PostMapping("/setup")
    public ResponseEntity<TotpSetupStartResponse> startSetup(Authentication authentication,
                                                            HttpServletRequest request) {
        Long userId = requireCurrentUserId(authentication, request);
        TotpSetupStartResult result = userTotpService.startSetup(userId);
        return ResponseEntity.ok(TotpSetupStartResponse.from(result));
    }

    @PostMapping("/setup/confirm")
    public ResponseEntity<TotpVerifyResponse> confirmSetup(@RequestBody TotpSetupConfirmRequest body,
                                                           Authentication authentication,
                                                           HttpServletRequest request) {
        Long userId = requireCurrentUserId(authentication, request);
        TotpVerificationResult result = userTotpService.confirmSetup(userId, body.code());
        return ResponseEntity.status(result.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_REQUEST)
                .body(TotpVerifyResponse.from(result));
    }

    @PostMapping("/verify")
    public ResponseEntity<TotpVerifyResponse> verify(@RequestBody TotpVerifyRequest body,
                                                     Authentication authentication,
                                                     HttpServletRequest request) {
        Long userId = requireCurrentUserId(authentication, request);
        TotpVerificationResult result = userTotpService.verify(userId, body.code());
        return ResponseEntity.status(result.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_REQUEST)
                .body(TotpVerifyResponse.from(result));
    }

    @DeleteMapping
    public ResponseEntity<TotpVerifyResponse> disable(Authentication authentication,
                                                     HttpServletRequest request) {
        Long userId = requireCurrentUserId(authentication, request);
        boolean disabled = userTotpService.disable(userId);
        return ResponseEntity.ok(new TotpVerifyResponse(disabled, disabled ? "TOTP disabled." : "Failed to disable TOTP.", null));
    }

    private Long requireCurrentUserId(Authentication authentication, HttpServletRequest request) {
        Long sessionUserId = resolveSessionUserId(request);
        if (sessionUserId != null) {
            return sessionUserId;
        }

        if (authentication != null && authentication.isAuthenticated()) {
            Long authenticationNameUserId = parseUserId(authentication.getName());
            if (authenticationNameUserId != null) {
                return authenticationNameUserId;
            }
        }

        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Current user is not authenticated.");
    }

    private Long resolveSessionUserId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object value = session.getAttribute(AUTH_USER_ID_SESSION_ATTRIBUTE);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            return parseUserId(text);
        }
        return null;
    }

    private Long parseUserId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
