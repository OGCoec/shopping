package com.example.ShoppingSystem.interceptor;

import com.example.ShoppingSystem.filter.preauth.PreAuthBindingService;
import com.example.ShoppingSystem.loginflow.LoginFlowCookieFactory;
import com.example.ShoppingSystem.registerflow.RegisterFlowCookieFactory;
import com.example.ShoppingSystem.registerflow.RegisterFlowWebSupport;
import com.example.ShoppingSystem.service.user.auth.register.RegisterFlowSessionService;
import com.example.ShoppingSystem.service.user.auth.register.model.RegisterFlowSession;
import com.example.ShoppingSystem.service.user.auth.register.model.RegisterFlowStep;
import com.example.ShoppingSystem.service.user.auth.register.model.RegisterFlowValidationResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Guards semantic register step page routes.
 */
@Component
public class RegisterFlowGuardInterceptor implements HandlerInterceptor {

    private final RegisterFlowSessionService registerFlowSessionService;
    private final RegisterFlowCookieFactory registerFlowCookieFactory;
    private final LoginFlowCookieFactory loginFlowCookieFactory;
    private final PreAuthBindingService preAuthBindingService;

    public RegisterFlowGuardInterceptor(RegisterFlowSessionService registerFlowSessionService,
                                        RegisterFlowCookieFactory registerFlowCookieFactory,
                                        LoginFlowCookieFactory loginFlowCookieFactory,
                                        PreAuthBindingService preAuthBindingService) {
        this.registerFlowSessionService = registerFlowSessionService;
        this.registerFlowCookieFactory = registerFlowCookieFactory;
        this.loginFlowCookieFactory = loginFlowCookieFactory;
        this.preAuthBindingService = preAuthBindingService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        String requestPath = request.getRequestURI();
        if (!RegisterFlowWebSupport.isGuardedPagePath(requestPath)) {
            return true;
        }

        String flowId = registerFlowCookieFactory.resolveFlowId(request);
        String mode = request.getParameter(RegisterFlowWebSupport.MODE_QUERY_PARAM);
        if (com.example.ShoppingSystem.loginflow.LoginFlowWebSupport.isSharedWithRegisterPath(requestPath)
                && com.example.ShoppingSystem.loginflow.LoginFlowWebSupport.LOGIN_MODE.equals(mode)) {
            return true;
        }
        if (com.example.ShoppingSystem.loginflow.LoginFlowWebSupport.isSharedWithRegisterPath(requestPath)
                && org.springframework.util.StringUtils.hasText(loginFlowCookieFactory.resolveFlowId(request))
                && !org.springframework.util.StringUtils.hasText(flowId)) {
            return true;
        }
        String preAuthToken = preAuthBindingService.resolveIncomingToken(request);
        RegisterFlowValidationResult validationResult = registerFlowSessionService.validate(flowId, preAuthToken);
        if (!validationResult.valid()) {
            clearFlowCookie(response, request);
            redirect(response, RegisterFlowWebSupport.sessionEndedWithNotice());
            return false;
        }

        RegisterFlowSession session = validationResult.session();
        RegisterFlowStep step = session.getStep();
        if (session.isCompleted() || step == RegisterFlowStep.DONE) {
            redirect(response, RegisterFlowWebSupport.withNotice(
                    RegisterFlowWebSupport.LOGIN_PATH,
                    RegisterFlowWebSupport.NOTICE_REGISTER_COMPLETED
            ));
            return false;
        }

        String expectedPath = RegisterFlowWebSupport.pathForStep(step);
        if (!requestPath.equals(expectedPath)) {
            redirect(response, RegisterFlowWebSupport.withNotice(
                    RegisterFlowWebSupport.routeForStep(step),
                    RegisterFlowWebSupport.NOTICE_STEP_RESTORED
            ));
            return false;
        }

        return true;
    }

    private void clearFlowCookie(HttpServletResponse response, HttpServletRequest request) {
        response.addHeader("Set-Cookie", registerFlowCookieFactory.buildExpiredFlowCookie(request).toString());
    }

    private void redirect(HttpServletResponse response, String location) throws Exception {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(HttpServletResponse.SC_FOUND);
        response.setHeader("Location", location);
    }
}
