package com.example.ShoppingSystem.controller.auth;

import com.example.ShoppingSystem.security.token.AuthUserContext;
import com.example.ShoppingSystem.security.token.AuthUserContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/shopping/user/session")
public class PageAccessGateController {

    @GetMapping("/page-gate")
    public PageGateResponse pageGate() {
        AuthUserContext context = AuthUserContextHolder.get();
        return new PageGateResponse(true, context == null ? null : context.riskLevel());
    }

    public record PageGateResponse(boolean success, String riskLevel) {
    }
}
