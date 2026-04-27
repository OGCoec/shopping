package com.example.ShoppingSystem.filter.preauth.support;

import com.example.ShoppingSystem.filter.preauth.PreAuthHeaders;

import cn.hutool.core.util.StrUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * 负责从 HttpServletRequest 中解析 preauth 所需的上下文信息。
 * <p>
 * 这样做的好处是把 servlet 细节隔离出来，
 * 让上层流程编排器只关心“拿到什么”，而不是“从哪里拿、按什么优先级拿”。
 */
@Component
public class PreAuthRequestResolver {

    private final PreAuthProperties properties;

    /**
     * 注入 preauth 配置，用于读取 cookie 名等协议参数。
     */
    public PreAuthRequestResolver(PreAuthProperties properties) {
        this.properties = properties;
    }

    /**
     * 解析入站请求中的预登录 token。
     * <p>
     * 优先级：
     * 1) Cookie；
     * 2) 旧版 Header。
     */
    public String resolveIncomingToken(HttpServletRequest request) {
        if (request == null) {
            return "";
        }

        // 先走当前主链路：从 HttpOnly Cookie 中读取。
        String cookieToken = resolveTokenFromCookie(request);
        if (StrUtil.isNotBlank(cookieToken)) {
            return cookieToken;
        }

        // 再兼容旧版 Header 传 token 的场景。
        return StrUtil.blankToDefault(request.getHeader(PreAuthHeaders.HEADER_PREAUTH_TOKEN), "").trim();
    }

    /**
     * 规范化设备指纹。
     * <p>
     * 如果前端已经传了原始指纹，就直接使用；
     * 如果前端没传，就退化为“UA + Accept-Language”的兜底指纹。
     */
    public String normalizeFingerprint(String rawFingerprint, HttpServletRequest request) {
        if (StrUtil.isNotBlank(rawFingerprint)) {
            return rawFingerprint.trim();
        }

        // 前端未提供指纹时，用浏览器环境信息拼一个可复用的兜底值。
        String userAgent = resolveUserAgent(request);
        String language = request == null
                ? "unknown"
                : StrUtil.blankToDefault(request.getHeader("Accept-Language"), "unknown");
        return userAgent + "|" + language;
    }

    /**
     * 解析并规范化 User-Agent。
     */
    public String resolveUserAgent(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        return StrUtil.blankToDefault(request.getHeader("User-Agent"), "unknown").trim();
    }

    /**
     * 解析客户端真实 IP。
     * <p>
     * 优先级：
     * 1) X-Forwarded-For 的第一个地址；
     * 2) X-Real-IP；
     * 3) remoteAddr。
     */
    public String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }

        // 反向代理场景下，通常第一个 X-Forwarded-For 才是原始客户端地址。
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StrUtil.isNotBlank(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }

        // 有些代理只写 X-Real-IP，不写 X-Forwarded-For。
        String realIp = request.getHeader("X-Real-IP");
        if (StrUtil.isNotBlank(realIp)) {
            return realIp.trim();
        }

        // 都没有时，退回到容器感知到的 remoteAddr。
        return StrUtil.blankToDefault(request.getRemoteAddr(), "unknown");
    }

    /**
     * 判断当前请求是否应视为 HTTPS。
     * <p>
     * 除了 request.isSecure()，也兼容反向代理回源时通过 X-Forwarded-Proto 传递协议。
     */
    public boolean isHttpsRequest(HttpServletRequest request) {
        if (request == null) {
            return false;
        }

        // 直连 HTTPS 场景。
        if (request.isSecure()) {
            return true;
        }

        // 反向代理终止 TLS 后，通常通过这个头把原协议回传给应用。
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        return "https".equalsIgnoreCase(StrUtil.blankToDefault(forwardedProto, "").trim());
    }

    /**
     * 从 Cookie 中读取 PREAUTH_TOKEN。
     */
    private String resolveTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null || cookies.length == 0) {
            return "";
        }
        for (Cookie cookie : cookies) {
            if (cookie == null) {
                continue;
            }

            // 只识别配置中声明的 preauth token cookie。
            if (!StrUtil.equals(properties.getCookieName(), cookie.getName())) {
                continue;
            }
            return StrUtil.blankToDefault(cookie.getValue(), "").trim();
        }
        return "";
    }
}
