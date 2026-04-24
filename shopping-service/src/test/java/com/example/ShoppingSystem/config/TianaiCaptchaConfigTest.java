package com.example.ShoppingSystem.config;

import cloud.tianai.captcha.common.AnyMap;
import cloud.tianai.captcha.common.constant.CaptchaTypeConstant;
import cloud.tianai.captcha.common.response.ApiResponse;
import cloud.tianai.captcha.interceptor.CaptchaInterceptor;
import cloud.tianai.captcha.validator.ImageCaptchaValidator;
import cloud.tianai.captcha.validator.common.constant.TrackTypeConstant;
import cloud.tianai.captcha.validator.common.model.dto.ImageCaptchaTrack;
import cloud.tianai.captcha.validator.common.model.dto.MatchParam;
import cloud.tianai.captcha.validator.impl.SimpleImageCaptchaValidator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TianaiCaptchaConfigTest {

    @Test
    void imageCaptchaValidatorUsesSimpleValidatorForBasicPositionCheck() {
        TianaiCaptchaConfig config = new TianaiCaptchaConfig();

        ImageCaptchaValidator validator = config.imageCaptchaValidator();

        assertInstanceOf(SimpleImageCaptchaValidator.class, validator);
    }

    @Test
    void captchaInterceptorRejectsMachineLikeSliderTracks() {
        TianaiCaptchaConfig config = new TianaiCaptchaConfig();
        config.imageCaptchaValidator();
        CaptchaInterceptor interceptor = config.captchaInterceptor();

        ApiResponse<?> response = interceptor.afterValid(
                interceptor.createContext(),
                CaptchaTypeConstant.SLIDER,
                new MatchParam(buildMachineLikeTrack()),
                new AnyMap(),
                ApiResponse.ofSuccess());

        assertFalse(response.isSuccess());
    }

    @Test
    void captchaInterceptorBypassesRotateAndWordClickBehaviorChecks() {
        TianaiCaptchaConfig config = new TianaiCaptchaConfig();
        config.imageCaptchaValidator();
        CaptchaInterceptor interceptor = config.captchaInterceptor();

        ApiResponse<?> rotateResponse = interceptor.afterValid(
                interceptor.createContext(),
                CaptchaTypeConstant.ROTATE,
                new MatchParam(buildMachineLikeTrack()),
                new AnyMap(),
                ApiResponse.ofSuccess());
        ApiResponse<?> wordClickResponse = interceptor.afterValid(
                interceptor.createContext(),
                CaptchaTypeConstant.WORD_IMAGE_CLICK,
                new MatchParam(buildMachineLikeTrack()),
                new AnyMap(),
                ApiResponse.ofSuccess());

        assertTrue(rotateResponse.isSuccess());
        assertTrue(wordClickResponse.isSuccess());
    }

    @Test
    void captchaInterceptorAlsoRejectsMachineLikeConcatTracks() {
        TianaiCaptchaConfig config = new TianaiCaptchaConfig();
        config.imageCaptchaValidator();
        CaptchaInterceptor interceptor = config.captchaInterceptor();

        ApiResponse<?> response = interceptor.afterValid(
                interceptor.createContext(),
                CaptchaTypeConstant.CONCAT,
                new MatchParam(buildMachineLikeTrack()),
                new AnyMap(),
                ApiResponse.ofSuccess());

        assertFalse(response.isSuccess());
    }

    private ImageCaptchaTrack buildMachineLikeTrack() {
        ImageCaptchaTrack track = new ImageCaptchaTrack();
        track.setBgImageWidth(260);
        track.setBgImageHeight(120);
        track.setStartTime(0L);
        track.setStopTime(120L);
        track.setTrackList(List.of(
                new ImageCaptchaTrack.Track(0F, 0F, 0F, TrackTypeConstant.DOWN),
                new ImageCaptchaTrack.Track(120F, 0F, 120F, TrackTypeConstant.UP)
        ));
        return track;
    }
}
