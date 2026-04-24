package com.example.ShoppingSystem.service.captcha.tianai.impl;

import cloud.tianai.captcha.application.ImageCaptchaApplication;
import cloud.tianai.captcha.application.vo.ImageCaptchaVO;
import cloud.tianai.captcha.common.response.ApiResponse;
import cloud.tianai.captcha.generator.common.model.dto.GenerateParam;
import cloud.tianai.captcha.generator.common.model.dto.ParamKeyEnum;
import cloud.tianai.captcha.validator.common.constant.TrackTypeConstant;
import cloud.tianai.captcha.validator.common.model.dto.ImageCaptchaTrack;
import cloud.tianai.captcha.validator.common.model.dto.MatchParam;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.example.ShoppingSystem.common.exception.TianaiCaptchaFormatException;
import com.example.ShoppingSystem.service.captcha.tianai.TianaiCaptchaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TianaiCaptchaServiceImpl implements TianaiCaptchaService {

    private static final Logger log = LoggerFactory.getLogger(TianaiCaptchaServiceImpl.class);
    private static final int CAPTCHA_ID_NANO_LENGTH = 48;
    private static final float ROTATE_FULL_CIRCLE_DEGREES = 360F;
    private static final int ROTATE_TRACK_BASE_WIDTH = 1000;
    private static final int ROTATE_TRACK_BASE_HEIGHT = 100;

    private final ImageCaptchaApplication imageCaptchaApplication;

    public TianaiCaptchaServiceImpl(ImageCaptchaApplication imageCaptchaApplication) {
        this.imageCaptchaApplication = imageCaptchaApplication;
    }

    @Override
    public ImageCaptchaVO generateRotateCaptcha() {
        return generateRotateCaptcha(null);
    }

    @Override
    public ImageCaptchaVO generateRotateCaptcha(String existingCaptchaId) {
        return generateByType("ROTATE", existingCaptchaId);
    }

    @Override
    public ImageCaptchaVO generateSliderCaptcha() {
        return generateSliderCaptcha(null);
    }

    @Override
    public ImageCaptchaVO generateSliderCaptcha(String existingCaptchaId) {
        return generateByType("SLIDER", existingCaptchaId);
    }

    @Override
    public ImageCaptchaVO generateConcatCaptcha() {
        return generateConcatCaptcha(null);
    }

    @Override
    public ImageCaptchaVO generateConcatCaptcha(String existingCaptchaId) {
        return generateByType("CONCAT", existingCaptchaId);
    }

    @Override
    public ImageCaptchaVO generateWordClickCaptcha() {
        return generateWordClickCaptcha(null);
    }

    @Override
    public ImageCaptchaVO generateWordClickCaptcha(String existingCaptchaId) {
        return generateByType("WORD_IMAGE_CLICK", existingCaptchaId);
    }

    @Override
    public ApiResponse<ImageCaptchaVO> generateCaptcha(String subType) {
        return generateCaptcha(subType, null);
    }

    @Override
    public ApiResponse<ImageCaptchaVO> generateCaptcha(String subType, String existingCaptchaId) {
        return generateResponseByType(subType, existingCaptchaId);
    }

    @Override
    public boolean validateCaptcha(String captchaId, String captchaData) {
        if (captchaData == null || captchaData.trim().isEmpty()) {
            throw new TianaiCaptchaFormatException("Invalid Tianai captcha payload", captchaId);
        }
        if (cn.hutool.core.util.NumberUtil.isNumber(captchaData)) {
            float rotateValue = Float.parseFloat(captchaData);
            boolean verified = validateRotateCaptcha(captchaId, rotateValue);
            if (!verified) {
                log.warn("天爱旋转验证码校验失败，captchaId={}, 旋转值={}",
                        captchaId,
                        rotateValue);
            }
            return verified;
        }
        try {
            ImageCaptchaTrack track = parseTrackPayload(captchaId, captchaData);
            ApiResponse<?> response = imageCaptchaApplication.matching(captchaId, new MatchParam(track));
            if (response == null) {
                log.warn("天爱验证码校验失败，captchaId={}, 原因=响应为空，轨迹={}",
                        captchaId,
                        summarizeTrack(track));
                return false;
            }
            if (!response.isSuccess()) {
                log.warn("天爱验证码校验失败，captchaId={}, code={}, msg={}, data={}, 轨迹={}, 失败细节={}, 响应={}",
                        captchaId,
                        response.getCode(),
                        response.getMsg(),
                        response.getData(),
                        summarizeTrack(track),
                        buildTrackFailureDetail(track, response),
                        response);
                return false;
            }
            return true;
        } catch (TianaiCaptchaFormatException e) {
            throw e;
        } catch (Exception e) {
            log.warn("天爱验证码校验异常，captchaId={}, 错误={}", captchaId, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean validateRotateCaptcha(String captchaId, Float rotateValue) {
        if (rotateValue == null || !Float.isFinite(rotateValue)) {
            return false;
        }
        float percentage = normalizeRotatePercentage(rotateValue);
        ApiResponse<?> response = imageCaptchaApplication.matching(captchaId, buildRotateMatchParam(percentage));
        return response != null && response.isSuccess();
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

    private String summarizeTrack(ImageCaptchaTrack track) {
        if (track == null) {
            return "null";
        }
        int trackPoints = track.getTrackList() == null ? 0 : track.getTrackList().size();
        return "背景="
                + track.getBgImageWidth()
                + "x"
                + track.getBgImageHeight()
                + ",left="
                + track.getLeft()
                + ",top="
                + track.getTop()
                + ",轨迹点数="
                + trackPoints
                + ",开始时间="
                + track.getStartTime()
                + ",结束时间="
                + track.getStopTime();
    }

    private String buildTrackFailureDetail(ImageCaptchaTrack track, ApiResponse<?> response) {
        if (track == null) {
            return "轨迹为空";
        }
        if (track.getTrackList() == null || track.getTrackList().isEmpty()) {
            return "轨迹点列表为空";
        }

        java.util.List<String> reasons = new java.util.ArrayList<>();
        java.util.List<ImageCaptchaTrack.Track> trackList = track.getTrackList();

        long start = track.getStartTime();
        long stop = track.getStopTime();
        int points = trackList.size();
        Integer bgWidth = track.getBgImageWidth();
        int safeBgWidth = bgWidth == null ? 0 : bgWidth;

        if (start + 300 > stop) {
            reasons.add("滑动时间不足300ms");
        }

        if (points < 10) {
            reasons.add("轨迹点数小于10");
        }
        if (safeBgWidth > 0 && points > safeBgWidth * 5) {
            reasons.add("轨迹点数大于背景宽度5倍");
        }

        ImageCaptchaTrack.Track first = trackList.get(0);
        if (first.getX() > 10 || first.getX() < -10 || first.getY() > 10 || first.getY() < -10) {
            reasons.add("首点偏移过大(x/y起点不在[-10,10])");
        }

        int sameYCount = 0;
        int xOverflowCount = 0;
        boolean jumpTooLarge = false;
        for (int i = 1; i < trackList.size(); i++) {
            ImageCaptchaTrack.Track current = trackList.get(i);
            ImageCaptchaTrack.Track prev = trackList.get(i - 1);

            if (first.getY() == current.getY()) {
                sameYCount++;
            }
            if (safeBgWidth > 0 && current.getX() >= safeBgWidth) {
                xOverflowCount++;
            }
            if ((current.getX() - prev.getX()) > 50 || (current.getY() - prev.getY()) > 50) {
                jumpTooLarge = true;
            }
        }

        if (sameYCount == trackList.size() - 1) {
            reasons.add("Y轴几乎无波动(疑似机器轨迹)");
        }
        if (xOverflowCount > 200) {
            reasons.add("x越界点过多(>200)");
        }
        if (jumpTooLarge) {
            reasons.add("相邻点跳变过大(dx>50或dy>50)");
        }

        if (reasons.isEmpty()) {
            Integer code = response == null ? null : response.getCode();
            String msg = response == null ? null : response.getMsg();
            if (Integer.valueOf(50001).equals(code)
                    || StrUtil.containsIgnoreCase(StrUtil.nullToEmpty(msg), "track")) {
                return "命中自定义规则6(起止速度/跃度/Y轴频谱)判定";
            }
            return "未识别到明确失败规则";
        }

        return String.join(" | ", reasons);
    }
    private ApiResponse<ImageCaptchaVO> generateResponseByType(String captchaType, String existingCaptchaId) {
        String normalizedCaptchaType = StrUtil.blankToDefault(captchaType, "SLIDER").trim().toUpperCase();
        String captchaId = buildCaptchaId(normalizedCaptchaType, existingCaptchaId);
        GenerateParam generateParam = buildGenerateParam(normalizedCaptchaType, captchaId);
        ApiResponse<ImageCaptchaVO> response = imageCaptchaApplication.generateCaptcha(generateParam);
        if (response != null && response.getData() != null) {
            response.getData().setId(captchaId);
        }
        return response;
    }

    private GenerateParam buildGenerateParam(String captchaType, String captchaId) {
        GenerateParam generateParam = GenerateParam.builder()
                .type(captchaType)
                .build();
        generateParam.addParam(ParamKeyEnum.ID, captchaId);
        return generateParam;
    }

    private String buildCaptchaId(String captchaType, String existingCaptchaId) {
        if (StrUtil.isNotBlank(existingCaptchaId)
                && existingCaptchaId.startsWith(captchaType + "_")) {
            return existingCaptchaId;
        }
        return captchaType + "_" + IdUtil.nanoId(CAPTCHA_ID_NANO_LENGTH);
    }

    private ImageCaptchaVO generateByType(String captchaType, String existingCaptchaId) {
        ApiResponse<ImageCaptchaVO> response = generateResponseByType(captchaType, existingCaptchaId);
        return response == null ? null : response.getData();
    }
}

