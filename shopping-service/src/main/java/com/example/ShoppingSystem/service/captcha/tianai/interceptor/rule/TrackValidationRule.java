package com.example.ShoppingSystem.service.captcha.tianai.interceptor.rule;

import cloud.tianai.captcha.validator.common.model.dto.ImageCaptchaTrack;

import java.util.List;

/**
 * 轨迹校验规则接口。
 */
public interface TrackValidationRule {

    /**
     * @param track     完整轨迹对象
     * @param trackList 轨迹点序列
     * @return true 表示通过，false 表示拒绝
     */
    boolean validate(ImageCaptchaTrack track, List<ImageCaptchaTrack.Track> trackList);
}
