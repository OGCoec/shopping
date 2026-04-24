package com.example.ShoppingSystem.service.captcha.tianai.generator;

import cloud.tianai.captcha.common.constant.CaptchaTypeConstant;
import cloud.tianai.captcha.generator.ImageTransform;
import cloud.tianai.captcha.generator.common.model.dto.CaptchaExchange;
import cloud.tianai.captcha.generator.common.model.dto.CustomData;
import cloud.tianai.captcha.generator.common.model.dto.GenerateParam;
import cloud.tianai.captcha.generator.common.model.dto.ImageCaptchaInfo;
import cloud.tianai.captcha.generator.common.model.dto.ImageTransformData;
import cloud.tianai.captcha.generator.impl.StandardConcatImageCaptchaGenerator;
import cloud.tianai.captcha.interceptor.CaptchaInterceptor;
import cloud.tianai.captcha.resource.ImageCaptchaResourceManager;
import cloud.tianai.captcha.resource.common.model.dto.Resource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.concurrent.ThreadLocalRandom;

import static cloud.tianai.captcha.generator.common.util.CaptchaImageUtils.concatImage;
import static cloud.tianai.captcha.generator.common.util.CaptchaImageUtils.splitImage;

/**
 * Concat captcha generator with strict split range and random moving layer.
 * Rules:
 * 1) split Y in [0.3H, 0.7H]
 * 2) exactly one layer moves (TOP/BOTTOM), selected randomly per generation
 */
public class StrictConcatImageCaptchaGenerator extends StandardConcatImageCaptchaGenerator {

    public StrictConcatImageCaptchaGenerator(ImageCaptchaResourceManager imageCaptchaResourceManager) {
        super(imageCaptchaResourceManager);
    }

    public StrictConcatImageCaptchaGenerator(ImageCaptchaResourceManager imageCaptchaResourceManager,
                                             ImageTransform imageTransform) {
        super(imageCaptchaResourceManager, imageTransform);
    }

    public StrictConcatImageCaptchaGenerator(ImageCaptchaResourceManager imageCaptchaResourceManager,
                                             ImageTransform imageTransform,
                                             CaptchaInterceptor interceptor) {
        super(imageCaptchaResourceManager, imageTransform, interceptor);
    }

    private enum MovingLayer {
        TOP,
        BOTTOM
    }

    private static class StrictData {
        int x;
        int y;
        MovingLayer movingLayer;
        BufferedImage topLayer;
        BufferedImage bottomLayer;
    }

    @Override
    public void doGenerateCaptchaImage(CaptchaExchange captchaExchange) {
        // 先取出本次生成参数，并按验证码类型/标签随机选一张背景图资源。
        GenerateParam param = captchaExchange.getParam();
        Resource resourceImage = requiredRandomGetResource(param.getType(), param.getBackgroundImageTag());
        BufferedImage bgImage = getResourceImage(resourceImage);

        // Y 轴切分线强制落在图片高度的 30%~70% 区间，避免切得过高或过低导致题目太简单。
        int height = bgImage.getHeight();
        int minY = Math.max(1, (int) Math.ceil(height * 0.3d));
        int maxY = Math.max(minY, (int) Math.floor(height * 0.7d));
        int randomY = randomInclusive(minY, maxY);

        // X 轴错位点也做一个相对保守的范围控制：
        // 左边至少留出 1/8 宽度，右边最多走到整图宽度减去 1/5，避免拼接块过于贴边。
        int spacingX = bgImage.getWidth() / 8;
        int maxX = bgImage.getWidth() - bgImage.getWidth() / 5;
        int randomX = randomInclusive(spacingX, maxX);

        // StrictData 是当前题目的中间态，后面包装前端数据时会继续复用。
        StrictData data = new StrictData();
        data.x = randomX;
        data.y = randomY;
        // 本生成器要求“上下两层只随机移动其中一层”，因此这里先随机决定是 TOP 还是 BOTTOM。
        data.movingLayer = ThreadLocalRandom.current().nextBoolean() ? MovingLayer.TOP : MovingLayer.BOTTOM;

        // 先按 Y 轴把原图切成上下两层，后续只对其中一层做水平错位。
        BufferedImage[] bgImageSplit = splitImage(randomY, true, bgImage);
        BufferedImage topLayer = bgImageSplit[0];
        BufferedImage bottomLayer = bgImageSplit[1];
        if (data.movingLayer == MovingLayer.TOP) {
            // 如果移动的是上层，就把上层再按 X 轴切成左右两块，然后交换左右顺序制造“错位”效果。
            BufferedImage[] topLayerSplit = splitImage(randomX, false, topLayer);
            topLayer = concatImage(
                    true,
                    topLayerSplit[0].getWidth() + topLayerSplit[1].getWidth(),
                    topLayerSplit[0].getHeight(),
                    topLayerSplit[1],
                    topLayerSplit[0]
            );
        } else {
            // 否则就对下层执行同样的左右交换，上层保持不动。
            BufferedImage[] bottomLayerSplit = splitImage(randomX, false, bottomLayer);
            bottomLayer = concatImage(
                    true,
                    bottomLayerSplit[0].getWidth() + bottomLayerSplit[1].getWidth(),
                    bottomLayerSplit[0].getHeight(),
                    bottomLayerSplit[1],
                    bottomLayerSplit[0]
            );
        }
        // 最后把处理后的上下两层重新垂直拼回一张完整背景图，作为前端看到的错位题面。
        bgImage = concatImage(
                false,
                bgImage.getWidth(),
                topLayer.getHeight() + bottomLayer.getHeight(),
                topLayer,
                bottomLayer
        );
        // 把生成过程中用到的层信息保存起来，后面包装响应时还要告诉前端如何渲染上下层。
        data.topLayer = topLayer;
        data.bottomLayer = bottomLayer;

        // 生成阶段的中间数据、最终背景图和原始资源图都塞回 exchange，供下一个包装阶段继续使用。
        captchaExchange.setTransferData(data);
        captchaExchange.setBackgroundImage(bgImage);
        captchaExchange.setResourceImage(resourceImage);
    }

