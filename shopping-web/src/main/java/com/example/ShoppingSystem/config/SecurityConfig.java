package com.example.ShoppingSystem.config;

import cn.hutool.core.util.IdUtil;
import com.example.ShoppingSystem.security.OAuth2LoginFailureHandler;
import com.example.ShoppingSystem.security.OAuth2LoginSuccessHandler;
import com.example.ShoppingSystem.security.OAuth2PreAuthRiskFilter;
import com.example.ShoppingSystem.security.RedisStateAuthorizationRequestRepository;
import org.springframework.core.annotation.Order;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String[] APP_SECURITY_PATHS = {
            "/",
            "/index.html",
            "/favicon.ico",
            "/shopping/**",
            "/shopping/admin/**",
            "/oauth2/**",
            "/login/oauth2/code/**",
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
            "/webjars/**",
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/doc.html"
    };

    private static final String[] PUBLIC_PATHS = {
            "/",
            "/index.html",
            "/favicon.ico",
            "/shopping/user/log-in",
            "/shopping/user/log-in/password",
            "/shopping/user/lojin",
            "/shopping/user/firstlogin",
            "/shopping/admin/**",
            "/shopping/user/login",
            "/shopping/user/login/**",
            "/shopping/user/create-account",
            "/shopping/user/create-account/password",
            "/shopping/user/register",
            "/shopping/user/register/**",
            "/shopping/user/email-verification",
            "/shopping/user/totp-verification",
            "/shopping/user/add-phone",
            "/shopping/user/session-ended",
            "/shopping/user/profile",
            "/shopping/user/console",
            "/shopping/user/forgot-password",
            "/shopping/user/forgot-password/**",
            "/shopping/user/reset-password-url",
            "/shopping/user/reset-password-code",
            "/shopping/auth/network-check-failed",
            "/shopping/auth/preauth/bootstrap",
            "/shopping/auth/preauth/phone-country",
            "/shopping/auth/preauth/phone-validate",
            "/shopping/auth/waf/verify",
            "/shopping/user/auth/me",
            "/shopping/user/session/page-gate",
            "/shopping/user/auth/refresh",
            "/shopping/user/auth/logout",
            "/shopping/user/auth/logout-all",
            "/shopping/user/profile/avatar",
            "/shopping/user/profile/deletion",
            "/shopping/user/security/phone",
            "/shopping/user/security/phone/**",
            "/shopping/user/totp",
            "/shopping/user/totp/**",
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
            "/webjars/**",
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/doc.html",
            "/oauth2/github/login",
            "/oauth2/google/login",
            "/oauth2/microsoft/login",
            "/oauth2/authorization/**",
            "/login/oauth2/code/**"
    };

    private static final String[] LEGACY_PAGE_PATHS = {
            "/login",
            "/login.html",
            "/register.html",
            "/forgot-password.html"
    };

    @Bean
    @Order(1)
    public SecurityFilterChain legacyPageSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher(LEGACY_PAGE_PATHS)
                .authorizeHttpRequests(auth -> auth.anyRequest().denyAll())
                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain errorSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/error", "/error/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .build();
    }

    @Bean
    @Order(3)
    public SecurityFilterChain appSecurityFilterChain(HttpSecurity http,
                                                      OAuth2LoginSuccessHandler successHandler,
                                                      OAuth2LoginFailureHandler failureHandler,
                                                      RedisStateAuthorizationRequestRepository redisStateAuthorizationRequestRepository,
                                                      OAuth2AuthorizationRequestResolver oauth2AuthorizationRequestResolver,
                                                      OAuth2PreAuthRiskFilter oauth2PreAuthRiskFilter) throws Exception {
        return http
                .securityMatcher(APP_SECURITY_PATHS)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS)
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .oauth2Login(oauth2 -> oauth2
                        // 指定自定义登录页，禁用 Spring Security 默认 /login 登录页
                        .loginPage("/shopping/user/log-in")
                        .authorizationEndpoint(endpoint -> endpoint
                                .authorizationRequestResolver(oauth2AuthorizationRequestResolver)
                                .authorizationRequestRepository(redisStateAuthorizationRequestRepository))
                        .successHandler(successHandler)
                        .failureHandler(failureHandler))
                .addFilterBefore(oauth2PreAuthRiskFilter, OAuth2AuthorizationRequestRedirectFilter.class)
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
                        .ignoringRequestMatchers(
                                "/shopping/auth/preauth/**",
                                "/shopping/admin/**",
                                "/shopping/user/forgot-password",
                                "/shopping/user/forgot-password/**"
                        ))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 统一把 OAuth2 state 生成规则改为 48 位随机串，便于与 Redis 防重放策略保持一致。
     */
    @Bean
    public OAuth2AuthorizationRequestResolver oauth2AuthorizationRequestResolver(ClientRegistrationRepository clientRegistrationRepository) {
        DefaultOAuth2AuthorizationRequestResolver resolver =
                new DefaultOAuth2AuthorizationRequestResolver(clientRegistrationRepository, "/oauth2/authorization");
        resolver.setAuthorizationRequestCustomizer(builder -> builder.state(IdUtil.nanoId(48)));
        return resolver;
    }
}
