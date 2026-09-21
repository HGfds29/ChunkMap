package com.geek.chunkmap.render;

/**
 * 方向性坡度光照 + 绝对高度明暗。
 * shadeByHeight = false 时完全禁用所有明暗效果。
 */
public class ChunkTopDownRenderer {

    private final int resolution;
    private final boolean shadeByHeight;

    private static final int   SAMPLE_RADIUS   = 1;
    private static final float SLOPE_STRENGTH  = 0.35f;
    private static final float MIN_SHADE       = 0.48f;
    private static final float MAX_SHADE       = 1.15f;

    public ChunkTopDownRenderer(int resolution, boolean shadeByHeight) {
        this.resolution = resolution;
        this.shadeByHeight = shadeByHeight;
    }

    public int getResolution() { return resolution; }
    public boolean isShadeByHeight() { return shadeByHeight; }

    public int[] renderChunk(ChunkSnapshot snapshot) {
        int res = resolution;
        int scale = res / 16;
        int[] pixels = new int[res * res];
        int[] colors = snapshot.colors();
        int[] ys = snapshot.topYs();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int idx = z * 16 + x;
                int baseRgb = colors[idx];
                int y = ys[idx];

                if (baseRgb == 0 || y == Integer.MIN_VALUE) {
                    fillBlock(pixels, x, z, scale, baseRgb);
                    continue;
                }

                int rgb = baseRgb;

                if (shadeByHeight) {
                    int yE = sampleY(ys, x + SAMPLE_RADIUS, z, y);
                    int yW = sampleY(ys, x - SAMPLE_RADIUS, z, y);
                    int yN = sampleY(ys, x, z - SAMPLE_RADIUS, y);
                    int yS = sampleY(ys, x, z + SAMPLE_RADIUS, y);

                    float gradX = (yE - yW) / (2f * SAMPLE_RADIUS);
                    float gradZ = (yS - yN) / (2f * SAMPLE_RADIUS);

                    float lit = (gradX + gradZ) * SLOPE_STRENGTH;
                    float shade = clamp(1.0f + lit, MIN_SHADE, MAX_SHADE);

                    rgb = applyFactor(rgb, shade);
                    rgb = applyHeightShade(rgb, y);
                }

                fillBlock(pixels, x, z, scale, rgb);
            }
        }
        return pixels;
    }

    private static int sampleY(int[] ys, int x, int z, int fallback) {
        if (x < 0 || x >= 16 || z < 0 || z >= 16) return fallback;
        int v = ys[z * 16 + x];
        return v == Integer.MIN_VALUE ? fallback : v;
    }

    private void fillBlock(int[] pixels, int bx, int bz, int scale, int rgb) {
        int res = resolution;
        int startX = bx * scale;
        int startZ = bz * scale;
        for (int dz = 0; dz < scale; dz++) {
            int rowStart = (startZ + dz) * res + startX;
            for (int dx = 0; dx < scale; dx++) {
                pixels[rowStart + dx] = rgb;
            }
        }
    }

    private int applyHeightShade(int rgb, int y) {
        float factor;
        if (y <= 40) {
            factor = 0.68f;
        } else if (y <= 120) {
            factor = 0.68f + 0.32f * (y - 40) / 80f;
        } else if (y <= 200) {
            factor = 1.00f + 0.10f * (y - 120) / 80f;
        } else {
            factor = 1.10f + 0.05f * Math.min(1f, (y - 200) / 120f);
        }
        return applyFactor(rgb, factor);
    }

    private static int applyFactor(int rgb, float factor) {
        int a = (rgb >>> 24) & 0xFF;
        if (a == 0) return rgb;
        int r = (int) (((rgb >> 16) & 0xFF) * factor);
        int g = (int) (((rgb >> 8) & 0xFF) * factor);
        int b = (int) ((rgb & 0xFF) * factor);
        if (r > 255) r = 255; else if (r < 0) r = 0;
        if (g > 255) g = 255; else if (g < 0) g = 0;
        if (b > 255) b = 255; else if (b < 0) b = 0;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}