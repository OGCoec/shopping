package com.example.ShoppingSystem.admin.controller;

import com.example.ShoppingSystem.admin.service.AdminConfigService;
import com.example.ShoppingSystem.admin.service.AdminSessionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminPageController {

    private final AdminConfigService adminConfigService;
    private final AdminSessionService adminSessionService;

    public AdminPageController(AdminConfigService adminConfigService,
                               AdminSessionService adminSessionService) {
        this.adminConfigService = adminConfigService;
        this.adminSessionService = adminSessionService;
    }

    @GetMapping("/shopping/admin/login")
    public Object adminLoginPage(HttpServletRequest request) {
        if (!adminConfigService.isInitialized()) {
            return "redirect:/shopping/admin/firstlogin";
        }
        if (adminSessionService.isAuthenticated(request)) {
            return "redirect:/shopping/admin/console";
        }
        return htmlPage("admin-login.html");
    }

    @GetMapping("/shopping/admin/firstlogin")
    public Object adminFirstLoginPage() {
        if (adminConfigService.isInitialized()) {
            return "redirect:/shopping/admin/login";
        }
        return htmlPage("admin-firstlogin.html");
    }

    @GetMapping("/shopping/user/lojin")
    public String legacyAdminLoginPage() {
        return "redirect:/shopping/admin/login";
    }

    @GetMapping("/shopping/user/firstlogin")
    public String legacyAdminFirstLoginPage() {
        return "redirect:/shopping/admin/firstlogin";
    }

    @GetMapping({
            "/shopping/admin/console",
            "/shopping/admin/console/{section}",
            "/shopping/admin/console/{section}/{subsection}",
            "/shopping/admin/console/{section}/{subsection}/{sub2}"
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
