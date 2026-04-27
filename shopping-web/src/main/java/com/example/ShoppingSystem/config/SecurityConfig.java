package com.example.ShoppingSystem.config;

import cn.hutool.core.util.IdUtil;
import com.example.ShoppingSystem.filter.preauth.PreAuthBindingService;
import com.example.ShoppingSystem.filter.preauth.PreAuthFilter;
import com.example.ShoppingSystem.security.OAuth2LoginFailureHandler;
import com.example.ShoppingSystem.security.OAuth2LoginSuccessHandler;
import com.example.ShoppingSystem.security.RedisStateAuthorizationRequestRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/",
            "/index.html",
            "/shopping/user/login",
            "/shopping/user/register",
            "/shopping/user/register/**",
            "/shopping/user/forgot-password",
            "/shopping/auth/preauth/bootstrap",
            "/shopping/auth/preauth/phone-country",
            "/shopping/auth/preauth/phone-validate",
            "/shopping/auth/waf/verify",
            "/css/**",
            "/js/**",
            "/images/**",
            "/fragments/**",
            "/webjars/**",
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/doc.html",
            "/error",
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
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   OAuth2LoginSuccessHandler successHandler,
                                                   OAuth2LoginFailureHandler failureHandler,
                                                   RedisStateAuthorizationRequestRepository redisStateAuthorizationRequestRepository,
                                                   OAuth2AuthorizationRequestResolver oauth2AuthorizationRequestResolver,
                                                   PreAuthFilter preAuthFilter) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(LEGACY_PAGE_PATHS)
                        .denyAll()
                        .requestMatchers(PUBLIC_PATHS)
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .oauth2Login(oauth2 -> oauth2
                        // 指定自定义登录页，禁用 Spring Security 默认 /login 登录页
                        .loginPage("/shopping/user/login")
                        .authorizationEndpoint(endpoint -> endpoint
                                .authorizationRequestResolver(oauth2AuthorizationRequestResolver)
                                .authorizationRequestRepository(redisStateAuthorizationRequestRepository))
                        .successHandler(successHandler)
                        .failureHandler(failureHandler))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
                        .ignoringRequestMatchers(
                                "/shopping/auth/preauth/**",
                                "/shopping/user/forgot-password",
                                "/shopping/user/forgot-password/**"
                        ))
                .addFilterAfter(preAuthFilter, CsrfFilter.class)
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

    @Bean
    public PreAuthFilter preAuthFilter(PreAuthBindingService preAuthBindingService,
                                       ObjectMapper objectMapper) {
        return new PreAuthFilter(preAuthBindingService, objectMapper);
    }
}
