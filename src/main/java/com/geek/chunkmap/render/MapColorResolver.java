package com.geek.chunkmap.render;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

/**
 * 基于 Minecraft 原生 MapColor。
 * 饱和度略增强，但明度不上调，防止整体过曝。
 */
public class MapColorResolver implements BlockColorResolver {

    /** 饱和度倍数：1.75 → 1.30（降幅，避免荧光感）。 */
    private static final float SAT_MUL = 1.30f;
    /** 明度倍数：1.08 → 0.98（轻微压暗，防止顶部过曝）。 */
    private static final float VAL_MUL = 0.98f;

    @Override
    public int resolve(BlockState state, BlockGetter level, BlockPos pos) {
        try {
            var mapColor = state.getMapColor(level, pos);
            if (mapColor == null) return 0xFF888888;
            int rgb = mapColor.calculateARGBColor(MapColor.Brightness.NORMAL);
            if (rgb == 0) return 0xFF555555;
            int argb = 0xFF000000 | (rgb & 0xFFFFFF);
            return boost(argb, SAT_MUL, VAL_MUL);
        } catch (Throwable t) {
            return 0xFF666666;
        }
    }

    private static int boost(int argb, float satMul, float valMul) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;

        int gray = (r * 30 + g * 59 + b * 11) / 100;

        int nr = clamp((int) (gray + (r - gray) * satMul));
        int ng = clamp((int) (gray + (g - gray) * satMul));
        int nb = clamp((int) (gray + (b - gray) * satMul));

        nr = clamp((int) (nr * valMul));
        ng = clamp((int) (ng * valMul));
        nb = clamp((int) (nb * valMul));

        return (a << 24) | (nr << 16) | (ng << 8) | nb;
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }
}