package com.example.ShoppingSystem.controller.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.ShoppingSystem.security.token.AuthTokenRefreshResult;
import com.example.ShoppingSystem.security.token.AuthTokenService;
import com.example.ShoppingSystem.security.token.AuthUserContext;
import com.example.ShoppingSystem.security.token.AuthUserContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "用户认证会话", description = "用户认证会话和令牌接口")
@RestController
@RequestMapping("/shopping/user/auth")
public class AuthSessionController {

    private final AuthTokenService authTokenService;

    public AuthSessionController(AuthTokenService authTokenService) {
        this.authTokenService = authTokenService;
    }

    @Operation(summary = "查询当前用户会话")
    @GetMapping("/me")
    public ResponseEntity<AuthMeResponse> currentUser(Authentication authentication) {
        AuthUserContext context = currentUserContext(authentication);
        if (context == null) {
            return ResponseEntity.status(HttpServletResponse.SC_UNAUTHORIZED)
                    .body(new AuthMeResponse(false, null));
        }
        return ResponseEntity.ok(new AuthMeResponse(true, context));
    }

    @Operation(summary = "刷新用户登录令牌")
    @PostMapping("/refresh")
    public ResponseEntity<AuthActionResponse> refresh(HttpServletRequest request,
                                                      HttpServletResponse response) {
        AuthTokenRefreshResult result = authTokenService.refresh(request, response);
        return ResponseEntity.status(result.status())
                .body(new AuthActionResponse(result.success(), result.error(), result.message()));
    }

    @Operation(summary = "退出当前用户会话")
    @PostMapping("/logout")
    public AuthActionResponse logoutCurrent(HttpServletRequest request,
                                            HttpServletResponse response) {
        authTokenService.logoutCurrentDevice(request, response);
        return new AuthActionResponse(true, null, "logged_out");
    }

    @Operation(summary = "退出全部用户会话")
    @PostMapping("/logout-all")
    public AuthActionResponse logoutAll(Authentication authentication,
                                        HttpServletRequest request,
                                        HttpServletResponse response) {
        Long userId = currentUserId(authentication);
        authTokenService.logoutAllDevices(userId, request, response);
        return new AuthActionResponse(true, null, "logged_out_all");
    }

    private Long currentUserId(Authentication authentication) {
        AuthUserContext context = currentUserContext(authentication);
        return context == null ? null : context.userId();
    }

    private AuthUserContext currentUserContext(Authentication authentication) {
        AuthUserContext context = AuthUserContextHolder.get();
        if (context != null) {
            return context;
        }
        if (authentication != null && authentication.getPrincipal() instanceof AuthUserContext authUserContext) {
            return authUserContext;
        }
        return null;
    }

    public record AuthMeResponse(boolean success, AuthUserContext user) {
    }

    public record AuthActionResponse(boolean success, String error, String message) {
    }
}
