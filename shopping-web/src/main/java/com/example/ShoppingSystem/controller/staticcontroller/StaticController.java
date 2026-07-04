package com.example.ShoppingSystem.controller.staticcontroller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "用户页面", description = "用户前台页面路由接口")
@Controller
public class StaticController {

    /**
     * 登录页面路由。
     * 统一返回 SPA 容器页 login.html，由前端路由决定右侧显示内容。
     */
    @Operation(summary = "打开用户登录页面")
    @GetMapping("/shopping/user/login")
    public ResponseEntity<Resource> loginPage() {
        return htmlPage("login.html");
    }

    @Operation(summary = "打开用户登录入口页面")
    @GetMapping("/shopping/user/log-in")
    public ResponseEntity<Resource> logInPage() {
        return htmlPage("login.html");
    }

    @Operation(summary = "打开密码登录页面")
    @GetMapping("/shopping/user/log-in/password")
    public ResponseEntity<Resource> logInPasswordPage() {
        return htmlPage("login.html");
    }

    /**
     * 注册页面路由。
     * 统一返回 SPA 容器页 login.html，由前端路由决定右侧显示内容。
     */
    @Operation(summary = "打开用户注册页面")
    @GetMapping("/shopping/user/register")
    public ResponseEntity<Resource> registerPage() {
        return htmlPage("login.html");
    }

    @Operation(summary = "打开创建账号页面")
    @GetMapping("/shopping/user/create-account")
    public ResponseEntity<Resource> createAccountPage() {
        return htmlPage("login.html");
    }

    @Operation(summary = "打开创建账号密码页面")
    @GetMapping("/shopping/user/create-account/password")
    public ResponseEntity<Resource> createAccountPasswordPage() {
        return htmlPage("login.html");
    }

    @Operation(summary = "打开邮箱验证页面")
    @GetMapping("/shopping/user/email-verification")
    public ResponseEntity<Resource> emailVerificationPage() {
        return htmlPage("login.html");
    }

    @Operation(summary = "打开动态口令验证页面")
    @GetMapping("/shopping/user/totp-verification")
    public ResponseEntity<Resource> totpVerificationPage() {
        return htmlPage("login.html");
    }

    @Operation(summary = "打开绑定手机页面")
    @GetMapping("/shopping/user/add-phone")
    public ResponseEntity<Resource> addPhonePage() {
        return htmlPage("login.html");
    }

    @Operation(summary = "打开会话结束页面")
    @GetMapping("/shopping/user/session-ended")
    public ResponseEntity<Resource> sessionEndedPage() {
        return htmlPage("login.html");
    }

    /**
     * 找回密码页面路由。
     * 统一返回 SPA 容器页 login.html，由前端路由决定右侧显示内容。
     */
    @Operation(summary = "打开用户资料页面")
    @GetMapping("/shopping/user/profile")
    public ResponseEntity<Resource> profilePage() {
        return htmlPage("profile.html");
    }

    @Operation(summary = "打开用户控制台页面")
    @GetMapping("/shopping/user/console")
    public ResponseEntity<Resource> consolePage() {
        return htmlPage("console.html");
    }

    @Operation(summary = "打开用户签到页面")
    @GetMapping("/shopping/user/sign-in")
    public ResponseEntity<Resource> signInPage() {
        return htmlPage("sign-in.html");
    }

    @Operation(summary = "打开商品详情页面")
    @GetMapping("/shopping/user/products/{id}")
    public ResponseEntity<Resource> productDetailPage() {
        return htmlPage("product-detail.html");
    }

    @Operation(summary = "打开订单结算页面")
    @GetMapping("/shopping/user/checkout/{skuId}")
    public ResponseEntity<Resource> checkoutPage() {
        return htmlPage("checkout.html");
    }

    @Operation(summary = "打开用户订单页面")
    @GetMapping({
            "/shopping/user/orders",
            "/shopping/user/orders/{orderNo}"
    })
    public ResponseEntity<Resource> ordersPage() {
        return htmlPage("orders.html");
    }

    @Operation(summary = "打开用户优惠券页面")
    @GetMapping({
            "/shopping/user/coupons",
            "/shopping/user/coupons/{couponTemplateId}",
            "/shopping/user/coupons/mine",
            "/shopping/user/coupons/mine/{userCouponId}"
    })
    public ResponseEntity<Resource> couponsPage() {
        return htmlPage("coupons.html");
    }

    @Operation(summary = "打开安全手机页面")
    @GetMapping("/shopping/user/security/phone")
    public ResponseEntity<Resource> securityPhonePage() {
        return htmlPage("security-phone.html");
    }

    @Operation(summary = "打开找回密码页面")
    @GetMapping("/shopping/user/forgot-password")
    public ResponseEntity<Resource> forgotPasswordPage() {
        return htmlPage("login.html");
    }

    @Operation(summary = "打开重置密码页面")
    @GetMapping({"/shopping/user/reset-password-url", "/shopping/user/reset-password-code"})
    public ResponseEntity<Resource> resetPasswordPage() {
        return htmlPage("login.html");
    }

    @Operation(summary = "打开网络检查失败页面")
    @GetMapping("/shopping/auth/network-check-failed")
    public ResponseEntity<Resource> networkCheckFailedPage() {
        return htmlPage("network-check-failed.html");
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
