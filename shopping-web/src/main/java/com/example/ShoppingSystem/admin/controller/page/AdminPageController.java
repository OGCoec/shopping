package com.example.ShoppingSystem.admin.controller.page;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.ShoppingSystem.admin.service.config.AdminConfigService;
import com.example.ShoppingSystem.admin.service.auth.AdminSessionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Tag(name = "后台页面", description = "后台管理页面路由接口")
@Controller
public class AdminPageController {

    private final AdminConfigService adminConfigService;
    private final AdminSessionService adminSessionService;

    public AdminPageController(AdminConfigService adminConfigService,
                               AdminSessionService adminSessionService) {
        this.adminConfigService = adminConfigService;
        this.adminSessionService = adminSessionService;
    }

    private static final String CONSOLE_BASE_PATH = "/shopping/admin/console";
    private static final int RETURN_TO_MAX_LENGTH = 512;

    @Operation(summary = "打开后台登录页面")
    @GetMapping("/shopping/admin/login")
    public Object adminLoginPage(HttpServletRequest request) {
        if (!adminConfigService.isInitialized()) {
            return "redirect:/shopping/admin/firstlogin";
        }
        if (adminSessionService.isAuthenticated(request)) {
            String returnTo = request.getParameter("returnTo");
            if (isAllowedConsoleReturnTo(returnTo)) {
                return "redirect:" + returnTo;
            }
            return "redirect:/shopping/admin/console";
        }
        return htmlPage("admin-login.html");
    }

    private boolean isAllowedConsoleReturnTo(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        if (value.length() > RETURN_TO_MAX_LENGTH) {
            return false;
        }
        if (!value.startsWith("/") || value.startsWith("//")) {
            return false;
        }
        int queryIndex = value.indexOf('?');
        String path = queryIndex >= 0 ? value.substring(0, queryIndex) : value;
        return path.equals(CONSOLE_BASE_PATH) || path.startsWith(CONSOLE_BASE_PATH + "/");
    }

    @Operation(summary = "打开后台首次登录页面")
    @GetMapping("/shopping/admin/firstlogin")
    public Object adminFirstLoginPage() {
        if (adminConfigService.isInitialized()) {
            return "redirect:/shopping/admin/login";
        }
        return htmlPage("admin-firstlogin.html");
    }

    @Operation(summary = "打开旧后台登录页面")
    @GetMapping("/shopping/user/lojin")
    public String legacyAdminLoginPage() {
        return "redirect:/shopping/admin/login";
    }

    @Operation(summary = "打开旧后台首次登录页面")
    @GetMapping("/shopping/user/firstlogin")
    public String legacyAdminFirstLoginPage() {
        return "redirect:/shopping/admin/firstlogin";
    }

    @Operation(summary = "打开后台控制台页面")
    @GetMapping({
            "/shopping/admin/console",
            "/shopping/admin/console/{section}",
            "/shopping/admin/console/{section}/{subsection}",
            "/shopping/admin/console/{section}/{subsection}/{sub2}",
            "/shopping/admin/console/{section}/{subsection}/{sub2}/{sub3}"
    })
    public ResponseEntity<Resource> adminConsolePage() {
        return htmlPage("admin-console.html");
    }

    private ResponseEntity<Resource> htmlPage(String fileName) {
        Resource resource = new ClassPathResource("static/" + fileName);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().mustRevalidate())
                .header("Pragma", "no-cache")
                .header("Expires", "0")
                .contentType(MediaType.TEXT_HTML)
                .body(resource);
    }
}
