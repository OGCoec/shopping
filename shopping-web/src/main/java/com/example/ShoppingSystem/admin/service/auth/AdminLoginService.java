package com.example.ShoppingSystem.admin.service.auth;

import com.example.ShoppingSystem.admin.dto.AdminLoginRequest;
import com.example.ShoppingSystem.admin.model.AdminAccount;
import com.example.ShoppingSystem.security.RegisterPasswordCryptoService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.example.ShoppingSystem.admin.service.common.AdminServiceException;
import com.example.ShoppingSystem.admin.service.config.AdminConfigService;

public interface AdminLoginService {
    public void login(AdminLoginRequest request,
                      HttpServletRequest httpServletRequest,
                      HttpServletResponse httpServletResponse);
}
