package com.example.ShoppingSystem.service.captcha.tianai.interceptor;

import cloud.tianai.captcha.common.AnyMap;
import cloud.tianai.captcha.common.constant.CaptchaTypeConstant;
import cloud.tianai.captcha.common.response.ApiResponse;
import cloud.tianai.captcha.common.response.CodeDefinition;
import cloud.tianai.captcha.interceptor.CaptchaInterceptor;
import cloud.tianai.captcha.interceptor.Context;
import cloud.tianai.captcha.validator.common.model.dto.ImageCaptchaTrack;
import cloud.tianai.captcha.validator.common.model.dto.MatchParam;
import com.example.ShoppingSystem.service.captcha.tianai.interceptor.rule.AdvancedTrackRule6;
import com.example.ShoppingSystem.service.captcha.tianai.interceptor.rule.BasicTrackRules123457;
import com.example.ShoppingSystem.service.captcha.tianai.interceptor.rule.TrackValidationRule;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 仅对 SLIDER / CONCAT 启用轨迹行为校验。
 * <p>
 * 具体规则实现下沉到 rule 包，拦截器只负责流程编排与题型选择。
 */
public class SelectiveTrackCaptchaInterceptor implements CaptchaInterceptor {

    private static final CodeDefinition DEFINITION = new CodeDefinition(50001, "basic track check fail");

    private static final Set<String> TRACK_CHECK_ENABLED_TYPES = Set.of(
            CaptchaTypeConstant.SLIDER,
            CaptchaTypeConstant.CONCAT
    );

    private final List<TrackValidationRule> trackRules = List.of(
            new BasicTrackRules123457(),
            new AdvancedTrackRule6()
    );

    @Override
    public String getName() {
        return "selective_track_check";
    }

    @Override
    public ApiResponse<?> afterValid(Context context,
                                     String type,
                                     MatchParam matchParam,
                                     AnyMap validData,
                                     ApiResponse<?> basicValid) {
        if (basicValid == null || !basicValid.isSuccess()) {
            return context.getGroup().afterValid(context, type, matchParam, validData, basicValid);
        }

        String normalizedType = normalizeType(type);
        if (!TRACK_CHECK_ENABLED_TYPES.contains(normalizedType)) {
            return ApiResponse.ofSuccess();
        }

        if (matchParam == null || matchParam.getTrack() == null) {
            return reject(context);
        }

        ImageCaptchaTrack track = matchParam.getTrack();
        List<ImageCaptchaTrack.Track> trackList = track.getTrackList();
        if (trackList == null || trackList.isEmpty()) {
            return reject(context);
        }

        for (TrackValidationRule rule : trackRules) {
            if (!rule.validate(track, trackList)) {
                return reject(context);
            }
        }
        return ApiResponse.ofSuccess();
    }

    private String normalizeType(String type) {
        if (type == null) {
            return "";
        }
        return type.trim().toUpperCase(Locale.ROOT);
    }

    private ApiResponse<?> reject(Context context) {
        context.end();
        return ApiResponse.ofMessage(DEFINITION);
    }
}
