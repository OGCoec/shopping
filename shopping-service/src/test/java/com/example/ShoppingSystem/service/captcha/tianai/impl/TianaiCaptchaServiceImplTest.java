package com.example.ShoppingSystem.service.captcha.tianai.impl;

import cloud.tianai.captcha.application.ImageCaptchaApplication;
import cloud.tianai.captcha.common.response.ApiResponse;
import cloud.tianai.captcha.validator.common.constant.TrackTypeConstant;
import cloud.tianai.captcha.validator.common.model.dto.ImageCaptchaTrack;
import cloud.tianai.captcha.validator.common.model.dto.MatchParam;
import com.example.ShoppingSystem.common.exception.TianaiCaptchaFormatException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TianaiCaptchaServiceImplTest {

    @Test
    void validateCaptchaThrowsFormatExceptionWhenTrackPayloadIsMalformed() {
        ImageCaptchaApplication imageCaptchaApplication = mock(ImageCaptchaApplication.class);
        TianaiCaptchaServiceImpl service = new TianaiCaptchaServiceImpl(imageCaptchaApplication);

        assertThrows(TianaiCaptchaFormatException.class,
                () -> service.validateCaptcha("slider-captcha-id", "{bad-json"));

        verify(imageCaptchaApplication, never()).matching(eq("slider-captcha-id"), any(MatchParam.class));
    }

    @Test
    void validateCaptchaThrowsFormatExceptionWhenPayloadIsBlank() {
        ImageCaptchaApplication imageCaptchaApplication = mock(ImageCaptchaApplication.class);
        TianaiCaptchaServiceImpl service = new TianaiCaptchaServiceImpl(imageCaptchaApplication);

        assertThrows(TianaiCaptchaFormatException.class,
                () -> service.validateCaptcha("slider-captcha-id", " "));
    }

    @Test
    void validateCaptchaUsesPercentageMatchingForRotateNumericPayload() {
        ImageCaptchaApplication imageCaptchaApplication = mock(ImageCaptchaApplication.class);
        when(imageCaptchaApplication.matching(eq("rotate-captcha-id"), any(MatchParam.class)))
                .thenReturn(ApiResponse.ofSuccess());
        TianaiCaptchaServiceImpl service = new TianaiCaptchaServiceImpl(imageCaptchaApplication);

        boolean verified = service.validateCaptcha("rotate-captcha-id", "0.5");

        assertTrue(verified);
        ArgumentCaptor<MatchParam> matchParamCaptor = ArgumentCaptor.forClass(MatchParam.class);
        verify(imageCaptchaApplication).matching(eq("rotate-captcha-id"), matchParamCaptor.capture());
        verify(imageCaptchaApplication, never()).matching("rotate-captcha-id", 0.5f);

        ImageCaptchaTrack track = matchParamCaptor.getValue().getTrack();
        assertEquals(1000, track.getBgImageWidth());
        assertEquals(500, track.getLeft());
        assertEquals(2, track.getTrackList().size());
        assertEquals(0F, track.getTrackList().get(0).getX());
        assertEquals(500F, track.getTrackList().get(1).getX());
        assertEquals(TrackTypeConstant.DOWN, track.getTrackList().get(0).getType());
        assertEquals(TrackTypeConstant.UP, track.getTrackList().get(1).getType());
    }

    @Test
    void validateRotateCaptchaKeepsLegacyDegreeInputCompatible() {
        ImageCaptchaApplication imageCaptchaApplication = mock(ImageCaptchaApplication.class);
        when(imageCaptchaApplication.matching(eq("rotate-captcha-id"), any(MatchParam.class)))
                .thenReturn(ApiResponse.ofSuccess());
        TianaiCaptchaServiceImpl service = new TianaiCaptchaServiceImpl(imageCaptchaApplication);

        boolean verified = service.validateRotateCaptcha("rotate-captcha-id", 180f);

        assertTrue(verified);
        ArgumentCaptor<MatchParam> matchParamCaptor = ArgumentCaptor.forClass(MatchParam.class);
        verify(imageCaptchaApplication).matching(eq("rotate-captcha-id"), matchParamCaptor.capture());
        verify(imageCaptchaApplication, never()).matching("rotate-captcha-id", 0.5f);

        ImageCaptchaTrack track = matchParamCaptor.getValue().getTrack();
        assertEquals(500F, track.getTrackList().get(1).getX());
    }
}
