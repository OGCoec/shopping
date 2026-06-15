package com.example.ShoppingSystem.service.captcha.thirdparty;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.example.ShoppingSystem.common.proxy.LocalProxyResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class ThirdPartyCaptchaVerifyClient {

    private static final Duration VERIFY_TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient httpClient;

    public ThirdPartyCaptchaVerifyClient(@Value("${captcha.proxy.enabled:false}") boolean proxyEnabled,
                                         @Value("${captcha.proxy.host:127.0.0.1}") String proxyHost,
                                         @Value("${captcha.proxy.port:0}") int proxyPort,
                                         LocalProxyResolver localProxyResolver) {
        this.httpClient = buildHttpClient(proxyEnabled, proxyHost, proxyPort, localProxyResolver);
    }

    public boolean validate(String providerName,
                            String verifyUrl,
                            String secretKey,
                            String token,
                            String remoteIp,
                            String siteKey,
                            boolean includeSiteKey) {
        if (StrUtil.hasBlank(secretKey, token, verifyUrl)) {
            return false;
        }

        try {
            String body = includeSiteKey
                    ? formBody(
                    "secret", secretKey,
                    "response", token,
                    "remoteip", remoteIp,
                    "sitekey", siteKey
            )
                    : formBody(
                    "secret", secretKey,
                    "response", token,
                    "remoteip", remoteIp
            );
            HttpRequest request = HttpRequest.newBuilder(URI.create(verifyUrl))
                    .timeout(VERIFY_TIMEOUT)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("{} siteverify returned httpStatus={}", providerName, response.statusCode());
                return false;
            }

            boolean success = JSONUtil.parseObj(response.body()).getBool("success", false);
            if (!success) {
                log.warn("{} validation failed, response={}", providerName, response.body());
            }
            return success;
        } catch (IOException | InterruptedException | RuntimeException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("{} validation error: {}", providerName, e.getMessage());
            return false;
        }
    }

    private static HttpClient buildHttpClient(boolean proxyEnabled,
                                              String proxyHost,
                                              int proxyPort,
                                              LocalProxyResolver localProxyResolver) {
        HttpClient.Builder builder = HttpClient.newBuilder().connectTimeout(VERIFY_TIMEOUT);
        boolean effectiveProxyEnabled = proxyEnabled && StrUtil.isNotBlank(proxyHost) && proxyPort > 0;
        log.info("Third-party captcha HTTP proxy config: enabled={}, host={}, port={}, effective={}",
                proxyEnabled,
                StrUtil.blankToDefault(proxyHost, ""),
                proxyPort,
                effectiveProxyEnabled);
        if (effectiveProxyEnabled) {
            LocalProxyResolver.ProxySelection proxySelection =
                    localProxyResolver.resolveOrConfigured(proxyHost, proxyPort);
            InetSocketAddress proxyAddress = proxySelection.address();
            if (proxyAddress != null) {
                log.info("Third-party captcha HTTP proxy selected: host={}, port={}, reachable={}, reason={}",
                        proxyAddress.getHostString(),
                        proxyAddress.getPort(),
                        proxySelection.reachable(),
                        proxySelection.reason());
                builder.proxy(ProxySelector.of(proxyAddress));
            }
        }
        return builder.build();
    }

    private String formBody(String... pairs) {
        List<String> encodedPairs = new ArrayList<>();
        for (int i = 0; i < pairs.length; i += 2) {
            String value = pairs[i + 1];
            if (StrUtil.isBlank(value)) {
                continue;
            }
            encodedPairs.add(urlEncode(pairs[i]) + "=" + urlEncode(value));
        }
        return String.join("&", encodedPairs);
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
