package com.example.ShoppingSystem.controller.login.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.IOException;

/**
 * OAuth2 登录入口控制器。
 * 仅负责把前端自定义入口转发到 Spring Security 标准入口。
 */
@Tag(name = "第三方登录入口", description = "GitHub/Google/Microsoft 登录发起入口")
@Controller
public class OAuth2LoginEntryController {

    private static final String AUTHORIZATION_PATH_PREFIX = "/oauth2/authorization/";

    @Operation(summary = "发起 GitHub 登录", description = "302 跳转到 Spring Security 标准授权入口")
    @ApiResponses({@ApiResponse(responseCode = "302", description = "重定向到 /oauth2/authorization/github")})
    @GetMapping("/oauth2/github/login")
    public void loginByGithub(HttpServletResponse response) throws IOException {
        redirectToAuthorization(response, "github");
    }

    @Operation(summary = "发起 Google 登录", description = "302 跳转到 Spring Security 标准授权入口")
    @ApiResponses({@ApiResponse(responseCode = "302", description = "重定向到 /oauth2/authorization/google")})
    @GetMapping("/oauth2/google/login")
    public void loginByGoogle(HttpServletResponse response) throws IOException {
        redirectToAuthorization(response, "google");
    }

    @Operation(summary = "发起 Microsoft 登录", description = "302 跳转到 Spring Security 标准授权入口")
    @ApiResponses({@ApiResponse(responseCode = "302", description = "重定向到 /oauth2/authorization/microsoft")})
    @GetMapping("/oauth2/microsoft/login")
    public void loginByMicrosoft(HttpServletResponse response) throws IOException {
        redirectToAuthorization(response, "microsoft");
    }

    private void redirectToAuthorization(HttpServletResponse response, String registrationId) throws IOException {
        response.sendRedirect(AUTHORIZATION_PATH_PREFIX + registrationId);
    }
}
