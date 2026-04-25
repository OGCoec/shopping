package com.example.ShoppingSystem.controller.auth;

import com.example.ShoppingSystem.filter.preauth.PreAuthBindingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;

/**
 * WAF 验证回调控制器。
 * 使用场景：
 * 当预登录上下文检测到“同 token 的来源 IP 发生变化”时，会要求先完成 Cloudflare WAF 人机验证。
 * 验证完成后会回调本控制器，由本控制器完成“放行标记写入 + 回跳”。
 */
@Controller
@RequestMapping("/shopping/auth/waf")
public class WafVerifyController {

    private static final String DEFAULT_RETURN_PATH = "/";
    private final PreAuthBindingService preAuthBindingService;

    /**
     * 构造函数：注入预登录绑定服务。
     */
    public WafVerifyController(PreAuthBindingService preAuthBindingService) {
        this.preAuthBindingService = preAuthBindingService;
    }

    /**
     * WAF 验证完成后的回调入口。
     * 关键动作：
     * 1) 读取当前请求携带的 preauth token。
     * 2) 将“token + 当前 IP”写入短期已验证标记（仅允许这次 IP 漂移放行）。
     * 3) 清理 WAF_REQUIRED cookie，避免后续请求重复触发挑战。
     * 4) 回跳到 return 页面，并追加 waf_verified=1，供前端重放中断请求。
     */
    @GetMapping("/verify")
    public void verify(@RequestParam(value = "return", required = false) String returnPath,
                       HttpServletRequest request,
                       HttpServletResponse response) throws IOException {
        String token = preAuthBindingService.resolveIncomingToken(request);
        if (token != null && !token.isBlank()) {
            // 通过 WAF 后立即同步绑定到当前 IP，并刷新 score/riskLevel。
            preAuthBindingService.markWafVerifiedAndRefreshBindingForCurrentIp(token, request);
        }
        // 验证流程结束后立即清理挑战标记 cookie。
        response.addHeader("Set-Cookie", preAuthBindingService.buildClearWafRequiredCookie(request).toString());
        response.sendRedirect(appendWafVerifiedFlag(sanitizeReturnPath(returnPath)));
    }

    /**
     * 清洗 return 参数，防止开放重定向风险。
     * 仅允许站内相对路径：
     * - 必须以 "/" 开头
     * - 不能以 "//" 开头（避免协议相对 URL）
     */
    private String sanitizeReturnPath(String returnPath) {
        if (returnPath == null || returnPath.isBlank()) {
            return DEFAULT_RETURN_PATH;
        }
        String trimmed = returnPath.trim();
        if (!trimmed.startsWith("/") || trimmed.startsWith("//")) {
            return DEFAULT_RETURN_PATH;
        }
        return trimmed;
    }

    /**
     * 给回跳地址追加 waf_verified=1 标记。
     * 若 URL 已包含该参数，则保持原样，避免重复拼接。
     */
    private String appendWafVerifiedFlag(String returnPath) {
        if (returnPath.contains("waf_verified=")) {
            return returnPath;
        }
        return returnPath + (returnPath.contains("?") ? "&" : "?") + "waf_verified=1";
    }
}
