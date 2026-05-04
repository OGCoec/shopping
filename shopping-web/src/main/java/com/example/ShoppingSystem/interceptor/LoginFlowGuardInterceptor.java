package com.example.ShoppingSystem.interceptor;

import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.filter.preauth.PreAuthBindingService;
import com.example.ShoppingSystem.loginflow.LoginFlowCookieFactory;
import com.example.ShoppingSystem.loginflow.LoginFlowWebSupport;
import com.example.ShoppingSystem.registerflow.RegisterFlowCookieFactory;
import com.example.ShoppingSystem.service.user.auth.login.LoginFlowSessionService;
import com.example.ShoppingSystem.service.user.auth.login.model.LoginFlowSession;
import com.example.ShoppingSystem.service.user.auth.login.model.LoginFlowStep;
import com.example.ShoppingSystem.service.user.auth.login.model.LoginFlowValidationResult;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class LoginFlowGuardInterceptor implements HandlerInterceptor {

    private static final String LOGIN_WAF_RESUME_COOKIE_NAME = "LOGIN_WAF_RESUME";

    private final LoginFlowSessionService loginFlowSessionService;
    private final LoginFlowCookieFactory loginFlowCookieFactory;
    private final RegisterFlowCookieFactory registerFlowCookieFactory;
    private final PreAuthBindingService preAuthBindingService;

    public LoginFlowGuardInterceptor(LoginFlowSessionService loginFlowSessionService,
                                     LoginFlowCookieFactory loginFlowCookieFactory,
                                     RegisterFlowCookieFactory registerFlowCookieFactory,
                                     PreAuthBindingService preAuthBindingService) {
        this.loginFlowSessionService = loginFlowSessionService;
        this.loginFlowCookieFactory = loginFlowCookieFactory;
        this.registerFlowCookieFactory = registerFlowCookieFactory;
        this.preAuthBindingService = preAuthBindingService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        String requestPath = request.getRequestURI();
        if (!LoginFlowWebSupport.isGuardedPagePath(requestPath)) {
            return true;
        }

        String loginFlowId = loginFlowCookieFactory.resolveFlowId(request);
        String mode = request.getParameter(LoginFlowWebSupport.MODE_QUERY_PARAM);
        if (LoginFlowWebSupport.isSharedWithRegisterPath(requestPath)
                && com.example.ShoppingSystem.registerflow.RegisterFlowWebSupport.REGISTER_MODE.equals(mode)) {
            return true;
        }
        if (LoginFlowWebSupport.isSharedWithRegisterPath(requestPath)
                && StrUtil.isBlank(loginFlowId)
                && StrUtil.isNotBlank(registerFlowCookieFactory.resolveFlowId(request))) {
            return true;
        }

        String preAuthToken = preAuthBindingService.resolveIncomingToken(request);
        if (isPasswordWafResumeRequest(requestPath, request, preAuthToken)) {
            return true;
        }

        LoginFlowValidationResult validationResult = loginFlowSessionService.validate(loginFlowId, preAuthToken);
        if (!validationResult.valid()) {
            clearFlowCookie(response, request);
            redirect(response, LoginFlowWebSupport.sessionEndedWithNotice());
            return false;
        }

        LoginFlowSession session = validationResult.session();
        LoginFlowStep step = session.getStep();
        if (session.isCompleted() || step == LoginFlowStep.DONE) {
            clearFlowCookie(response, request);
            redirect(response, LoginFlowWebSupport.withNotice(
                    LoginFlowWebSupport.AUTHENTICATED_PATH,
                    LoginFlowWebSupport.NOTICE_LOGIN_COMPLETED
            ));
            return false;
        }

        if (step == LoginFlowStep.ADD_PHONE) {
            if (!LoginFlowWebSupport.ADD_PHONE_PATH.equals(requestPath)) {
                redirect(response, LoginFlowWebSupport.withNotice(
                        LoginFlowWebSupport.routeForStep(LoginFlowStep.ADD_PHONE),
                        LoginFlowWebSupport.NOTICE_STEP_RESTORED
                ));
                return false;
            }
            return true;
        }

        if (LoginFlowWebSupport.isAllowedFactorPath(session, requestPath)) {
            return true;
        }

        redirect(response, LoginFlowWebSupport.withNotice(
                LoginFlowWebSupport.routeForStep(step),
                LoginFlowWebSupport.NOTICE_STEP_RESTORED
        ));
        return false;
    }

    private boolean isPasswordWafResumeRequest(String requestPath,
                                               HttpServletRequest request,
                                               String preAuthToken) {
        return LoginFlowWebSupport.LOGIN_PASSWORD_PATH.equals(requestPath)
                && hasLoginWafResumeCookie(request)
                && StrUtil.isNotBlank(preAuthToken);
    }

    private boolean hasLoginWafResumeCookie(HttpServletRequest request) {
        Cookie[] cookies = request == null ? null : request.getCookies();
        if (cookies == null || cookies.length == 0) {
            return false;
        }
        for (Cookie cookie : cookies) {
            if (cookie == null) {
                continue;
            }
            if (LOGIN_WAF_RESUME_COOKIE_NAME.equals(cookie.getName())
                    && "1".equals(StrUtil.blankToDefault(cookie.getValue(), "").trim())) {
                return true;
            }
        }
        return false;
    }

    private void clearFlowCookie(HttpServletResponse response, HttpServletRequest request) {
        response.addHeader("Set-Cookie", loginFlowCookieFactory.buildExpiredFlowCookie(request).toString());
    }

    private void redirect(HttpServletResponse response, String location) throws Exception {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(HttpServletResponse.SC_FOUND);
        response.setHeader("Location", location);
    }
}
