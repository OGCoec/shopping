package com.example.ShoppingSystem.admin.service.auth;

import com.example.ShoppingSystem.admin.dto.AdminLoginRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
public interface AdminLoginService {
    public void login(AdminLoginRequest request,
                      HttpServletRequest httpServletRequest,
                      HttpServletResponse httpServletResponse);
}
