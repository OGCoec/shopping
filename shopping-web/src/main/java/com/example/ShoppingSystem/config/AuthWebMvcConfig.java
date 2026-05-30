package com.example.ShoppingSystem.config;

import com.example.ShoppingSystem.admin.interceptor.AdminSessionInterceptor;
import com.example.ShoppingSystem.admin.interceptor.AdminIpChangeWafInterceptor;
import com.example.ShoppingSystem.interceptor.LoginFlowGuardInterceptor;
import com.example.ShoppingSystem.interceptor.PasswordResetTokenGuardInterceptor;
import com.example.ShoppingSystem.interceptor.PhoneBindingRequiredInterceptor;
import com.example.ShoppingSystem.interceptor.PostLoginAccountNetworkRiskInterceptor;
import com.example.ShoppingSystem.interceptor.PreAuthInterceptor;
import com.example.ShoppingSystem.interceptor.RegisterFlowGuardInterceptor;
import com.example.ShoppingSystem.interceptor.WebRtcIpConsistencyInterceptor;
import com.example.ShoppingSystem.registerflow.RegisterFlowWebSupport;
import com.example.ShoppingSystem.security.token.AccessTokenAuthenticationInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC interceptor registration for semantic auth step routes.
 */
@Configuration
public class AuthWebMvcConfig implements WebMvcConfigurer {

    private final PreAuthInterceptor preAuthInterceptor;
    private final WebRtcIpConsistencyInterceptor webRtcIpConsistencyInterceptor;
    private final RegisterFlowGuardInterceptor registerFlowGuardInterceptor;
    private final LoginFlowGuardInterceptor loginFlowGuardInterceptor;
    private final PasswordResetTokenGuardInterceptor passwordResetTokenGuardInterceptor;
    private final AccessTokenAuthenticationInterceptor accessTokenAuthenticationInterceptor;
    private final PostLoginAccountNetworkRiskInterceptor postLoginAccountNetworkRiskInterceptor;
    private final PhoneBindingRequiredInterceptor phoneBindingRequiredInterceptor;
    private final AdminIpChangeWafInterceptor adminIpChangeWafInterceptor;
    private final AdminSessionInterceptor adminSessionInterceptor;

