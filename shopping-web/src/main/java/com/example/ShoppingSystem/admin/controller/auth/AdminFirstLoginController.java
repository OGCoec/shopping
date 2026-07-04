package com.example.ShoppingSystem.admin.controller.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.ShoppingSystem.admin.dto.AdminApiResponse;
import com.example.ShoppingSystem.admin.dto.AdminEmailCodeResponse;
import com.example.ShoppingSystem.admin.dto.AdminFirstLoginCompleteRequest;
import com.example.ShoppingSystem.admin.dto.AdminFirstLoginEmailCodeRequest;
import com.example.ShoppingSystem.admin.dto.AdminRedirectResponse;
import com.example.ShoppingSystem.admin.service.auth.AdminFirstLoginService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "后台首次登录", description = "后台首次登录初始化接口")
@RestController
@RequestMapping("/shopping/admin/firstlogin")
public class AdminFirstLoginController {

    private static final String ADMIN_LOGIN_PATH = "/shopping/admin/login";

    private final AdminFirstLoginService adminFirstLoginService;

    public AdminFirstLoginController(AdminFirstLoginService adminFirstLoginService) {
        this.adminFirstLoginService = adminFirstLoginService;
    }

    @Operation(summary = "发送后台首次登录邮箱验证码")
    @PostMapping("/email-code")
    public AdminApiResponse<AdminEmailCodeResponse> sendEmailCode(@RequestBody AdminFirstLoginEmailCodeRequest request) {
        return AdminApiResponse.ok(adminFirstLoginService.sendEmailCode(request == null ? null : request.email()));
    }

    @Operation(summary = "完成后台首次登录设置")
    @PostMapping("/complete")
    public AdminApiResponse<AdminRedirectResponse> complete(@RequestBody AdminFirstLoginCompleteRequest request) {
        adminFirstLoginService.complete(request);
        return AdminApiResponse.ok(new AdminRedirectResponse(ADMIN_LOGIN_PATH));
    }
}
