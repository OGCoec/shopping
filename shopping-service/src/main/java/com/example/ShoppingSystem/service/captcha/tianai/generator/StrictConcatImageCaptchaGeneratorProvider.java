package com.example.ShoppingSystem.service.captcha.tianai.generator;

import cloud.tianai.captcha.common.constant.CaptchaTypeConstant;
import cloud.tianai.captcha.generator.ImageCaptchaGenerator;
import cloud.tianai.captcha.generator.ImageCaptchaGeneratorProvider;
import cloud.tianai.captcha.generator.ImageTransform;
import cloud.tianai.captcha.interceptor.CaptchaInterceptor;
import cloud.tianai.captcha.resource.ImageCaptchaResourceManager;
import org.springframework.stereotype.Component;

/**
 * Override default CONCAT generator with strict split range + moving layer metadata.
 */
@Component
public class StrictConcatImageCaptchaGeneratorProvider implements ImageCaptchaGeneratorProvider {

    @Override
    public ImageCaptchaGenerator get(ImageCaptchaResourceManager imageCaptchaResourceManager,
                                     ImageTransform imageTransform,
                                     CaptchaInterceptor captchaInterceptor) {
        return new StrictConcatImageCaptchaGenerator(imageCaptchaResourceManager, imageTransform, captchaInterceptor);
    }

    @Override
    public String getType() {
        return CaptchaTypeConstant.CONCAT;
    }
}
