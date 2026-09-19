package com.geek.chunkmap.render;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 取方块模型的粒子图标（particle icon）的平均色作为颜色。
 * 任何一步失败（无模型 / 无 sprite / API 变化）都回退到 {@link MapColorResolver}。
 *
 * 结果会被 {@link BlockColorPalette} 缓存，每个 BlockState 只会走一次。
 *
 * 注意：Minecraft 1.21.11 中 SpriteContents.originalImage（私有字段）和
 * NativeImage.getPixelABGR（包级私有方法）均不可见，需通过反射访问。
 */
public class TextureAverageResolver implements BlockColorResolver {

    private static final Field SPRITE_ORIGINAL_IMAGE;
    private static final Method NATIVE_IMAGE_GET_PIXEL_ABGR;

    static {
        Field f = null;
        Method m = null;
        try {
            Class<?> spriteContentsClass = Class.forName("net.minecraft.client.renderer.texture.SpriteContents");
            f = spriteContentsClass.getDeclaredField("originalImage");
            f.setAccessible(true);
        } catch (Throwable ignored) {}
        try {
            m = NativeImage.class.getDeclaredMethod("getPixelABGR", int.class, int.class);
            m.setAccessible(true);
        } catch (Throwable ignored) {}
        SPRITE_ORIGINAL_IMAGE = f;
        NATIVE_IMAGE_GET_PIXEL_ABGR = m;
    }

    private final BlockColorResolver fallback = new MapColorResolver();

    @Override
    public int resolve(BlockState state, BlockGetter level, BlockPos pos) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return fallback.resolve(state, level, pos);

            // 1.21.11：BlockModelShaper 直接提供 getParticleIcon(state)，无需经过 BlockStateModel
            TextureAtlasSprite sprite = mc.getModelManager()
                    .getBlockModelShaper()
                    .getParticleIcon(state);
            if (sprite == null) return fallback.resolve(state, level, pos);

            return averageSprite(sprite);
        } catch (Throwable t) {
            return fallback.resolve(state, level, pos);
        }
    }

    private int averageSprite(TextureAtlasSprite sprite) {
        try {
            if (SPRITE_ORIGINAL_IMAGE == null || NATIVE_IMAGE_GET_PIXEL_ABGR == null) {
                return 0xFF888888;
            }

            // SpriteContents.originalImage 是私有字段，通过反射获取 NativeImage
            NativeImage img = (NativeImage) SPRITE_ORIGINAL_IMAGE.get(sprite.contents());
            if (img == null) return 0xFF888888;

            int w = Math.max(1, img.getWidth());
            int h = Math.max(1, img.getHeight());
            int stepX = Math.max(1, w / 8);
            int stepY = Math.max(1, h / 8);

            long r = 0, g = 0, b = 0;
            int n = 0;
            for (int i = 0; i < w; i += stepX) {
                for (int j = 0; j < h; j += stepY) {
                    // NativeImage.getPixelABGR(int, int) 是包级私有方法，通过反射调用
                    int abgr = (int) NATIVE_IMAGE_GET_PIXEL_ABGR.invoke(img, i, j);
                    int a = (abgr >>> 24) & 0xFF;
                    if (a < 128) continue;      // 半透明/全透明像素不参与平均
                    // ABGR: 字节0=R, 字节1=G, 字节2=B, 字节3=A
                    r += abgr & 0xFF;
                    g += (abgr >>> 8) & 0xFF;
                    b += (abgr >>> 16) & 0xFF;
                    n++;
                }
            }
            if (n == 0) return 0xFF888888;

            int ar = (int) (r / n);
            int ag = (int) (g / n);
            int ab = (int) (b / n);
            return 0xFF000000 | (ar << 16) | (ag << 8) | ab;
        } catch (Throwable t) {
            return 0xFF888888;
        }
    }
}