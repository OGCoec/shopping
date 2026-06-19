package com.example.ShoppingSystem.signin.controller;

import com.example.ShoppingSystem.security.token.AuthUserContext;
import com.example.ShoppingSystem.security.token.AuthUserContextHolder;
import com.example.ShoppingSystem.signin.dto.UserSignInResponse;
import com.example.ShoppingSystem.signin.dto.UserSignInStatusResponse;
import com.example.ShoppingSystem.signin.service.UserSignInService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/shopping/user/api/sign-in")
public class UserSignInController {

    private static final String AUTH_USER_ID_SESSION_ATTRIBUTE = "AUTH_USER_ID";

    private final UserSignInService userSignInService;

    public UserSignInController(UserSignInService userSignInService) {
        this.userSignInService = userSignInService;
    }

    @GetMapping("/status")
    public ResponseEntity<UserSignInStatusResponse> status(Authentication authentication,
                                                          HttpServletRequest request) {
        Long userId = currentUserId(authentication, request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(UserSignInStatusResponse.authRequired());
        }
        return ResponseEntity.ok(userSignInService.status(userId));
    }

    @PostMapping
    public ResponseEntity<UserSignInResponse> signIn(Authentication authentication,
                                                     HttpServletRequest request) {
        Long userId = currentUserId(authentication, request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(UserSignInResponse.authRequired());
        }
        return ResponseEntity.ok(userSignInService.signIn(userId));
    }

    private Long currentUserId(Authentication authentication, HttpServletRequest request) {
        AuthUserContext holderContext = AuthUserContextHolder.get();
        if (holderContext != null && holderContext.userId() != null) {
            return holderContext.userId();
        }

        Object requestUserId = request == null ? null : request.getAttribute("authUserId");
        Long parsedRequestUserId = parseUserId(requestUserId);
        if (parsedRequestUserId != null) {
            return parsedRequestUserId;
        }

        Long sessionUserId = resolveSessionUserId(request);
        if (sessionUserId != null) {
            return sessionUserId;
        }

        if (authentication != null && authentication.isAuthenticated()) {
            if (authentication.getPrincipal() instanceof AuthUserContext context && context.userId() != null) {
                return context.userId();
            }
            return parseUserId(authentication.getName());
        }
        return null;
    }

    private Long resolveSessionUserId(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        return parseUserId(session.getAttribute(AUTH_USER_ID_SESSION_ATTRIBUTE));
    }

    private Long parseUserId(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (!(value instanceof String text) || text.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