    @Override
    public ImageCaptchaInfo doWrapImageCaptchaInfo(CaptchaExchange captchaExchange) {
        // 先取出生成阶段放进 exchange 的各种数据，准备包装成 Tianai 对外的 ImageCaptchaInfo。
        GenerateParam param = captchaExchange.getParam();
        BufferedImage bgImage = captchaExchange.getBackgroundImage();
        Resource resourceImage = captchaExchange.getResourceImage();
        CustomData customData = captchaExchange.getCustomData();
        // 交给 ImageTransform 做最后一层输出转换，例如转成 base64 URL 或其它前端可用格式。
        ImageTransformData transform = getImageTransform().transform(param, bgImage, resourceImage, customData);
        StrictData data = (StrictData) captchaExchange.getTransferData();

        // CONCAT 类型的关键校验值仍然是 X 轴错位位置，所以这里把 data.x 作为正确答案写入 ImageCaptchaInfo。
        ImageCaptchaInfo imageCaptchaInfo = ImageCaptchaInfo.of(
                transform.getBackgroundImageUrl(),
                null,
                resourceImage.getTag(),
                null,
                bgImage.getWidth(),
                bgImage.getHeight(),
                null,
                null,
                data.x,
                CaptchaTypeConstant.CONCAT
        );
        // viewData 是前端渲染专用附加信息：
        // randomY/movingLayer/topHeight/bottomHeight 用来还原切层关系，
        // topImage/bottomImage 用来分别展示上下层图像。
        customData.putViewData("randomY", data.y);
        customData.putViewData("movingLayer", data.movingLayer.name());
        customData.putViewData("topHeight", data.topLayer.getHeight());
        customData.putViewData("bottomHeight", data.bottomLayer.getHeight());
        customData.putViewData("topImage", toDataUrl(data.topLayer));
        customData.putViewData("bottomImage", toDataUrl(data.bottomLayer));
        // CONCAT 相比默认滑块稍微放宽一点误差，避免拼接题因为层边界视觉误差过大而过难。
        imageCaptchaInfo.setTolerant(0.05F);
        return imageCaptchaInfo;
    }

    private int randomInclusive(int min, int max) {
        // 当上界小于等于下界时直接返回下界，避免 ThreadLocalRandom 传入非法区间。
        if (max <= min) {
            return min;
        }
        // nextInt(min, max + 1) 才是闭区间 [min, max] 的随机数。
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    private String toDataUrl(BufferedImage image) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            // 前端当前消费的是 data URL，所以这里直接把 BufferedImage 编码成 base64 png。
            ImageIO.write(image, "png", outputStream);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (IOException e) {
            // 这里是纯内存编码，正常不该失败；一旦失败直接抛出非法状态，交给上层终止本次生成。
            throw new IllegalStateException("Failed to encode concat captcha layer", e);
        }
    }
}
