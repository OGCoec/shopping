package com.example.ShoppingSystem.service.captcha.tianai.generator;

import cloud.tianai.captcha.generator.ImageTransform;
import cloud.tianai.captcha.generator.common.FontWrapper;
import cloud.tianai.captcha.generator.common.model.dto.ClickImageCheckDefinition;
import cloud.tianai.captcha.generator.common.model.dto.GenerateParam;
import cloud.tianai.captcha.generator.common.util.CaptchaImageUtils;
import cloud.tianai.captcha.generator.impl.StandardWordClickImageCaptchaGenerator;
import cloud.tianai.captcha.interceptor.CaptchaInterceptor;
import cloud.tianai.captcha.resource.ImageCaptchaResourceManager;
import cloud.tianai.captcha.resource.common.model.dto.Resource;

import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.util.concurrent.ThreadLocalRandom;

/**
 * WORD_IMAGE_CLICK generator using font size = background width / 12.
 */
public class RatioWordClickImageCaptchaGenerator extends StandardWordClickImageCaptchaGenerator {

    private static final float FONT_SIZE_WIDTH_RATIO = 12f;

    public RatioWordClickImageCaptchaGenerator(ImageCaptchaResourceManager imageCaptchaResourceManager,
                                               ImageTransform imageTransform,
                                               CaptchaInterceptor interceptor) {
        super(imageCaptchaResourceManager, imageTransform, interceptor);
    }

    @Override
    public ClickImageCheckDefinition.ImgWrapper getClickImg(GenerateParam param,
                                                            Resource tip,
                                                            Color randomColor,
                                                            BufferedImage bgImage) {
        Color wordColor = randomColor;
        if (wordColor == null) {
            wordColor = CaptchaImageUtils.getRandomColor(ThreadLocalRandom.current());
        }

        int randomDeg = randomInt(0, 85);
        FontWrapper fontWrapper = randomFont(param);
        Font font = fontWrapper.getFont(bgImage.getWidth() / FONT_SIZE_WIDTH_RATIO);
        float currentFontTopCoef = fontWrapper.getFontTopCoef(font);
        int clickImgWidth = (int) (font.getSize() * 1.428571428571429);

        BufferedImage fontImage = CaptchaImageUtils.drawWordImg(
                wordColor,
                tip.getData(),
                font,
                currentFontTopCoef,
                clickImgWidth,
                clickImgWidth,
                randomDeg
        );
        return new ClickImageCheckDefinition.ImgWrapper(fontImage, tip, wordColor);
    }
}
