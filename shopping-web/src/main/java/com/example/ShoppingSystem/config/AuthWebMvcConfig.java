package com.example.ShoppingSystem.config;

import com.example.ShoppingSystem.interceptor.LoginFlowGuardInterceptor;
import com.example.ShoppingSystem.interceptor.PasswordResetTokenGuardInterceptor;
import com.example.ShoppingSystem.interceptor.PhoneBindingRequiredInterceptor;
import com.example.ShoppingSystem.interceptor.PreAuthInterceptor;
import com.example.ShoppingSystem.interceptor.RegisterFlowGuardInterceptor;
import com.example.ShoppingSystem.registerflow.RegisterFlowWebSupport;
import com.example.ShoppingSystem.security.token.AccessTokenAuthenticationInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC interceptor registration for semantic auth step routes.
 */
@Configuration
public class AuthWebMvcConfig implements WebMvcConfigurer {

    private final PreAuthInterceptor preAuthInterceptor;
    private final RegisterFlowGuardInterceptor registerFlowGuardInterceptor;
    private final LoginFlowGuardInterceptor loginFlowGuardInterceptor;
    private final PasswordResetTokenGuardInterceptor passwordResetTokenGuardInterceptor;
    private final AccessTokenAuthenticationInterceptor accessTokenAuthenticationInterceptor;
    private final PhoneBindingRequiredInterceptor phoneBindingRequiredInterceptor;

    public AuthWebMvcConfig(PreAuthInterceptor preAuthInterceptor,
                            RegisterFlowGuardInterceptor registerFlowGuardInterceptor,
                            LoginFlowGuardInterceptor loginFlowGuardInterceptor,
                            PasswordResetTokenGuardInterceptor passwordResetTokenGuardInterceptor,
                            AccessTokenAuthenticationInterceptor accessTokenAuthenticationInterceptor,
                            PhoneBindingRequiredInterceptor phoneBindingRequiredInterceptor) {
        this.preAuthInterceptor = preAuthInterceptor;
        this.registerFlowGuardInterceptor = registerFlowGuardInterceptor;
        this.loginFlowGuardInterceptor = loginFlowGuardInterceptor;
        this.passwordResetTokenGuardInterceptor = passwordResetTokenGuardInterceptor;
        this.accessTokenAuthenticationInterceptor = accessTokenAuthenticationInterceptor;
        this.phoneBindingRequiredInterceptor = phoneBindingRequiredInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(preAuthInterceptor)
                .addPathPatterns("/shopping/**")
                .excludePathPatterns(
                        "/shopping/auth/preauth/bootstrap",
                        "/shopping/auth/preauth/phone-country",
                        "/shopping/auth/waf/verify",
                        "/shopping/user/login",
                        "/shopping/user/log-in",
                        "/shopping/user/log-in/password",
                        "/shopping/user/register",
                        "/shopping/user/create-account",
                        "/shopping/user/create-account/password",
                        "/shopping/user/email-verification",
                        "/shopping/user/totp-verification",
                        "/shopping/user/add-phone",
                        "/shopping/user/session-ended",
                        "/shopping/user/profile",
                        "/shopping/user/console",
                        "/shopping/user/security/phone",
                        "/shopping/user/forgot-password",
                        "/shopping/user/reset-password-url",
                        "/shopping/user/reset-password-code",
                        "/css/**",
                        "/js/**",
                        "/images/**",
                        "/fragments/**",
                        "/webjars/**"
                )
                .order(0);

        registry.addInterceptor(registerFlowGuardInterceptor)
                .addPathPatterns(
                        RegisterFlowWebSupport.CREATE_ACCOUNT_PASSWORD_PATH,
                        RegisterFlowWebSupport.EMAIL_VERIFICATION_PATH,
                        RegisterFlowWebSupport.ADD_PHONE_PATH
                )
                .order(10);
        registry.addInterceptor(loginFlowGuardInterceptor)
                .addPathPatterns(
                        com.example.ShoppingSystem.loginflow.LoginFlowWebSupport.LOGIN_PASSWORD_PATH,
                        com.example.ShoppingSystem.loginflow.LoginFlowWebSupport.EMAIL_VERIFICATION_PATH,
                        com.example.ShoppingSystem.loginflow.LoginFlowWebSupport.TOTP_VERIFICATION_PATH,
                        com.example.ShoppingSystem.loginflow.LoginFlowWebSupport.ADD_PHONE_PATH
                )
                .order(10);
        registry.addInterceptor(passwordResetTokenGuardInterceptor)
                .addPathPatterns(
                        "/shopping/user/reset-password-url",
                        "/shopping/user/reset-password-code"
                )
                .order(10);

        registry.addInterceptor(accessTokenAuthenticationInterceptor)
                .addPathPatterns(
                        "/shopping/user/auth/me",
                        "/shopping/user/auth/logout-all",
                        "/shopping/user/profile/avatar",
                        "/shopping/user/profile/deletion",
                        "/shopping/user/security/phone/**",
                        "/shopping/user/totp",
                        "/shopping/user/totp/**"
                )
                .order(100);

        registry.addInterceptor(phoneBindingRequiredInterceptor)
                .addPathPatterns(
                        "/shopping/user/profile/avatar",
                        "/shopping/user/profile/deletion",
                        "/shopping/user/totp",
                        "/shopping/user/totp/**"
                )
                .order(110);
    }
}
