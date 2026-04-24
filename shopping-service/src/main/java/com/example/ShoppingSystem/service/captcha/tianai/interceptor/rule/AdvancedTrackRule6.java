package com.example.ShoppingSystem.service.captcha.tianai.interceptor.rule;

import cloud.tianai.captcha.validator.common.model.dto.ImageCaptchaTrack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 新规则 6：
 * 1) 48Hz 重采样后，起始速度与终止速度必须接近 0（或很小阈值）
 * 2) 基于三阶差分的 jerk（跃度）方差，过于平滑判为脚本
 * 3) 对 Y 轴做频域分析，纯白噪声特征且无 8-12Hz 生理峰值时判为脚本
 */
public class AdvancedTrackRule6 implements TrackValidationRule {

    private static final double SAMPLE_RATE_HZ = 48.0D;
    private static final int START_END_WINDOW_FRAMES = 4;
    private static final double START_END_SPEED_EPSILON = 300.0D;

    private static final int MIN_RULE6_SAMPLES = 18;
    private static final int MIN_FFT_SAMPLES = 32;
    private static final double JERK_SMOOTH_VARIANCE_THRESHOLD = 0.003D;
    private static final double Y_SPECTRAL_FLATNESS_THRESHOLD = 0.92D;
    private static final double Y_TREMOR_MIN_RATIO = 0.02D;
    private static final double Y_ACTIVITY_MIN_VARIANCE = 0.12D;

    @Override
    public boolean validate(ImageCaptchaTrack track, List<ImageCaptchaTrack.Track> trackList) {
        List<Point> samples = resampleToFixedRate(trackList, SAMPLE_RATE_HZ);
        if (samples.size() < MIN_RULE6_SAMPLES) {
            return true;
        }

        List<Double> speeds = computeSpeedSeries(samples, SAMPLE_RATE_HZ);
        if (speeds.isEmpty()) {
            return true;
        }

        int window = Math.max(1, Math.min(START_END_WINDOW_FRAMES, speeds.size() / 4));
        double headAvg = average(speeds.subList(0, window));
        double tailAvg = average(speeds.subList(speeds.size() - window, speeds.size()));
        if (headAvg > START_END_SPEED_EPSILON || tailAvg > START_END_SPEED_EPSILON) {
            return false;
        }

        if (isJerkTooSmooth(samples)) {
            return false;
        }
        return !hasSuspiciousWhiteNoiseY(samples);
    }

    private boolean isJerkTooSmooth(List<Point> samples) {
        List<Double> jx = thirdOrderDifference(extractAxis(samples, true));
        List<Double> jy = thirdOrderDifference(extractAxis(samples, false));
        if (jx.isEmpty() || jy.isEmpty()) {
            return false;
        }

        int size = Math.min(jx.size(), jy.size());
        List<Double> jerkMagnitude = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            jerkMagnitude.add(Math.hypot(jx.get(i), jy.get(i)));
        }

        double maxJerk = max(jerkMagnitude);
        if (maxJerk <= 0) {
            return true;
        }

        List<Double> normalized = new ArrayList<>(jerkMagnitude.size());
        for (Double value : jerkMagnitude) {
            normalized.add(value / maxJerk);
        }

