package com.example.ShoppingSystem.controller.auth;

import com.example.ShoppingSystem.controller.auth.dto.PreAuthBootstrapResponse;
import com.example.ShoppingSystem.controller.auth.dto.PreAuthPhoneCountryResponse;
import com.example.ShoppingSystem.filter.preauth.PreAuthBindingService;
import com.example.ShoppingSystem.filter.preauth.PreAuthHeaders;
import com.example.ShoppingSystem.quota.IpCountryQueryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 预登录（PreAuth）引导控制器。
 * 主要职责：
 * 1) 为匿名用户初始化/续期预登录上下文（token、风险等级、挑战需求）。
 * 2) 为前端提供基于客户端 IP 的国家信息，便于默认手机号区号展示。
 */
@RestController
@RequestMapping("/shopping/auth/preauth")
public class PreAuthBootstrapController {

    private final PreAuthBindingService preAuthBindingService;
    private final IpCountryQueryService ipCountryQueryService;

    /**
     * 构造函数：注入预登录绑定服务与 IP 国家查询服务。
     */
    public PreAuthBootstrapController(PreAuthBindingService preAuthBindingService,
                                      IpCountryQueryService ipCountryQueryService) {
        this.preAuthBindingService = preAuthBindingService;
        this.ipCountryQueryService = ipCountryQueryService;
    }

    /**
     * 预登录引导接口。
     * 行为说明：
     * 1) 尝试从请求中读取已有预登录 token（cookie 优先，header 兜底）。
     * 2) 调用服务层进行“新建或续期”。
     * 3) 若预登录功能启用，则回写 HttpOnly token cookie。
     * 4) 返回前端所需的风险上下文信息。
     */
    @PostMapping("/bootstrap")
    public PreAuthBootstrapResponse bootstrap(
            @RequestHeader(value = PreAuthHeaders.HEADER_DEVICE_FINGERPRINT, required = false) String fingerprint,
            HttpServletRequest request,
            HttpServletResponse response) {
        // 读取入站 token（如果有），用于“同设备续期”而不是每次新建。
        String incomingToken = preAuthBindingService.resolveIncomingToken(request);
        // 统一在服务层完成指纹/IP/UA 绑定与风险快照生成。
        PreAuthBindingService.PreAuthSnapshot snapshot = preAuthBindingService.bootstrap(incomingToken, fingerprint, request);
        if (preAuthBindingService.isEnabled()) {
            // 仅在功能开启时写 cookie，关闭模式下返回的是只读快照信息。
            response.addHeader("Set-Cookie", preAuthBindingService.buildTokenCookie(snapshot.token(), request).toString());
        }
        return new PreAuthBootstrapResponse(
                true,
                "ok",
                null,
                snapshot.expiresAtEpochMillis(),
                snapshot.riskLevel(),
                snapshot.challengeRequired(),
                snapshot.blocked()
        );
    }

    /**
     * 获取手机号国家（区号）建议值。
     * 前端注册页可据此自动选择国家代码，提升输入体验。
     */
    @GetMapping("/phone-country")
    public PreAuthPhoneCountryResponse resolvePhoneCountry(HttpServletRequest request) {
        // 提取真实客户端 IP，尽量避免代理层地址干扰。
        String clientIp = resolveClientIp(request);
        IpCountryQueryService.CountryQueryResult result = ipCountryQueryService.queryCountry(clientIp);
        if (result.success()) {
            return new PreAuthPhoneCountryResponse(true, "ok", result.country(), result.source());
        }
        return new PreAuthPhoneCountryResponse(false, result.reason(), null, result.source());
    }

    /**
     * 解析客户端 IP。
     * 优先级：
     * 1) X-Forwarded-For 的首个地址（最靠近客户端）
     * 2) X-Real-IP
     * 3) 容器提供的 remoteAddr
     */
    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            // 规范格式通常是 "client, proxy1, proxy2"，第一个才是原始客户端。
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
