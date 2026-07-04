package com.example.ShoppingSystem.controller.user.totp;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.ShoppingSystem.controller.user.totp.dto.TotpSetupConfirmRequest;
import com.example.ShoppingSystem.controller.user.totp.dto.TotpSetupStartResponse;
import com.example.ShoppingSystem.controller.user.totp.dto.TotpVerifyRequest;
import com.example.ShoppingSystem.controller.user.totp.dto.TotpVerifyResponse;
import com.example.ShoppingSystem.security.token.AuthUserContext;
import com.example.ShoppingSystem.service.user.auth.totp.UserTotpService;
import com.example.ShoppingSystem.service.user.auth.totp.model.TotpSetupStartResult;
import com.example.ShoppingSystem.service.user.auth.totp.model.TotpVerificationResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Tag(name = "用户动态口令", description = "用户动态口令设置和验证接口")
@RestController
@RequestMapping("/shopping/user/totp")
public class UserTotpController {

    public static final String AUTH_USER_ID_SESSION_ATTRIBUTE = "AUTH_USER_ID";

    private final UserTotpService userTotpService;

    public UserTotpController(UserTotpService userTotpService) {
        this.userTotpService = userTotpService;
    }

    @Operation(summary = "查询动态口令状态")
    @GetMapping("/status")
    public ResponseEntity<TotpStatusResponse> status(Authentication authentication,
                                                     HttpServletRequest request) {
        Long userId = requireCurrentUserId(authentication, request);
        return ResponseEntity.ok(new TotpStatusResponse(true, userTotpService.isEnabled(userId)));
    }

    @Operation(summary = "开始设置动态口令")
    @PostMapping("/setup")
    public ResponseEntity<TotpSetupStartResponse> startSetup(Authentication authentication,
                                                            HttpServletRequest request) {
        Long userId = requireCurrentUserId(authentication, request);
        TotpSetupStartResult result = userTotpService.startSetup(userId);
        return ResponseEntity.ok(TotpSetupStartResponse.from(result));
    }

    @Operation(summary = "确认设置动态口令")
    @PostMapping("/setup/confirm")
    public ResponseEntity<TotpVerifyResponse> confirmSetup(@RequestBody TotpSetupConfirmRequest body,
                                                           Authentication authentication,
                                                           HttpServletRequest request) {
        Long userId = requireCurrentUserId(authentication, request);
        TotpVerificationResult result = userTotpService.confirmSetup(userId, body.code());
        return ResponseEntity.status(result.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_REQUEST)
                .body(TotpVerifyResponse.from(result));
    }

    @Operation(summary = "验证动态口令")
    @PostMapping("/verify")
    public ResponseEntity<TotpVerifyResponse> verify(@RequestBody TotpVerifyRequest body,
                                                     Authentication authentication,
                                                     HttpServletRequest request) {
        Long userId = requireCurrentUserId(authentication, request);
        TotpVerificationResult result = userTotpService.verify(userId, body.code());
        return ResponseEntity.status(result.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_REQUEST)
                .body(TotpVerifyResponse.from(result));
    }

    @Operation(summary = "关闭动态口令")
    @DeleteMapping
    public ResponseEntity<TotpVerifyResponse> disable(Authentication authentication,
                                                     HttpServletRequest request) {
        Long userId = requireCurrentUserId(authentication, request);
        boolean disabled = userTotpService.disable(userId);
        return ResponseEntity.ok(new TotpVerifyResponse(disabled, disabled ? "TOTP disabled." : "Failed to disable TOTP.", null));
    }

    private Long requireCurrentUserId(Authentication authentication, HttpServletRequest request) {
        Long requestUserId = resolveRequestUserId(request);
        if (requestUserId != null) {
            return requestUserId;
        }

        Long sessionUserId = resolveSessionUserId(request);
        if (sessionUserId != null) {
            return sessionUserId;
        }

        if (authentication != null && authentication.isAuthenticated()) {
            if (authentication.getPrincipal() instanceof AuthUserContext context && context.userId() != null) {
                return context.userId();
            }
            Long authenticationNameUserId = parseUserId(authentication.getName());
            if (authenticationNameUserId != null) {
                return authenticationNameUserId;
            }
        }

        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Current user is not authenticated.");
    }

    private Long resolveRequestUserId(HttpServletRequest request) {
        Object value = request == null ? null : request.getAttribute("authUserId");
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            return parseUserId(text);
        }
        return null;
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

    public record TotpStatusResponse(boolean success, boolean enabled) {
    }
}
