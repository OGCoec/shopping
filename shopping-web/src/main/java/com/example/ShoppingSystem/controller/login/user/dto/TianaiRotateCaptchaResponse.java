package com.example.ShoppingSystem.controller.login.user.dto;

import cloud.tianai.captcha.application.vo.ImageCaptchaVO;
import lombok.Builder;
import lombok.Data;

/**
 * 天爱 Rotate 验证码响应。
 */
@Data
@Builder
public class TianaiRotateCaptchaResponse {

    private String captchaId;
    private String type;
    private String backgroundImage;
    private Integer backgroundImageWidth;
    private Integer backgroundImageHeight;
    private String templateImage;
    private Object data;

    public static TianaiRotateCaptchaResponse from(ImageCaptchaVO imageCaptchaVO) {
        return TianaiRotateCaptchaResponse.builder()
                .captchaId(imageCaptchaVO.getId())
                .type(imageCaptchaVO.getType())
                .backgroundImage(imageCaptchaVO.getBackgroundImage())
                .backgroundImageWidth(imageCaptchaVO.getBackgroundImageWidth())
                .backgroundImageHeight(imageCaptchaVO.getBackgroundImageHeight())
                .templateImage(imageCaptchaVO.getTemplateImage())
                .data(imageCaptchaVO.getData())
                .build();
    }
}
