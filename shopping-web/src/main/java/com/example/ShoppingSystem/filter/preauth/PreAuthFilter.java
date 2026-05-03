package com.example.ShoppingSystem.filter.preauth;

import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.filter.preauth.model.PreAuthBinding;
import com.example.ShoppingSystem.filter.preauth.model.PreAuthValidationOutcome;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * preauth 预登录绑定过滤器。
 * <p>
 * 作用：
 * 1. 放行登录页、注册页、静态资源和 preauth / WAF 相关公开接口；
 * 2. 对受保护请求校验 PREAUTH_TOKEN、设备指纹、UA 和当前真实 IP；
 * 3. 在需要时返回统一 JSON 错误，或者引导前端先完成 WAF 验证。
 */
public class PreAuthFilter extends OncePerRequestFilter {

    private static final String BLOCKED_PUBLIC_ERROR_CODE = "PREAUTH_RETRY_LATER";
    private static final String BLOCKED_PUBLIC_ERROR_MESSAGE = "当前操作过于频繁，请稍后重试";

    /**
     * 允许匿名 GET / HEAD 访问的页面路径。
     */
    private static final Set<String> PUBLIC_GET_PATHS = Set.of(
            "/",
            "/index.html",
            "/shopping/user/log-in",
            "/shopping/user/log-in/password",
            "/shopping/user/login",
            "/shopping/user/create-account",
            "/shopping/user/create-account/password",
            "/shopping/user/register",
            "/shopping/user/email-verification",
            "/shopping/user/totp-verification",
            "/shopping/user/add-phone",
            "/shopping/user/session-ended",
            "/shopping/user/forgot-password",
            "/swagger-ui.html",
            "/doc.html",
            "/error",
            "/oauth2/github/login",
            "/oauth2/google/login",
            "/oauth2/microsoft/login"
    );

    /**
     * 不区分 HTTP 方法、始终允许访问的公开路径。
     */
    private static final Set<String> PUBLIC_ANY_METHOD_PATHS = Set.of(
            "/shopping/auth/preauth/bootstrap",
            "/shopping/auth/preauth/phone-country",
            "/shopping/auth/preauth/phone-validate",
            "/shopping/auth/waf/verify",
            "/favicon.ico"
    );

    /**
     * 公开静态资源或公开前缀路径。
     */
    private static final String[] PUBLIC_PATH_PREFIXES = {
            "/css/",
            "/js/",
            "/images/",
            "/fragments/",
            "/webjars/",
            "/v3/api-docs",
            "/swagger-ui/",
            "/oauth2/authorization/",
            "/login/oauth2/code/"
    };

    /**
     * preauth 主服务，负责 token 校验、绑定续期和风险判断。
     */
    private final PreAuthBindingService preAuthBindingService;

    /**
     * JSON 输出工具。
     */
    private final ObjectMapper objectMapper;

    public PreAuthFilter(PreAuthBindingService preAuthBindingService,
                         ObjectMapper objectMapper) {
        this.preAuthBindingService = preAuthBindingService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null || uri.isBlank()) {
            return true;
        }

        String method = request.getMethod();
        if ("OPTIONS".equalsIgnoreCase(method)) {
            // 预检请求直接放行。
            return true;
        }

        if (PUBLIC_ANY_METHOD_PATHS.contains(uri)) {
            // bootstrap、手机号校验、WAF 回调等公开接口直接放行。
            return true;
        }

        if (isGetLikeMethod(method) && PUBLIC_GET_PATHS.contains(uri)) {
            // 登录、注册、文档等公开页面直接放行。
            return true;
        }

