package com.example.ShoppingSystem.service.captcha.tianai.generator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SliderTemplateImageGeneratorTest {

    @TempDir
    Path tempDir;

    @Test
    void generateFillsClosedOutlineIntoMaskAndKeepsActiveCenterTransparent() throws Exception {
        File sourceFile = tempDir.resolve("house.png").toFile();
        BufferedImage source = new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = source.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.drawRect(10, 10, 20, 20);
        } finally {
            graphics.dispose();
        }
        ImageIO.write(source, "png", sourceFile);

        File templateDir = new SliderTemplateImageGenerator().generate(sourceFile, tempDir.resolve("generated").toFile());

        BufferedImage mask = ImageIO.read(new File(templateDir, SliderTemplateImageGenerator.MASK_IMAGE_NAME));
        BufferedImage active = ImageIO.read(new File(templateDir, SliderTemplateImageGenerator.ACTIVE_IMAGE_NAME));
        BufferedImage fixed = ImageIO.read(new File(templateDir, SliderTemplateImageGenerator.FIXED_IMAGE_NAME));

        assertTrue(alpha(mask, 20, 20) > 100);
        assertEquals(0, alpha(mask, 5, 5));
        assertEquals(0, alpha(active, 20, 20));
        assertTrue(alpha(active, 10, 10) > 100);
        assertTrue(alpha(fixed, 20, 20) > 0);
    }

    @Test
    void generateDoesNotThickenSourcePixelsIntoMask() throws Exception {
        File sourceFile = tempDir.resolve("single-pixel.png").toFile();
        BufferedImage source = new BufferedImage(9, 9, BufferedImage.TYPE_INT_ARGB);
        source.setRGB(4, 4, new Color(255, 255, 255, 255).getRGB());
        ImageIO.write(source, "png", sourceFile);

        File templateDir = new SliderTemplateImageGenerator().generate(sourceFile, tempDir.resolve("generated").toFile());

        BufferedImage mask = ImageIO.read(new File(templateDir, SliderTemplateImageGenerator.MASK_IMAGE_NAME));
        assertTrue(alpha(mask, 4, 4) > 100);
        assertEquals(0, alpha(mask, 3, 4));
        assertEquals(0, alpha(mask, 5, 4));
        assertEquals(0, alpha(mask, 4, 3));
        assertEquals(0, alpha(mask, 4, 5));
    }

    private static int alpha(BufferedImage image, int x, int y) {
        return (image.getRGB(x, y) >>> 24) & 0xff;
    }
}
