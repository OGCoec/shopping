package com.example.ShoppingSystem.service.captcha.tianai;

import cloud.tianai.captcha.common.response.ApiResponse;
import cloud.tianai.captcha.application.vo.ImageCaptchaVO;

/**
 * 天爱验证码服务接口。
 * 支持 SLIDER / ROTATE / CONCAT / WORD_IMAGE_CLICK 四种子类型的生成与校验。
 */
public interface TianaiCaptchaService {

    ImageCaptchaVO generateRotateCaptcha();

    ImageCaptchaVO generateRotateCaptcha(String existingCaptchaId);

    ImageCaptchaVO generateSliderCaptcha();

    ImageCaptchaVO generateSliderCaptcha(String existingCaptchaId);

    ImageCaptchaVO generateConcatCaptcha();

    ImageCaptchaVO generateConcatCaptcha(String existingCaptchaId);

    ImageCaptchaVO generateWordClickCaptcha();

    ImageCaptchaVO generateWordClickCaptcha(String existingCaptchaId);

    /**
     * 根据子类型生成验证码，返回包含 captchaId 的完整响应。
     */
    ApiResponse<ImageCaptchaVO> generateCaptcha(String subType);

    ApiResponse<ImageCaptchaVO> generateCaptcha(String subType, String existingCaptchaId);

    /**
     * 通用校验入口。
     * @param captchaId 验证码 ID（前端生成时返回的 id）
     * @param captchaData 用户操作数据（滑动/旋转为数值字符串，点选为 JSON 坐标）
     * @return 是否验证通过
     */
    boolean validateCaptcha(String captchaId, String captchaData);

    boolean validateRotateCaptcha(String captchaId, Float angle);
}
