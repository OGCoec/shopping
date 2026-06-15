package com.example.ShoppingSystem.service.captcha.strategy;

import cloud.tianai.captcha.application.vo.ImageCaptchaVO;

public record CaptchaGenerateResult(String uuid, String image, ImageCaptchaVO tianaiCaptcha) {

    public static CaptchaGenerateResult hutool(String uuid, String image) {
        return new CaptchaGenerateResult(uuid, image, null);
    }

    public static CaptchaGenerateResult tianai(ImageCaptchaVO captcha) {
        return new CaptchaGenerateResult(null, null, captcha);
    }
}
