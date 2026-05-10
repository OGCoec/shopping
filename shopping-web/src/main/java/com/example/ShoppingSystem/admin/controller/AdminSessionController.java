package com.example.ShoppingSystem.admin.controller;

import com.example.ShoppingSystem.admin.dto.AdminApiResponse;
import com.example.ShoppingSystem.admin.dto.AdminRedirectResponse;
import com.example.ShoppingSystem.admin.dto.AdminSessionMeResponse;
import com.example.ShoppingSystem.admin.service.AdminSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/shopping/admin")
public class AdminSessionController {

    private static final String ADMIN_LOGIN_PATH = "/shopping/admin/login";

    private final AdminSessionService adminSessionService;

    public AdminSessionController(AdminSessionService adminSessionService) {
        this.adminSessionService = adminSessionService;
    }

    @GetMapping("/session/me")
    public AdminApiResponse<AdminSessionMeResponse> current(HttpServletRequest request) {
        return AdminApiResponse.ok(adminSessionService.current(request));
    }

    @PostMapping("/logout")
    public AdminApiResponse<AdminRedirectResponse> logout(HttpServletRequest request,
                                                          HttpServletResponse response) {
        adminSessionService.logout(request, response);
        return AdminApiResponse.ok(new AdminRedirectResponse(ADMIN_LOGIN_PATH));
    }
}
