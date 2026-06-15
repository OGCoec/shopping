package com.example.ShoppingSystem.service.captcha.tianai;

import cloud.tianai.captcha.application.ImageCaptchaApplication;
import cloud.tianai.captcha.application.vo.ImageCaptchaVO;
import cloud.tianai.captcha.common.response.ApiResponse;
import cloud.tianai.captcha.generator.common.model.dto.GenerateParam;
import cloud.tianai.captcha.generator.common.model.dto.ParamKeyEnum;
import cloud.tianai.captcha.validator.common.constant.TrackTypeConstant;
import cloud.tianai.captcha.validator.common.model.dto.ImageCaptchaTrack;
import cloud.tianai.captcha.validator.common.model.dto.MatchParam;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.example.ShoppingSystem.common.exception.TianaiCaptchaFormatException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class TianaiCaptchaEngine {

    private static final int CAPTCHA_ID_NANO_LENGTH = 48;
    private static final float ROTATE_FULL_CIRCLE_DEGREES = 360F;
    private static final int ROTATE_TRACK_BASE_WIDTH = 1000;
    private static final int ROTATE_TRACK_BASE_HEIGHT = 100;

    private final ImageCaptchaApplication imageCaptchaApplication;

    public TianaiCaptchaEngine(ImageCaptchaApplication imageCaptchaApplication) {
        this.imageCaptchaApplication = imageCaptchaApplication;
    }

    public ImageCaptchaVO generate(String captchaType, String existingCaptchaId) {
        ApiResponse<ImageCaptchaVO> response = generateResponse(captchaType, existingCaptchaId);
        return response == null ? null : response.getData();
    }

    public ApiResponse<ImageCaptchaVO> generateResponse(String captchaType, String existingCaptchaId) {
        String normalizedCaptchaType = normalizeCaptchaType(captchaType);
        String captchaId = buildCaptchaId(normalizedCaptchaType, existingCaptchaId);
        GenerateParam generateParam = GenerateParam.builder()
                .type(normalizedCaptchaType)
                .build();
        generateParam.addParam(ParamKeyEnum.ID, captchaId);

        ApiResponse<ImageCaptchaVO> response = imageCaptchaApplication.generateCaptcha(generateParam);
        if (response != null && response.getData() != null) {
            response.getData().setId(captchaId);
        }
        return response;
    }

    public boolean validate(String captchaId, String captchaData) {
        if (StrUtil.isBlank(captchaData)) {
            throw new TianaiCaptchaFormatException("Invalid Tianai captcha payload", captchaId);
        }
        if (NumberUtil.isNumber(captchaData)) {
            return validateRotate(captchaId, Float.parseFloat(captchaData));
        }
        try {
            ImageCaptchaTrack track = parseTrackPayload(captchaId, captchaData);
            ApiResponse<?> response = imageCaptchaApplication.matching(captchaId, new MatchParam(track));
            if (response == null) {
                log.warn("Tianai captcha validation failed, captchaId={}, reason=empty response, track={}",
                        captchaId,
                        summarizeTrack(track));
                return false;
            }
            if (!response.isSuccess()) {
                log.warn("Tianai captcha validation failed, captchaId={}, code={}, msg={}, data={}, track={}",
                        captchaId,
                        response.getCode(),
                        response.getMsg(),
                        response.getData(),
                        summarizeTrack(track));
                return false;
            }
            return true;
        } catch (TianaiCaptchaFormatException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Tianai captcha validation error, captchaId={}, error={}", captchaId, e.getMessage());
            return false;
        }
    }

    public boolean validateRotate(String captchaId, Float rotateValue) {
        if (rotateValue == null || !Float.isFinite(rotateValue)) {
            return false;
        }
        float percentage = normalizeRotatePercentage(rotateValue);
        ApiResponse<?> response = imageCaptchaApplication.matching(captchaId, buildRotateMatchParam(percentage));
        return response != null && response.isSuccess();
    }

    private String normalizeCaptchaType(String captchaType) {
        return StrUtil.blankToDefault(captchaType, "SLIDER").trim().toUpperCase();
    }

    private String buildCaptchaId(String captchaType, String existingCaptchaId) {
        if (StrUtil.isNotBlank(existingCaptchaId) && existingCaptchaId.startsWith(captchaType + "_")) {
            return existingCaptchaId;
        }
        return captchaType + "_" + IdUtil.nanoId(CAPTCHA_ID_NANO_LENGTH);
    }

    private ImageCaptchaTrack parseTrackPayload(String captchaId, String captchaData) {
        final ImageCaptchaTrack track;
        try {
            track = JSONUtil.toBean(captchaData, ImageCaptchaTrack.class);
        } catch (Exception e) {
            throw new TianaiCaptchaFormatException("Invalid Tianai captcha payload", captchaId, e);
        }
        if (track == null || track.getTrackList() == null || track.getTrackList().isEmpty()) {
            throw new TianaiCaptchaFormatException("Invalid Tianai captcha payload", captchaId);
        }
        return track;
    }

    private float normalizeRotatePercentage(Float rotateValue) {
        if (rotateValue == null || !Float.isFinite(rotateValue)) {
            return 0F;
        }
        float normalizedValue = rotateValue;
        if (normalizedValue > 1F) {
            normalizedValue = normalizedValue / ROTATE_FULL_CIRCLE_DEGREES;
        }
        if (normalizedValue < 0F) {
            return 0F;
        }
        if (normalizedValue > 1F) {
            return 1F;
        }
        return normalizedValue;
    }

    private MatchParam buildRotateMatchParam(float percentage) {
        int offsetX = Math.round(percentage * ROTATE_TRACK_BASE_WIDTH);

        ImageCaptchaTrack track = new ImageCaptchaTrack();
        track.setBgImageWidth(ROTATE_TRACK_BASE_WIDTH);
        track.setBgImageHeight(ROTATE_TRACK_BASE_HEIGHT);
        track.setStartTime(0L);
        track.setStopTime(1L);
        track.setLeft(offsetX);
        track.setTop(0);
        track.setTrackList(java.util.List.of(
                new ImageCaptchaTrack.Track(0F, 0F, 0F, TrackTypeConstant.DOWN),
                new ImageCaptchaTrack.Track((float) offsetX, 0F, 1F, TrackTypeConstant.UP)
        ));

        return new MatchParam(track);
    }

    private String summarizeTrack(ImageCaptchaTrack track) {
        if (track == null) {
            return "null";
        }
        int trackPoints = track.getTrackList() == null ? 0 : track.getTrackList().size();
        return "bg="
                + track.getBgImageWidth()
                + "x"
                + track.getBgImageHeight()
                + ",left="
                + track.getLeft()
                + ",top="
                + track.getTop()
                + ",points="
                + trackPoints
                + ",start="
                + track.getStartTime()
                + ",stop="
                + track.getStopTime();
    }
}
