package com.example.ShoppingSystem.admin.service;

import com.example.ShoppingSystem.admin.dto.AdminLoginRequest;
import com.example.ShoppingSystem.admin.model.AdminAccount;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AdminLoginService {

    private final AdminConfigService adminConfigService;
    private final AdminSessionService adminSessionService;
    private final PasswordEncoder passwordEncoder;

    public AdminLoginService(AdminConfigService adminConfigService,
                             AdminSessionService adminSessionService,
                             PasswordEncoder passwordEncoder) {
        this.adminConfigService = adminConfigService;
        this.adminSessionService = adminSessionService;
        this.passwordEncoder = passwordEncoder;
    }

    public void login(AdminLoginRequest request,
                      HttpServletRequest httpServletRequest,
                      HttpServletResponse httpServletResponse) {
        AdminAccount account = adminConfigService.requireInitialized();
        String identifier = normalizeIdentifier(request == null ? null : request.identifier());
        String password = request == null || request.password() == null ? "" : request.password();
        if (identifier.isBlank() || password.isBlank()) {
            throw loginFailed();
        }
        if (!matchesIdentifier(account, identifier)
                || account.getPasswordHash() == null
                || account.getPasswordHash().isBlank()
                || !passwordEncoder.matches(password, account.getPasswordHash())) {
            throw loginFailed();
        }
        adminSessionService.authenticate(httpServletRequest, httpServletResponse, account);
    }

    private boolean matchesIdentifier(AdminAccount account, String identifier) {
        String normalizedEmail = normalizeIdentifier(account.getEmail());
        String normalizedUsername = normalizeIdentifier(account.getUsername());
        String normalizedPhone = normalizeIdentifier(account.getPhone()).replace(" ", "");
        return identifier.equals(normalizedEmail)
                || identifier.equals(normalizedUsername)
                || identifier.equals(normalizedPhone);
    }

    private AdminServiceException loginFailed() {
        return new AdminServiceException(
                "ADMIN_LOGIN_FAILED",
                "管理员账号或密码不正确。",
                HttpStatus.UNAUTHORIZED
        );
    }

    private String normalizeIdentifier(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
