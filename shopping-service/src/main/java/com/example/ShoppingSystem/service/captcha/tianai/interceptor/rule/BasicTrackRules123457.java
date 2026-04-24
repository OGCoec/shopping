package com.example.ShoppingSystem.service.captcha.tianai.interceptor.rule;

import cloud.tianai.captcha.validator.common.model.dto.ImageCaptchaTrack;

import java.util.List;

/**
 * 基础规则（规则 1~5、7）：
 * 1) 滑动时长
 * 2) 点数范围
 * 3) 起点位置
 * 4) Y 轴全平
 * 5) 相邻点跳变
 * 7) X 轴越界频率
 */
public class BasicTrackRules123457 implements TrackValidationRule {

    private static final int MIN_DURATION_MS = 300;
    private static final int MIN_TRACK_POINTS = 10;
    private static final int MAX_XY_JUMP = 50;
    private static final int MAX_X_OVERFLOW_POINTS = 200;

    @Override
    public boolean validate(ImageCaptchaTrack track, List<ImageCaptchaTrack.Track> trackList) {
        long startSlidingTime = safeLong(track.getStartTime());
        long endSlidingTime = safeLong(track.getStopTime());
        int bgImageWidth = safeInt(track.getBgImageWidth());

        if (startSlidingTime + MIN_DURATION_MS > endSlidingTime) {
            return false;
        }

        int pointSize = trackList.size();
        if (pointSize < MIN_TRACK_POINTS || bgImageWidth <= 0 || pointSize > bgImageWidth * 5) {
            return false;
        }

        ImageCaptchaTrack.Track firstTrack = trackList.get(0);
        if (firstTrack == null) {
            return false;
        }

        float firstX = safeFloat(firstTrack.getX());
        float firstY = safeFloat(firstTrack.getY());
        if (firstX > 10 || firstX < -10 || firstY > 10 || firstY < -10) {
            return false;
        }

        int sameYCount = 0;
        int xOverflowCount = 0;
        for (int i = 1; i < trackList.size(); i++) {
            ImageCaptchaTrack.Track current = trackList.get(i);
            ImageCaptchaTrack.Track prev = trackList.get(i - 1);
            if (current == null || prev == null) {
                return false;
            }

            float x = safeFloat(current.getX());
            float y = safeFloat(current.getY());
            if (firstY == y) {
                sameYCount++;
            }
            if (x >= bgImageWidth) {
                xOverflowCount++;
            }

            float prevX = safeFloat(prev.getX());
            float prevY = safeFloat(prev.getY());
            if ((x - prevX) > MAX_XY_JUMP || (y - prevY) > MAX_XY_JUMP) {
                return false;
            }
        }

        if (sameYCount >= trackList.size() - 1) {
            return false;
        }
        return xOverflowCount <= MAX_X_OVERFLOW_POINTS;
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private float safeFloat(Float value) {
        return value == null ? 0F : value;
    }
}
