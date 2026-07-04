package com.example.ShoppingSystem.controller.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.ShoppingSystem.security.token.AuthUserContext;
import com.example.ShoppingSystem.security.token.AuthUserContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "用户页面访问校验", description = "用户页面访问状态校验接口")
@RestController
@RequestMapping("/shopping/user/session")
public class PageAccessGateController {

    @Operation(summary = "校验用户页面访问状态")
    @GetMapping("/page-gate")
    public PageGateResponse pageGate() {
        AuthUserContext context = AuthUserContextHolder.get();
        return new PageGateResponse(true, context == null ? null : context.riskLevel());
    }

    public record PageGateResponse(boolean success, String riskLevel) {
    }
}