        for (String prefix : PUBLIC_PATH_PREFIXES) {
            if (uri.startsWith(prefix)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 仅把 GET / HEAD 视为页面导航型方法。
     */
    private boolean isGetLikeMethod(String method) {
        return "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!preAuthBindingService.isEnabled()) {
            // preauth 关闭时不过滤业务请求。
            filterChain.doFilter(request, response);
            return;
        }

        // 先从 Cookie 或兼容 Header 中解析 token。
        String token = preAuthBindingService.resolveIncomingToken(request);
        if (StrUtil.isBlank(token)) {
            writeJsonError(
                    response,
                    request,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "PREAUTH_MISSING",
                    "预登录状态缺失，请刷新页面后重试",
                    null
            );
            return;
        }

        // 校验 token、设备指纹、UA 和当前真实 IP。
        PreAuthValidationOutcome outcome = preAuthBindingService.validateAndTouch(
                token,
                request.getHeader(PreAuthHeaders.HEADER_DEVICE_FINGERPRINT),
                request
        );
        if (!outcome.valid()) {
            switch (outcome.error()) {
                case EXPIRED -> {
                    refreshExpiredCookie(response, request);
                    writeJsonError(
                            response,
                            request,
                            HttpServletResponse.SC_UNAUTHORIZED,
                            "PREAUTH_EXPIRED",
                            "预登录状态已过期，请刷新页面后重试",
                            null
                    );
                }
                case FINGERPRINT_MISMATCH -> {
                    refreshExpiredCookie(response, request);
                    writeJsonError(
                            response,
                            request,
                            HttpServletResponse.SC_UNAUTHORIZED,
                            "PREAUTH_FINGERPRINT_MISMATCH",
                            "当前设备环境发生变化，请刷新页面后重试",
                            null
                    );
                }
                case USER_AGENT_MISMATCH -> {
                    refreshExpiredCookie(response, request);
                    writeJsonError(
                            response,
                            request,
                            HttpServletResponse.SC_UNAUTHORIZED,
                            "PREAUTH_UA_MISMATCH",
                            "当前浏览器环境发生变化，请刷新页面后重试",
                            null
                    );
                }
                case IP_CHANGED_WAF_REQUIRED -> writeWafVerificationRequired(response, request);
                default -> {
                    refreshExpiredCookie(response, request);
                    writeJsonError(
                            response,
                            request,
                            HttpServletResponse.SC_UNAUTHORIZED,
                            "PREAUTH_INVALID",
                            "预登录状态无效，请刷新页面后重试",
                            null
                    );
                }
            }
            return;
        }

        // 校验通过后刷新 token Cookie，并把绑定结果挂到 request 上供后续链路使用。
        PreAuthBinding binding = outcome.binding();
        refreshTokenCookie(response, request, binding.token());

        if (preAuthBindingService.isBlockedRisk(binding.riskLevel())
                && !isLoginFlowApiRequest(request)) {
            writeJsonError(
                    response,
                    request,
                    HttpServletResponse.SC_FORBIDDEN,
                    BLOCKED_PUBLIC_ERROR_CODE,
                    BLOCKED_PUBLIC_ERROR_MESSAGE,
                    null
            );
            return;
        }

        request.setAttribute("preAuthToken", binding.token());
        request.setAttribute("preAuthRiskLevel", binding.riskLevel());
        filterChain.doFilter(request, response);
    }

    private boolean isLoginFlowApiRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri != null && uri.startsWith("/shopping/user/login/");
    }

    /**
     * 写入最新 token Cookie。
     */
    private void refreshTokenCookie(HttpServletResponse response,
                                    HttpServletRequest request,
                                    String token) {
        response.addHeader("Set-Cookie", preAuthBindingService.buildTokenCookie(token, request).toString());
    }

    /**
     * 写入过期 token Cookie，用于清理浏览器中的 PREAUTH_TOKEN。
     */
    private void refreshExpiredCookie(HttpServletResponse response,
                                      HttpServletRequest request) {
        response.addHeader("Set-Cookie", preAuthBindingService.buildExpiredTokenCookie(request).toString());
    }

    /**
     * 输出不带 verifyUrl 的统一 JSON 错误。
     */
    private void writeJsonError(HttpServletResponse response,
                                HttpServletRequest request,
                                int status,
                                String errorCode,
                                String message,
                                PreAuthBinding binding) throws IOException {
        writeJsonError(response, request, status, errorCode, message, binding, null);
    }

