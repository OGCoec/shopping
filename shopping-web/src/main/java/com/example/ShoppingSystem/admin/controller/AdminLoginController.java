package com.example.ShoppingSystem.admin.controller;

import com.example.ShoppingSystem.admin.dto.AdminApiResponse;
import com.example.ShoppingSystem.admin.dto.AdminLoginRequest;
import com.example.ShoppingSystem.admin.dto.AdminRedirectResponse;
import com.example.ShoppingSystem.controller.auth.dto.RegisterPasswordCryptoKeyResponse;
import com.example.ShoppingSystem.admin.service.AdminLoginService;
import com.example.ShoppingSystem.security.RegisterPasswordCryptoService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/shopping/admin")
public class AdminLoginController {

    private static final String ADMIN_CONSOLE_PATH = "/shopping/admin/console";

    private final AdminLoginService adminLoginService;
    private final RegisterPasswordCryptoService registerPasswordCryptoService;

    public AdminLoginController(AdminLoginService adminLoginService,
                                RegisterPasswordCryptoService registerPasswordCryptoService) {
        this.adminLoginService = adminLoginService;
        this.registerPasswordCryptoService = registerPasswordCryptoService;
    }

    @PostMapping("/password-crypto/key")
    public AdminApiResponse<RegisterPasswordCryptoKeyResponse> issuePasswordCryptoKey() {
        RegisterPasswordCryptoService.PasswordCryptoKey passwordCryptoKey = registerPasswordCryptoService.issuePasswordCryptoKey();
        return AdminApiResponse.ok(new RegisterPasswordCryptoKeyResponse(
                passwordCryptoKey.kid(),
                passwordCryptoKey.alg(),
                passwordCryptoKey.publicKeyJwk(),
                passwordCryptoKey.expiresAtEpochMillis()
        ));
    }

    @PostMapping("/login")
    public AdminApiResponse<AdminRedirectResponse> login(@RequestBody AdminLoginRequest request,
                                                         HttpServletRequest httpServletRequest,
                                                         HttpServletResponse httpServletResponse) {
        adminLoginService.login(request, httpServletRequest, httpServletResponse);
        return AdminApiResponse.ok(new AdminRedirectResponse(ADMIN_CONSOLE_PATH));
    }
}
