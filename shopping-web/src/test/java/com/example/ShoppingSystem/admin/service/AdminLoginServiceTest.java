package com.example.ShoppingSystem.admin.service;

import com.example.ShoppingSystem.admin.dto.AdminLoginRequest;
import com.example.ShoppingSystem.admin.model.AdminAccount;
import com.example.ShoppingSystem.security.RegisterPasswordCryptoService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminLoginServiceTest {

    private final AdminConfigService adminConfigService = mock(AdminConfigService.class);
    private final AdminSessionService adminSessionService = mock(AdminSessionService.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final RegisterPasswordCryptoService registerPasswordCryptoService = mock(RegisterPasswordCryptoService.class);
    private final AdminLoginLockService adminLoginLockService = mock(AdminLoginLockService.class);
    private final AdminLoginService service = new AdminLoginService(
            adminConfigService,
            adminSessionService,
            passwordEncoder,
            registerPasswordCryptoService,
            adminLoginLockService
    );

    @Test
    void lockedAdminLoginStopsBeforePasswordDecryptAndVerify() {
        when(adminConfigService.requireInitialized()).thenReturn(adminAccount());
        when(adminLoginLockService.checkLocked(anyString()))
                .thenReturn(AdminLoginLockService.LockStatus.locked(1_800_000L));

        AdminServiceException ex = assertThrows(AdminServiceException.class, () ->
                service.login(loginRequest("admin", "secret"), mock(HttpServletRequest.class), mock(HttpServletResponse.class))
        );

        assertEquals("ADMIN_LOGIN_LOCKED", ex.getCode());
        assertEquals(HttpStatus.LOCKED, ex.getStatus());
        verify(registerPasswordCryptoService, never()).isEnabled();
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    void fifthWrongPasswordReturnsLocked() {
        when(adminConfigService.requireInitialized()).thenReturn(adminAccount());
        when(adminLoginLockService.checkLocked(anyString()))
                .thenReturn(AdminLoginLockService.LockStatus.open());
        when(registerPasswordCryptoService.isEnabled()).thenReturn(false);
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);
        when(adminLoginLockService.recordFailure(anyString(), any()))
                .thenReturn(AdminLoginLockService.FailureStatus.locked(1_800_000L, 5));

        AdminServiceException ex = assertThrows(AdminServiceException.class, () ->
                service.login(loginRequest("admin", "wrong"), mock(HttpServletRequest.class), mock(HttpServletResponse.class))
        );

        assertEquals("ADMIN_LOGIN_LOCKED", ex.getCode());
        assertEquals(HttpStatus.LOCKED, ex.getStatus());
        verify(adminLoginLockService).recordFailure(anyString(), any());
        verify(adminSessionService, never()).authenticate(any(), any(), any());
    }

    @Test
    void successfulLoginClearsFailuresAndAuthenticates() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        AdminAccount account = adminAccount();
        when(adminConfigService.requireInitialized()).thenReturn(account);
        when(adminLoginLockService.checkLocked(anyString()))
                .thenReturn(AdminLoginLockService.LockStatus.open());
        when(registerPasswordCryptoService.isEnabled()).thenReturn(false);
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);

        service.login(loginRequest("admin@example.com", "secret"), request, response);

        verify(adminLoginLockService).clearFailures(anyString());
        verify(adminSessionService).authenticate(request, response, account);
    }

    private AdminLoginRequest loginRequest(String identifier, String password) {
        return new AdminLoginRequest(identifier, password, null, null, null, null);
    }

    private AdminAccount adminAccount() {
        AdminAccount account = new AdminAccount();
        account.setInitialized(true);
        account.setUsername("admin");
        account.setEmail("admin@example.com");
        account.setPhone("13800138000");
        account.setPasswordHash("hash");
        return account;
    }
}
