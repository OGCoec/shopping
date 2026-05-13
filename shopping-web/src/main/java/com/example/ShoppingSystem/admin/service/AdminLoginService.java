package com.example.ShoppingSystem.admin.service;

import com.example.ShoppingSystem.admin.dto.AdminLoginRequest;
import com.example.ShoppingSystem.admin.model.AdminAccount;
import com.example.ShoppingSystem.security.RegisterPasswordCryptoService;
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
    private final RegisterPasswordCryptoService registerPasswordCryptoService;
    private final AdminLoginLockService adminLoginLockService;

    public AdminLoginService(AdminConfigService adminConfigService,
                             AdminSessionService adminSessionService,
                             PasswordEncoder passwordEncoder,
                             RegisterPasswordCryptoService registerPasswordCryptoService,
                             AdminLoginLockService adminLoginLockService) {
        this.adminConfigService = adminConfigService;
        this.adminSessionService = adminSessionService;
        this.passwordEncoder = passwordEncoder;
        this.registerPasswordCryptoService = registerPasswordCryptoService;
        this.adminLoginLockService = adminLoginLockService;
    }

    public void login(AdminLoginRequest request,
                      HttpServletRequest httpServletRequest,
                      HttpServletResponse httpServletResponse) {
        AdminAccount account = adminConfigService.requireInitialized();
        String identifier = normalizeIdentifier(request == null ? null : request.identifier());
        if (identifier.isBlank()) {
            throw loginFailed();
        }

        String lockIdentifier = resolveLockIdentifier(account, identifier);
        AdminLoginLockService.LockStatus lockStatus = adminLoginLockService.checkLocked(lockIdentifier);
        if (lockStatus.locked()) {
            throw loginLocked(lockStatus.retryAfterMs());
        }

        String password = resolveSubmittedPassword(request, httpServletRequest);
        if (password.isBlank()) {
            throw loginFailed();
        }
        if (!matchesIdentifier(account, identifier)
                || account.getPasswordHash() == null
                || account.getPasswordHash().isBlank()
                || !passwordEncoder.matches(password, account.getPasswordHash())) {
            AdminLoginLockService.FailureStatus failureStatus = adminLoginLockService.recordFailure(lockIdentifier, httpServletRequest);
            if (failureStatus.locked()) {
                throw loginLocked(failureStatus.retryAfterMs());
            }
            throw loginFailed();
        }
        adminLoginLockService.clearFailures(lockIdentifier);
        adminSessionService.authenticate(httpServletRequest, httpServletResponse, account);
    }

    private String resolveSubmittedPassword(AdminLoginRequest request,
                                            HttpServletRequest httpServletRequest) {
        if (request == null) {
            return "";
        }
        if (!registerPasswordCryptoService.isEnabled()) {
            return request.password() == null ? "" : request.password();
        }
        RegisterPasswordCryptoService.DecryptOutcome decryptOutcome = registerPasswordCryptoService.decryptPasswordCipher(
                request.kid(),
                request.passwordCipher(),
                request.nonce(),
                request.timestamp(),
                httpServletRequest
        );
        if (!decryptOutcome.success()) {
            throw passwordCryptoFailed(decryptOutcome.message());
        }
        return decryptOutcome.rawPassword();
    }

    private boolean matchesIdentifier(AdminAccount account, String identifier) {
        String normalizedEmail = normalizeIdentifier(account.getEmail());
        String normalizedUsername = normalizeIdentifier(account.getUsername());
        String normalizedPhone = normalizeIdentifier(account.getPhone()).replace(" ", "");
        return identifier.equals(normalizedEmail)
                || identifier.equals(normalizedUsername)
                || identifier.equals(normalizedPhone);
    }

    private String resolveLockIdentifier(AdminAccount account, String identifier) {
        if (!matchesIdentifier(account, identifier)) {
            return "submitted:" + identifier;
        }
        return "admin-account:"
                + normalizeIdentifier(account.getEmail())
                + ":"
                + normalizeIdentifier(account.getUsername())
                + ":"
                + normalizeIdentifier(account.getPhone()).replace(" ", "");
    }

    private AdminServiceException loginFailed() {
        return new AdminServiceException(
                "ADMIN_LOGIN_FAILED",
                "管理员账号或密码不正确。",
                HttpStatus.UNAUTHORIZED
        );
    }

    private AdminServiceException passwordCryptoFailed(String message) {
        String finalMessage = message == null || message.isBlank()
                ? "Password encryption expired, please refresh and try again."
                : message;
        return new AdminServiceException(
                "ADMIN_PASSWORD_CRYPTO_FAILED",
                finalMessage,
                HttpStatus.BAD_REQUEST
        );
    }

    private AdminServiceException loginLocked(long retryAfterMs) {
        long retryAfterMinutes = Math.max(1L, (Math.max(1L, retryAfterMs) + 59_999L) / 60_000L);
        return new AdminServiceException(
                "ADMIN_LOGIN_LOCKED",
                "管理员登录失败次数过多，请 " + retryAfterMinutes + " 分钟后再试。",
                HttpStatus.LOCKED
        );
    }

    private String normalizeIdentifier(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
