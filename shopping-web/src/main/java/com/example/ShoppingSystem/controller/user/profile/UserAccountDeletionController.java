package com.example.ShoppingSystem.controller.user.profile;

import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.security.token.AuthTokenService;
import com.example.ShoppingSystem.security.token.AuthUserContext;
import com.example.ShoppingSystem.security.token.AuthUserContextHolder;
import com.example.ShoppingSystem.service.user.profile.UserAccountDeletionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;

@RestController
@RequestMapping("/shopping/user/profile/deletion")
public class UserAccountDeletionController {

    private final UserAccountDeletionService userAccountDeletionService;
    private final AuthTokenService authTokenService;

    public UserAccountDeletionController(UserAccountDeletionService userAccountDeletionService,
                                         AuthTokenService authTokenService) {
        this.userAccountDeletionService = userAccountDeletionService;
        this.authTokenService = authTokenService;
    }

    @PostMapping
    public ResponseEntity<UserAccountDeletionResponse> submitDeletion(@RequestBody UserAccountDeletionRequest body,
                                                                      Authentication authentication,
                                                                      HttpServletRequest request,
                                                                      HttpServletResponse response) {
        AuthUserContext context = requireCurrentUser(authentication);
        String reason = normalizeReason(body == null ? null : body.deletionReason());
        try {
            userAccountDeletionService.submitSelfDeletionRequest(
                    context.userId(),
                    context.email(),
                    reason,
                    OffsetDateTime.now()
            );
            authTokenService.logoutCurrentDevice(request, response);
            return ResponseEntity.accepted()
                    .body(new UserAccountDeletionResponse(
                            true,
                            null,
                            "Account deletion request has been submitted."
                    ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest()
                    .body(new UserAccountDeletionResponse(false, "INVALID_DELETION_REQUEST", ex.getMessage()));
        }
    }

    private AuthUserContext requireCurrentUser(Authentication authentication) {
        AuthUserContext context = AuthUserContextHolder.get();
        if (context != null) {
            return context;
        }
        if (authentication != null && authentication.getPrincipal() instanceof AuthUserContext authUserContext) {
            return authUserContext;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Current user is not authenticated.");
    }

    private String normalizeReason(String value) {
        String normalized = StrUtil.trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException("Deletion reason is required.");
        }
        if (normalized.length() > 4000) {
            return normalized.substring(0, 4000);
        }
        return normalized;
    }

    public record UserAccountDeletionRequest(String deletionReason) {
    }

    public record UserAccountDeletionResponse(boolean success, String error, String message) {
    }
}
