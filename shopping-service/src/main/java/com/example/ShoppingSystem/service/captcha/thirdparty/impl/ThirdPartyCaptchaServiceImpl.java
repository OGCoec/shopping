package com.example.ShoppingSystem.service.captcha.thirdparty.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.example.ShoppingSystem.service.captcha.thirdparty.ThirdPartyCaptchaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
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
@Service
public class ThirdPartyCaptchaServiceImpl implements ThirdPartyCaptchaService {

    private static final Duration VERIFY_TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient httpClient;
    private final String turnstileSiteKey;
    private final String turnstileSecretKey;
    private final String turnstileVerifyUrl;
    private final String hCaptchaSiteKey;
    private final String hCaptchaSecretKey;
    private final String hCaptchaVerifyUrl;

    @Autowired
    public ThirdPartyCaptchaServiceImpl(
            @Value("${captcha.turnstile.site-key:}") String turnstileSiteKey,
            @Value("${captcha.turnstile.secret-key:}") String turnstileSecretKey,
            @Value("${captcha.turnstile.verify-url:https://challenges.cloudflare.com/turnstile/v0/siteverify}") String turnstileVerifyUrl,
            @Value("${captcha.hcaptcha.site-key:}") String hCaptchaSiteKey,
            @Value("${captcha.hcaptcha.secret-key:}") String hCaptchaSecretKey,
            @Value("${captcha.hcaptcha.verify-url:https://api.hcaptcha.com/siteverify}") String hCaptchaVerifyUrl) {
        this(HttpClient.newBuilder().connectTimeout(VERIFY_TIMEOUT).build(),
                turnstileSiteKey,
                turnstileSecretKey,
                turnstileVerifyUrl,
                hCaptchaSiteKey,
                hCaptchaSecretKey,
                hCaptchaVerifyUrl);
    }

    ThirdPartyCaptchaServiceImpl(HttpClient httpClient,
                                 String turnstileSiteKey,
                                 String turnstileSecretKey,
                                 String turnstileVerifyUrl,
                                 String hCaptchaSiteKey,
                                 String hCaptchaSecretKey,
                                 String hCaptchaVerifyUrl) {
        this.httpClient = httpClient;
        this.turnstileSiteKey = turnstileSiteKey;
        this.turnstileSecretKey = turnstileSecretKey;
        this.turnstileVerifyUrl = turnstileVerifyUrl;
        this.hCaptchaSiteKey = hCaptchaSiteKey;
        this.hCaptchaSecretKey = hCaptchaSecretKey;
        this.hCaptchaVerifyUrl = hCaptchaVerifyUrl;
    }

    @Override
    public String getTurnstileSiteKey() {
        return turnstileSiteKey;
    }

    @Override
    public String getHCaptchaSiteKey() {
        return hCaptchaSiteKey;
    }

    @Override
    public boolean validateTurnstile(String token, String remoteIp) {
        // 只要服务端密钥或前端回传 token 缺失，就没有调用官方接口的意义，直接判失败。
        if (StrUtil.hasBlank(turnstileSecretKey, token)) {
            return false;
        }

        try {
            // 按 Cloudflare Turnstile siteverify 要求构造 form-urlencoded 请求体。
            String body = formBody(
                    "secret", turnstileSecretKey,
                    "response", token,
                    "remoteip", remoteIp
            );
            // 构造一个短超时的 HTTP POST，请求第三方验证接口，避免验证码验证把注册链路长时间阻塞。
            HttpRequest request = HttpRequest.newBuilder(URI.create(turnstileVerifyUrl))
                    .timeout(VERIFY_TIMEOUT)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            // 发送请求并同步等待结果；这里的响应体会在后面解析 success 字段。
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            // 只有 2xx 才认为是第三方接口正常响应，其它状态码统一记日志并失败。
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Cloudflare Turnstile siteverify returned httpStatus={}", response.statusCode());
                return false;
            }
            // 官方返回 JSON 中的 success=true 才代表 token 通过校验。
            boolean success = JSONUtil.parseObj(response.body()).getBool("success", false);
            if (!success) {
                // 第三方明确返回失败时把完整响应记出来，方便后面看 error-codes。
                log.warn("Cloudflare Turnstile validation failed, response={}", response.body());
            }
            return success;
        } catch (IOException | InterruptedException | RuntimeException e) {
            // 如果是中断异常，要把线程中断标记补回去，避免上层线程池吞掉中断语义。
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            // 所有网络异常、JSON 解析异常、运行时异常统一降级成“验证码未通过”。
            log.warn("Cloudflare Turnstile validation error: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 调用 hCaptcha 官方 siteverify 接口校验 token。
     * 这里按官方要求使用 `application/x-www-form-urlencoded`，
     * 并额外带上 `remoteip` 与 `sitekey`，避免 token 被跨站点复用。
     */
    @Override
    public boolean validateHCaptcha(String token, String remoteIp) {
        // hCaptcha 同样要求服务端 secret 和前端 token 都存在，否则无法完成校验。
        if (StrUtil.hasBlank(hCaptchaSecretKey, token)) {
            return false;
        }

        try {
            // hCaptcha 要求 form-urlencoded，并建议同时提交 remoteip 和 sitekey 做额外约束。
            String body = formBody(
                    "secret", hCaptchaSecretKey,
                    "response", token,
                    "remoteip", remoteIp,
                    "sitekey", hCaptchaSiteKey
            );
            // 构造请求时和 Turnstile 保持一致，统一超时、统一内容类型，便于维护。
            HttpRequest request = HttpRequest.newBuilder(URI.create(hCaptchaVerifyUrl))
                    .timeout(VERIFY_TIMEOUT)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            // 发送到 hCaptcha 官方 siteverify 接口并读取字符串响应。
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            // 非 2xx 先视为第三方接口不可用，不继续解析 body。
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("hCaptcha siteverify returned httpStatus={}", response.statusCode());
                return false;
            }
            // 只读取 success 字段作为当前业务的通过标准，其它 error-codes 仅用于日志排查。
            boolean success = JSONUtil.parseObj(response.body()).getBool("success", false);
            if (!success) {
                // 失败时保留完整响应，方便确认是过期、重复使用还是站点密钥不匹配。
                log.warn("hCaptcha validation failed, response={}", response.body());
            }
            return success;
        } catch (IOException | InterruptedException | RuntimeException e) {
            // 保留线程中断语义，避免中断被 catch 后彻底丢失。
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            // 任何异常都按“第三方验证码验证失败”处理，不把异常直接抛给上层业务。
            log.warn("hCaptcha validation error: {}", e.getMessage());
            return false;
        }
    }

    private String formBody(String... pairs) {
        // 把成对出现的 key/value 转成 application/x-www-form-urlencoded 形式。
        // 这里允许 value 为空时跳过该字段，避免生成 `field=` 这样的无意义参数。
        List<String> encodedPairs = new ArrayList<>();
        for (int i = 0; i < pairs.length; i += 2) {
            String value = pairs[i + 1];
            if (StrUtil.isBlank(value)) {
                continue;
            }
            // key 和 value 都做 URL 编码，避免 ip、token、secret 中的特殊字符破坏表单格式。
            encodedPairs.add(urlEncode(pairs[i]) + "=" + urlEncode(value));
        }
        // 最终用 & 串起来，形成标准表单请求体。
        return String.join("&", encodedPairs);
    }

    private String urlEncode(String value) {
        // 统一按 UTF-8 编码，保证和第三方官方接口要求一致。
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