    /**
     * 输出统一 JSON 错误。
     * <p>
     * 当需要前端继续跳 WAF 时，可额外返回 verifyUrl。
     */
    private void writeJsonError(HttpServletResponse response,
                                HttpServletRequest request,
                                int status,
                                String errorCode,
                                String message,
                                PreAuthBinding binding,
                                String verifyUrl) throws IOException {
        if (response.isCommitted()) {
            return;
        }

        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("status", status);
        body.put("error", errorCode);
        body.put("message", message);
        body.put("path", request.getRequestURI());
        body.put("timestamp", OffsetDateTime.now().toString());
        if (binding != null) {
            body.put("riskLevel", binding.riskLevel());
        }
        if (verifyUrl != null && !verifyUrl.isBlank()) {
            body.put("verifyUrl", verifyUrl);
        }
        objectMapper.writeValue(response.getWriter(), body);
    }

    /**
     * 当前 token 绑定的 IP 与请求真实 IP 不一致时，要求先完成 WAF 验证。
     * <p>
     * 页面导航请求返回 302；AJAX / fetch 返回 409 JSON。
     */
    private void writeWafVerificationRequired(HttpServletResponse response,
                                              HttpServletRequest request) throws IOException {
        if (response.isCommitted()) {
            return;
        }

        // 先写一个提示 Cookie，告诉前端当前处于需要先过 WAF 的状态。
        response.addHeader("Set-Cookie", preAuthBindingService.buildWafRequiredCookie(request).toString());
        boolean htmlNavigation = isHtmlNavigationRequest(request);
        String verifyUrl = htmlNavigation
                ? buildWafVerifyUrlFromCurrentRequest(request)
                : buildWafVerifyUrlFromReferer(request);
        if (htmlNavigation) {
            response.setStatus(HttpServletResponse.SC_FOUND);
            response.setHeader("Location", verifyUrl);
            return;
        }

        writeJsonError(
                response,
                request,
                HttpServletResponse.SC_CONFLICT,
                "PREAUTH_IP_CHANGED_WAF_REQUIRED",
                "当前网络环境发生变化，请先完成安全验证",
                null,
                verifyUrl
        );
    }

    /**
     * 基于当前请求路径构造 WAF 验证跳转地址。
     */
    private String buildWafVerifyUrlFromCurrentRequest(HttpServletRequest request) {
        String returnPath = request.getRequestURI();
        String query = request.getQueryString();
        if (query != null && !query.isBlank()) {
            returnPath += "?" + query;
        }
        return buildWafVerifyUrl(returnPath);
    }

    /**
     * 基于 Referer 构造 WAF 验证跳转地址。
     * <p>
     * 主要用于 AJAX / fetch 请求，让前端知道用户原本所在页面应该跳回哪里。
     */
    private String buildWafVerifyUrlFromReferer(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        if (referer == null || referer.isBlank()) {
            return null;
        }
        try {
            URI refererUri = URI.create(referer.trim());
            String path = refererUri.getPath();
            if (path == null || path.isBlank() || !path.startsWith("/") || path.startsWith("//")) {
                return null;
            }
            if (path.startsWith("/shopping/auth/waf/verify")) {
                return null;
            }
            String query = refererUri.getQuery();
            String returnPath = (query == null || query.isBlank()) ? path : path + "?" + query;
            return buildWafVerifyUrl(returnPath);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 拼出最终的 WAF 验证地址。
     */
    private String buildWafVerifyUrl(String returnPath) {
        if (returnPath == null || returnPath.isBlank()) {
            return "/shopping/auth/waf/verify";
        }
        return "/shopping/auth/waf/verify?return="
                + URLEncoder.encode(returnPath, StandardCharsets.UTF_8);
    }

    /**
     * 判断当前请求是不是页面导航请求。
     * <p>
     * 条件：
     * 1. 方法是 GET / HEAD；
     * 2. Accept 包含 text/html。
     */
    private boolean isHtmlNavigationRequest(HttpServletRequest request) {
        String method = request.getMethod();
        if (!isGetLikeMethod(method)) {
            return false;
        }
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains("text/html");
    }
}
