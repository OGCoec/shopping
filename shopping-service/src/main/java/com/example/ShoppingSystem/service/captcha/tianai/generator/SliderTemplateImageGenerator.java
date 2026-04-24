package com.example.ShoppingSystem.service.captcha.tianai.generator;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Converts a transparent source icon into Tianai SLIDER template files.
 * mask.png decides the real clipped area, fixed.png renders the hole, and active.png renders the moving outline.
 */
public class SliderTemplateImageGenerator {

    public static final String ACTIVE_IMAGE_NAME = "active.png";
    public static final String FIXED_IMAGE_NAME = "fixed.png";
    public static final String MASK_IMAGE_NAME = "mask.png";

    private static final int SOURCE_ALPHA_THRESHOLD = 24;
    private static final int MASK_ALPHA = 255;
    private static final int FIXED_FILL_ALPHA = 72;
    private static final int FIXED_EDGE_ALPHA = 128;
    private static final int ACTIVE_ALPHA = 235;
    private static final int ACTIVE_SHADOW_ALPHA = 80;

    /**
     * Generates active.png, fixed.png and mask.png under outputRoot/{sanitized-source-name}.
     *
     * @param sourceFile transparent source icon file
     * @param outputRoot generated template root directory
     * @return generated template directory
     */
    public File generate(File sourceFile, File outputRoot) throws IOException {
        BufferedImage source = ImageIO.read(sourceFile);
        if (source == null) {
            throw new IOException("Unreadable slider template source: " + sourceFile.getAbsolutePath());
        }

        File templateDir = new File(outputRoot, stripExtension(sourceFile.getName()));
        if (!templateDir.exists() && !templateDir.mkdirs()) {
            throw new IOException("Failed to create slider template directory: " + templateDir.getAbsolutePath());
        }

        boolean[][] maskPixels = buildMaskPixels(source);
        BufferedImage mask = buildMaskImage(maskPixels);
        BufferedImage fixed = buildFixedImage(maskPixels);
        BufferedImage active = buildActiveImage(source, maskPixels);

        ImageIO.write(mask, "png", new File(templateDir, MASK_IMAGE_NAME));
        ImageIO.write(fixed, "png", new File(templateDir, FIXED_IMAGE_NAME));
        ImageIO.write(active, "png", new File(templateDir, ACTIVE_IMAGE_NAME));
        return templateDir;
    }

    private boolean[][] buildMaskPixels(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        boolean[][] sourcePixels = new boolean[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                sourcePixels[y][x] = alpha(source, x, y) > SOURCE_ALPHA_THRESHOLD;
            }
        }

        // Keep the source stroke width unchanged; dilation made custom templates look too thick.
        boolean[][] barrier = sourcePixels;
        boolean[][] outside = markOutsideTransparentPixels(barrier);
        boolean[][] mask = new boolean[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                mask[y][x] = barrier[y][x] || !outside[y][x];
            }
        }
        return mask;
    }

    private boolean[][] dilate(boolean[][] sourcePixels) {
        int height = sourcePixels.length;
        int width = sourcePixels[0].length;
        boolean[][] result = new boolean[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!sourcePixels[y][x]) {
                    continue;
                }
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx;
                        int ny = y + dy;
                        if (isInside(nx, ny, width, height)) {
                            result[ny][nx] = true;
                        }
                    }
                }
            }
        }
        return result;
    }

    private boolean[][] markOutsideTransparentPixels(boolean[][] barrier) {
        int height = barrier.length;
        int width = barrier[0].length;
        boolean[][] outside = new boolean[height][width];
        Queue<int[]> queue = new ArrayDeque<>();

        for (int x = 0; x < width; x++) {
            enqueueIfOutsideCandidate(x, 0, barrier, outside, queue);
            enqueueIfOutsideCandidate(x, height - 1, barrier, outside, queue);
        }
        for (int y = 0; y < height; y++) {
            enqueueIfOutsideCandidate(0, y, barrier, outside, queue);
            enqueueIfOutsideCandidate(width - 1, y, barrier, outside, queue);
        }

        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int[] point = queue.remove();
            for (int[] direction : directions) {
                int nx = point[0] + direction[0];
                int ny = point[1] + direction[1];
                enqueueIfOutsideCandidate(nx, ny, barrier, outside, queue);
            }
        }
        return outside;
    }

    private void enqueueIfOutsideCandidate(int x,
                                           int y,
                                           boolean[][] barrier,
                                           boolean[][] outside,
                                           Queue<int[]> queue) {
        int height = barrier.length;
        int width = barrier[0].length;
        if (!isInside(x, y, width, height) || barrier[y][x] || outside[y][x]) {
            return;
        }
        outside[y][x] = true;
        queue.add(new int[]{x, y});
    }

    private BufferedImage buildMaskImage(boolean[][] maskPixels) {
        int height = maskPixels.length;
        int width = maskPixels[0].length;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int white = argb(MASK_ALPHA, 255, 255, 255);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (maskPixels[y][x]) {
                    image.setRGB(x, y, white);
                }
            }
        }
        return image;
    }

    private BufferedImage buildFixedImage(boolean[][] maskPixels) {
        int height = maskPixels.length;
        int width = maskPixels[0].length;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!maskPixels[y][x]) {
                    continue;
                }
                int alpha = isEdge(maskPixels, x, y) ? FIXED_EDGE_ALPHA : FIXED_FILL_ALPHA;
                image.setRGB(x, y, argb(alpha, 0, 0, 0));
            }
        }
        return image;
    }

    private BufferedImage buildActiveImage(BufferedImage source, boolean[][] maskPixels) {
        int width = source.getWidth();
        int height = source.getHeight();
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (isEdge(maskPixels, x, y)) {
                    setIfTransparent(image, x + 1, y + 1, argb(ACTIVE_SHADOW_ALPHA, 0, 0, 0));
                    image.setRGB(x, y, argb(ACTIVE_ALPHA, 255, 255, 255));
                }
                if (alpha(source, x, y) > SOURCE_ALPHA_THRESHOLD) {
                    image.setRGB(x, y, argb(ACTIVE_ALPHA, 255, 255, 255));
                }
            }
        }
        return image;
    }

    private boolean isEdge(boolean[][] maskPixels, int x, int y) {
        if (!maskPixels[y][x]) {
            return false;
        }
        int height = maskPixels.length;
        int width = maskPixels[0].length;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0) {
                    continue;
                }
                int nx = x + dx;
                int ny = y + dy;
                if (!isInside(nx, ny, width, height) || !maskPixels[ny][nx]) {
                    return true;
                }
            }
        }
        return false;
    }

    private void setIfTransparent(BufferedImage image, int x, int y, int rgb) {
        if (!isInside(x, y, image.getWidth(), image.getHeight())) {
            return;
        }
        if (alpha(image, x, y) == 0) {
            image.setRGB(x, y, rgb);
        }
    }

    private boolean isInside(int x, int y, int width, int height) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }

    private int alpha(BufferedImage image, int x, int y) {
        return (image.getRGB(x, y) >>> 24) & 0xff;
    }

    private int argb(int alpha, int red, int green, int blue) {
        return ((alpha & 0xff) << 24)
                | ((red & 0xff) << 16)
                | ((green & 0xff) << 8)
                | (blue & 0xff);
    }

    private String stripExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        String name = dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
