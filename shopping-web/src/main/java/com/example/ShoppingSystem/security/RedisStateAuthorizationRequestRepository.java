package com.example.ShoppingSystem.security;

import com.example.ShoppingSystem.redisdata.GithubRedisKeys;
import com.example.ShoppingSystem.redisdata.GoogleRedisKeys;
import com.example.ShoppingSystem.redisdata.MicrosoftRedisKeys;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * OAuth2 state 的 Redis 防重放仓库。
 *
 * 说明：
 * 1. 仍使用 HttpSession 存储 OAuth2AuthorizationRequest（兼容 Spring Security 默认流程）
 * 2. 额外把 state 写入 Redis，并在回调时删除（一次性消费）
 * 3. Redis 中 state 缺失或删除失败时，视为无效/重放，直接拒绝
 */
@Component
public class RedisStateAuthorizationRequestRepository implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    private final HttpSessionOAuth2AuthorizationRequestRepository delegate =
            new HttpSessionOAuth2AuthorizationRequestRepository();

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 原子校验并删除脚本：
     * - 值匹配 expectedValue 时删除并返回 1
     * - 不匹配或不存在返回 0
     */
    private static final DefaultRedisScript<Long> COMPARE_AND_DELETE_SCRIPT =
            new DefaultRedisScript<>(
                    "if redis.call('GET', KEYS[1]) == ARGV[1] then " +
                            "return redis.call('DEL', KEYS[1]) " +
                            "else return 0 end",
                    Long.class
            );

    public RedisStateAuthorizationRequestRepository(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        return delegate.loadAuthorizationRequest(request);
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest,
                                         HttpServletRequest request,
                                         HttpServletResponse response) {
        delegate.saveAuthorizationRequest(authorizationRequest, request, response);

        if (authorizationRequest == null || !StringUtils.hasText(authorizationRequest.getState())) {
            return;
        }

        String registrationId = extractRegistrationIdFromAuthorizationPath(request);
        String prefix = statePrefixOf(registrationId);
        if (prefix == null) {
            return;
        }

        String redisKey = prefix + authorizationRequest.getState();
        // 把 provider 名写入 value，回调时做“值匹配 + 删除”原子校验
        stringRedisTemplate.opsForValue().set(redisKey, registrationId, stateTtlMinutesOf(registrationId), TimeUnit.MINUTES);
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,
                                                                 HttpServletResponse response) {
        OAuth2AuthorizationRequest authorizationRequest = delegate.removeAuthorizationRequest(request, response);
        if (authorizationRequest == null) {
            return null;
        }

        String state = request.getParameter("state");
        if (!StringUtils.hasText(state)) {
            return null;
        }

        String registrationId = extractRegistrationIdFromCallbackPath(request);
        String prefix = statePrefixOf(registrationId);
        if (prefix == null) {
            return null;
        }

        String redisKey = prefix + state;
        Long deleted = stringRedisTemplate.execute(
                COMPARE_AND_DELETE_SCRIPT,
                Collections.singletonList(redisKey),
                registrationId
        );
        if (!Long.valueOf(1L).equals(deleted)) {
            return null;
        }

        return authorizationRequest;
    }

    private String extractRegistrationIdFromAuthorizationPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String marker = "/oauth2/authorization/";
        int idx = uri.indexOf(marker);
        if (idx < 0) {
            return null;
        }
        String registrationId = uri.substring(idx + marker.length());
        return StringUtils.hasText(registrationId) ? registrationId : null;
    }

    private String extractRegistrationIdFromCallbackPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String marker = "/login/oauth2/code/";
        int idx = uri.indexOf(marker);
        if (idx < 0) {
            return null;
        }
        String registrationId = uri.substring(idx + marker.length());
        return StringUtils.hasText(registrationId) ? registrationId : null;
    }

    private String statePrefixOf(String registrationId) {
        if (!StringUtils.hasText(registrationId)) {
            return null;
        }
        return switch (registrationId) {
            case "github" -> GithubRedisKeys.STATE_PREFIX;
            case "google" -> GoogleRedisKeys.STATE_PREFIX;
            case "microsoft" -> MicrosoftRedisKeys.STATE_PREFIX;
            default -> null;
        };
    }

    private long stateTtlMinutesOf(String registrationId) {
        if (!StringUtils.hasText(registrationId)) {
            return 5L;
        }
        return switch (registrationId) {
            case "github" -> GithubRedisKeys.STATE_TTL_MINUTES;
            case "google" -> GoogleRedisKeys.STATE_TTL_MINUTES;
            case "microsoft" -> MicrosoftRedisKeys.STATE_TTL_MINUTES;
            default -> 5L;
        };
    }
}
