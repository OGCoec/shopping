package com.example.ShoppingSystem.service.captcha.thirdparty;

public interface ThirdPartyCaptchaService {

    /**
     * 返回 Cloudflare Turnstile 前端渲染所需的公开 siteKey。
     */
    String getTurnstileSiteKey();

    /**
     * 返回 hCaptcha 前端渲染所需的公开 siteKey。
     */
    String getHCaptchaSiteKey();

    String getRecaptchaSiteKey();

    /**
     * 使用 Cloudflare 官方 siteverify 接口校验前端返回的 token。
     */
    boolean validateTurnstile(String token, String remoteIp);

    /**
     * 使用 hCaptcha 官方 siteverify 接口校验前端返回的 token。
     */
    boolean validateHCaptcha(String token, String remoteIp);

    boolean validateRecaptchaV3(String token, String remoteIp);
}
