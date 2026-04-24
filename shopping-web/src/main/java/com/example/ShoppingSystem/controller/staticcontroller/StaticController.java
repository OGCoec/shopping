package com.example.ShoppingSystem.controller.staticcontroller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 静态页面路由控制器。
 * 负责把 RESTful 页面路径直接映射到对应 HTML 资源，
 * 同时配合安全配置禁止旧路径（/login.html 等）直接访问。
 */
@Controller
public class StaticController {

    /**
     * 登录页面路由。
     * 统一返回 SPA 容器页 login.html，由前端路由决定右侧显示内容。
     */
    @GetMapping("/shopping/user/login")
    public ResponseEntity<Resource> loginPage() {
        return htmlPage("login.html");
    }

    /**
     * 注册页面路由。
     * 统一返回 SPA 容器页 login.html，由前端路由决定右侧显示内容。
     */
    @GetMapping("/shopping/user/register")
    public ResponseEntity<Resource> registerPage() {
        return htmlPage("login.html");
    }

    /**
     * 找回密码页面路由。
     * 统一返回 SPA 容器页 login.html，由前端路由决定右侧显示内容。
     */
    @GetMapping("/shopping/user/forgot-password")
    public ResponseEntity<Resource> forgotPasswordPage() {
        return htmlPage("login.html");
    }

    /**
     * 统一构造 HTML 响应。
     * HTML 文件位于 resources/static 目录下，因此这里使用 static/<fileName> 加载。
     */
    private ResponseEntity<Resource> htmlPage(String fileName) {
        Resource resource = new ClassPathResource("static/" + fileName);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(resource);
    }
}
