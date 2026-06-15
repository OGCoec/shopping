package com.example.ShoppingSystem.service.captcha.tianai.impl;

import cloud.tianai.captcha.application.vo.ImageCaptchaVO;
import cloud.tianai.captcha.common.response.ApiResponse;
import com.example.ShoppingSystem.service.captcha.tianai.TianaiCaptchaEngine;
import com.example.ShoppingSystem.service.captcha.tianai.TianaiCaptchaService;
import org.springframework.stereotype.Service;

@Service
public class TianaiCaptchaServiceImpl implements TianaiCaptchaService {

    private final TianaiCaptchaEngine tianaiCaptchaEngine;

    public TianaiCaptchaServiceImpl(TianaiCaptchaEngine tianaiCaptchaEngine) {
        this.tianaiCaptchaEngine = tianaiCaptchaEngine;
    }

    @Override
    public ImageCaptchaVO generateRotateCaptcha() {
        return generateRotateCaptcha(null);
    }

    @Override
    public ImageCaptchaVO generateRotateCaptcha(String existingCaptchaId) {
        return tianaiCaptchaEngine.generate("ROTATE", existingCaptchaId);
    }

    @Override
    public ImageCaptchaVO generateSliderCaptcha() {
        return generateSliderCaptcha(null);
    }

    @Override
    public ImageCaptchaVO generateSliderCaptcha(String existingCaptchaId) {
        return tianaiCaptchaEngine.generate("SLIDER", existingCaptchaId);
    }

    @Override
    public ImageCaptchaVO generateConcatCaptcha() {
        return generateConcatCaptcha(null);
    }

    @Override
    public ImageCaptchaVO generateConcatCaptcha(String existingCaptchaId) {
        return tianaiCaptchaEngine.generate("CONCAT", existingCaptchaId);
    }

    @Override
    public ImageCaptchaVO generateWordClickCaptcha() {
        return generateWordClickCaptcha(null);
    }

    @Override
    public ImageCaptchaVO generateWordClickCaptcha(String existingCaptchaId) {
        return tianaiCaptchaEngine.generate("WORD_IMAGE_CLICK", existingCaptchaId);
    }

    @Override
    public ApiResponse<ImageCaptchaVO> generateCaptcha(String subType) {
        return generateCaptcha(subType, null);
    }

    @Override
    public ApiResponse<ImageCaptchaVO> generateCaptcha(String subType, String existingCaptchaId) {
        return tianaiCaptchaEngine.generateResponse(subType, existingCaptchaId);
    }

    @Override
    public boolean validateCaptcha(String captchaId, String captchaData) {
        return tianaiCaptchaEngine.validate(captchaId, captchaData);
    }

    @Override
    public boolean validateRotateCaptcha(String captchaId, Float angle) {
        return tianaiCaptchaEngine.validateRotate(captchaId, angle);
    }
}