    public AuthWebMvcConfig(PreAuthInterceptor preAuthInterceptor,
                            WebRtcIpConsistencyInterceptor webRtcIpConsistencyInterceptor,
                            RegisterFlowGuardInterceptor registerFlowGuardInterceptor,
                            LoginFlowGuardInterceptor loginFlowGuardInterceptor,
                            PasswordResetTokenGuardInterceptor passwordResetTokenGuardInterceptor,
                            AccessTokenAuthenticationInterceptor accessTokenAuthenticationInterceptor,
                            PostLoginAccountNetworkRiskInterceptor postLoginAccountNetworkRiskInterceptor,
                            PhoneBindingRequiredInterceptor phoneBindingRequiredInterceptor,
                            AdminIpChangeWafInterceptor adminIpChangeWafInterceptor,
                            AdminSessionInterceptor adminSessionInterceptor) {
        this.preAuthInterceptor = preAuthInterceptor;
        this.webRtcIpConsistencyInterceptor = webRtcIpConsistencyInterceptor;
        this.registerFlowGuardInterceptor = registerFlowGuardInterceptor;
        this.loginFlowGuardInterceptor = loginFlowGuardInterceptor;
        this.passwordResetTokenGuardInterceptor = passwordResetTokenGuardInterceptor;
        this.accessTokenAuthenticationInterceptor = accessTokenAuthenticationInterceptor;
        this.postLoginAccountNetworkRiskInterceptor = postLoginAccountNetworkRiskInterceptor;
        this.phoneBindingRequiredInterceptor = phoneBindingRequiredInterceptor;
        this.adminIpChangeWafInterceptor = adminIpChangeWafInterceptor;
        this.adminSessionInterceptor = adminSessionInterceptor;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/shopping/css/**")
                .addResourceLocations("classpath:/static/css/");
        registry.addResourceHandler("/shopping/js/**")
                .addResourceLocations("classpath:/static/js/");
        registry.addResourceHandler("/shopping/error/**")
                .addResourceLocations("classpath:/static/error/");
        registry.addResourceHandler("/shopping/fonts/**")
                .addResourceLocations("classpath:/static/fonts/");
        registry.addResourceHandler("/shopping/images/**")
                .addResourceLocations("classpath:/static/images/");
        registry.addResourceHandler("/shopping/admin/panels/**")
                .addResourceLocations("classpath:/static/admin/panels/");
        registry.addResourceHandler("/shopping/fragments/**")
                .addResourceLocations("classpath:/static/fragments/");
        registry.addResourceHandler("/shopping/favicon.ico")
                .addResourceLocations("classpath:/static/");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminIpChangeWafInterceptor)
                .addPathPatterns(
                        "/shopping/admin/console",
                        "/shopping/admin/console/**",
                        "/shopping/admin/api/**",
                        "/shopping/admin/session/me"
                )
                .order(-30);

        registry.addInterceptor(webRtcIpConsistencyInterceptor)
                .addPathPatterns("/shopping/admin/**")
                .excludePathPatterns(
                        "/css/**",
                        "/js/**",
                        "/images/**",
                        "/fragments/**",
                        "/shopping/css/**",
                        "/shopping/js/**",
                        "/shopping/images/**",
                        "/shopping/admin/panels/**",
                        "/shopping/fragments/**",
                        "/shopping/error/**",
                        "/shopping/fonts/**",
                        "/shopping/favicon.ico",
                        "/shopping/auth/network-check-failed",
                        "/webjars/**",
                        "/shopping/admin/password-crypto/key",
                        "/shopping/admin/console",
                        "/shopping/admin/console/**"
                )
                .order(-10);

        registry.addInterceptor(webRtcIpConsistencyInterceptor)
                .addPathPatterns("/shopping/**")
                .excludePathPatterns(
                        "/shopping/admin/**",
                        "/shopping/auth/waf/verify",
                        "/shopping/auth/network-check-failed",
                        "/css/**",
                        "/js/**",
                        "/images/**",
                        "/fragments/**",
                        "/shopping/css/**",
                        "/shopping/js/**",
                        "/shopping/images/**",
                        "/shopping/fragments/**",
                        "/shopping/error/**",
                        "/shopping/fonts/**",
                        "/shopping/favicon.ico",
                        "/webjars/**"
                )
                .order(-10);

        registry.addInterceptor(preAuthInterceptor)
                .addPathPatterns("/shopping/**")
                .excludePathPatterns(
                        "/shopping/auth/preauth/bootstrap",
                        "/shopping/auth/preauth/phone-country",
                        "/shopping/auth/waf/verify",
                        "/shopping/user/login",
                        "/shopping/user/log-in",
                        "/shopping/user/log-in/password",
                        "/shopping/user/lojin",
                        "/shopping/user/firstlogin",
                        "/shopping/admin/**",
                        "/shopping/user/register",
                        "/shopping/user/create-account",
                        "/shopping/user/create-account/password",
                        "/shopping/user/email-verification",
                        "/shopping/user/totp-verification",
                        "/shopping/user/add-phone",
                        "/shopping/user/session-ended",
                        "/shopping/user/forgot-password",
                        "/shopping/user/reset-password-url",
                        "/shopping/user/reset-password-code",
                        "/shopping/auth/network-check-failed",
                        "/css/**",
                        "/js/**",
                        "/images/**",
                        "/fragments/**",
                        "/shopping/css/**",
                        "/shopping/js/**",
                        "/shopping/images/**",
                        "/shopping/fragments/**",
                        "/shopping/error/**",
                        "/shopping/fonts/**",
                        "/shopping/favicon.ico",
                        "/webjars/**"
                )
                .order(-20);

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

        registry.addInterceptor(adminSessionInterceptor)
                .addPathPatterns("/shopping/admin/**")
                .excludePathPatterns(
                        "/shopping/admin/login",
                        "/shopping/admin/password-crypto/key",
                        "/shopping/admin/firstlogin",
                        "/shopping/admin/firstlogin/**"
                )
                .order(90);

        registry.addInterceptor(accessTokenAuthenticationInterceptor)
                .addPathPatterns(
                        "/shopping/user/auth/me",
                        "/shopping/user/products/**",
                        "/shopping/user/session/page-gate",
                        "/shopping/user/auth/logout-all",
                        "/shopping/user/profile/avatar",
                        "/shopping/user/profile/deletion",
                        "/shopping/api/product-categories/**",
                        "/shopping/api/products/**",
                        "/shopping/user/security/phone/**",
                        "/shopping/user/totp",
                        "/shopping/user/totp/**",
                        "/shopping/user/api/coupons/**"
                )
                .order(100);

        registry.addInterceptor(postLoginAccountNetworkRiskInterceptor)
                .addPathPatterns(
                        "/shopping/user/auth/me",
                        "/shopping/user/products/**",
                        "/shopping/user/session/page-gate",
                        "/shopping/user/auth/logout-all",
                        "/shopping/user/profile/avatar",
                        "/shopping/user/profile/deletion",
                        "/shopping/api/product-categories/**",
                        "/shopping/api/products/**",
                        "/shopping/user/security/phone/**",
                        "/shopping/user/totp",
                        "/shopping/user/totp/**",
                        "/shopping/user/api/coupons/**"
                )
                .order(105);

        registry.addInterceptor(phoneBindingRequiredInterceptor)
                .addPathPatterns(
                        "/shopping/user/profile/avatar",
                        "/shopping/user/products/**",
                        "/shopping/user/session/page-gate",
                        "/shopping/user/profile/deletion",
                        "/shopping/api/product-categories/**",
                        "/shopping/api/products/**",
                        "/shopping/user/totp",
                        "/shopping/user/totp/**",
                        "/shopping/user/api/coupons/**"
                )
                .order(110);
    }
}