        double variance = variance(normalized);
        return variance < JERK_SMOOTH_VARIANCE_THRESHOLD;
    }

    private boolean hasSuspiciousWhiteNoiseY(List<Point> samples) {
        if (samples.size() < MIN_FFT_SAMPLES) {
            return false;
        }

        double[] y = new double[samples.size()];
        for (int i = 0; i < samples.size(); i++) {
            y[i] = samples.get(i).y;
        }

        removeMeanInPlace(y);
        if (variance(y) < Y_ACTIVITY_MIN_VARIANCE) {
            return false;
        }

        int n = y.length;
        int maxK = n / 2;
        double totalPower = 0D;
        double tremorPower = 0D;
        double logSum = 0D;
        int validBins = 0;

        for (int k = 1; k <= maxK; k++) {
            double re = 0D;
            double im = 0D;
            for (int t = 0; t < n; t++) {
                double angle = -2D * Math.PI * k * t / n;
                re += y[t] * Math.cos(angle);
                im += y[t] * Math.sin(angle);
            }

            double power = re * re + im * im;
            if (power <= 0) {
                continue;
            }

            totalPower += power;
            logSum += Math.log(power);
            validBins++;

            double freq = k * SAMPLE_RATE_HZ / n;
            if (freq >= 8D && freq <= 12D) {
                tremorPower += power;
            }
        }

        if (totalPower <= 0D || validBins == 0) {
            return false;
        }

        double arithmeticMean = totalPower / validBins;
        double geometricMean = Math.exp(logSum / validBins);
        double spectralFlatness = geometricMean / arithmeticMean;
        double tremorRatio = tremorPower / totalPower;

        return spectralFlatness > Y_SPECTRAL_FLATNESS_THRESHOLD && tremorRatio < Y_TREMOR_MIN_RATIO;
    }

    private List<Point> resampleToFixedRate(List<ImageCaptchaTrack.Track> trackList, double sampleRateHz) {
        if (trackList == null || trackList.size() < 2) {
            return Collections.emptyList();
        }

        List<TrackPoint> sorted = new ArrayList<>(trackList.size());
        double fallbackT = 0D;
        for (ImageCaptchaTrack.Track t : trackList) {
            if (t == null) {
                continue;
            }
            double time = t.getT() == null ? fallbackT : t.getT();
            if (!sorted.isEmpty() && time <= sorted.get(sorted.size() - 1).t) {
                time = sorted.get(sorted.size() - 1).t + 1D;
            }
            sorted.add(new TrackPoint(safeFloat(t.getX()), safeFloat(t.getY()), time));
            fallbackT = time + 1D;
        }

        if (sorted.size() < 2) {
            return Collections.emptyList();
        }

        double startT = sorted.get(0).t;
        double endT = sorted.get(sorted.size() - 1).t;
        if (endT <= startT) {
            return Collections.emptyList();
        }

        double intervalMs = 1000D / sampleRateHz;
        List<Point> result = new ArrayList<>();
        int cursor = 0;
        for (double t = startT; t <= endT; t += intervalMs) {
            while (cursor + 1 < sorted.size() && sorted.get(cursor + 1).t < t) {
                cursor++;
            }
            if (cursor + 1 >= sorted.size()) {
                break;
            }

            TrackPoint p0 = sorted.get(cursor);
            TrackPoint p1 = sorted.get(cursor + 1);
            double dt = p1.t - p0.t;
            if (dt <= 0D) {
                continue;
            }

            double ratio = (t - p0.t) / dt;
            ratio = Math.max(0D, Math.min(1D, ratio));
            double x = p0.x + (p1.x - p0.x) * ratio;
            double y = p0.y + (p1.y - p0.y) * ratio;
            result.add(new Point(x, y));
        }
        return result;
    }

    private List<Double> computeSpeedSeries(List<Point> points, double sampleRateHz) {
        if (points.size() < 2) {
            return Collections.emptyList();
        }

        double dtSeconds = 1D / sampleRateHz;
        List<Double> speed = new ArrayList<>(points.size() - 1);
        for (int i = 1; i < points.size(); i++) {
            Point current = points.get(i);
            Point prev = points.get(i - 1);
            double distance = Math.hypot(current.x - prev.x, current.y - prev.y);
            speed.add(distance / dtSeconds);
        }
        return speed;
    }

    private List<Double> extractAxis(List<Point> samples, boolean xAxis) {
        List<Double> axis = new ArrayList<>(samples.size());
        for (Point sample : samples) {
            axis.add(xAxis ? sample.x : sample.y);
        }
        return axis;
    }

    private List<Double> thirdOrderDifference(List<Double> series) {
        if (series.size() < 4) {
            return Collections.emptyList();
        }
        List<Double> first = difference(series);
        List<Double> second = difference(first);
        return difference(second);
    }

    private List<Double> difference(List<Double> series) {
        if (series.size() < 2) {
            return Collections.emptyList();
        }

        List<Double> diff = new ArrayList<>(series.size() - 1);
        for (int i = 1; i < series.size(); i++) {
            diff.add(series.get(i) - series.get(i - 1));
        }
        return diff;
    }

    private void removeMeanInPlace(double[] values) {
        if (values.length == 0) {
            return;
        }

        double sum = 0D;
        for (double value : values) {
            sum += value;
        }
        double mean = sum / values.length;
        for (int i = 0; i < values.length; i++) {
            values[i] = values[i] - mean;
        }
    }

    private double average(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return 0D;
        }

        double sum = 0D;
        for (Double value : values) {
            sum += value;
        }
        return sum / values.size();
    }

    private double variance(List<Double> values) {
        if (values == null || values.size() < 2) {
            return 0D;
        }

        double mean = average(values);
        double sum = 0D;
        for (Double value : values) {
            double d = value - mean;
            sum += d * d;
        }
        return sum / values.size();
    }

    private double variance(double[] values) {
        if (values == null || values.length < 2) {
            return 0D;
        }

        double sum = 0D;
        for (double value : values) {
            sum += value;
        }
        double mean = sum / values.length;

        double square = 0D;
        for (double value : values) {
            double d = value - mean;
            square += d * d;
        }
        return square / values.length;
    }

    private double max(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return 0D;
        }

        double max = values.get(0);
        for (Double value : values) {
            if (value > max) {
                max = value;
            }
        }
        return max;
    }

    private float safeFloat(Float value) {
        return value == null ? 0F : value;
    }

    private static final class Point {
        private final double x;
        private final double y;

        private Point(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }

    private static final class TrackPoint {
        private final double x;
        private final double y;
        private final double t;

        private TrackPoint(double x, double y, double t) {
            this.x = x;
            this.y = y;
            this.t = t;
        }
    }
}
