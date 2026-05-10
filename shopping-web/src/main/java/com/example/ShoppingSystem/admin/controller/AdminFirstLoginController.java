package com.example.ShoppingSystem.admin.controller;

import com.example.ShoppingSystem.admin.dto.AdminApiResponse;
import com.example.ShoppingSystem.admin.dto.AdminEmailCodeResponse;
import com.example.ShoppingSystem.admin.dto.AdminFirstLoginCompleteRequest;
import com.example.ShoppingSystem.admin.dto.AdminFirstLoginEmailCodeRequest;
import com.example.ShoppingSystem.admin.dto.AdminRedirectResponse;
import com.example.ShoppingSystem.admin.service.AdminFirstLoginService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/shopping/admin/firstlogin")
public class AdminFirstLoginController {

    private static final String ADMIN_LOGIN_PATH = "/shopping/admin/login";

    private final AdminFirstLoginService adminFirstLoginService;

    public AdminFirstLoginController(AdminFirstLoginService adminFirstLoginService) {
        this.adminFirstLoginService = adminFirstLoginService;
    }

    @PostMapping("/email-code")
    public AdminApiResponse<AdminEmailCodeResponse> sendEmailCode(@RequestBody AdminFirstLoginEmailCodeRequest request) {
        return AdminApiResponse.ok(adminFirstLoginService.sendEmailCode(request == null ? null : request.email()));
    }

    @PostMapping("/complete")
    public AdminApiResponse<AdminRedirectResponse> complete(@RequestBody AdminFirstLoginCompleteRequest request) {
        adminFirstLoginService.complete(request);
        return AdminApiResponse.ok(new AdminRedirectResponse(ADMIN_LOGIN_PATH));
    }
}
