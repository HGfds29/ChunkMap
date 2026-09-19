package com.geek.chunkmap.render;

import com.geek.chunkmap.config.TileMapConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BlockState → RGB 的统一入口，带缓存。
 * 根据 config 选择解析策略（MAP_COLOR 或 TEXTURE_AVERAGE）。
 */
public class BlockColorPalette {
    private final BlockColorResolver resolver;
    private final Map<BlockState, Integer> cache = new ConcurrentHashMap<>();

    public BlockColorPalette(BlockColorResolver resolver) {
        this.resolver = resolver;
    }

    public int getColor(BlockState state, BlockGetter level, BlockPos pos) {
        if (state == null || state.isAir()) {
            return 0x00000000; // 透明
        }
        // BlockState 通常是单例（每个状态对应一个对象），可直接作缓存键
        return cache.computeIfAbsent(state, s -> resolver.resolve(s, level, pos));
    }

    public static BlockColorPalette create(TileMapConfig.ColorMode mode) {
        if (mode == TileMapConfig.ColorMode.TEXTURE_AVERAGE) {
            // 纹理平均色暂以 MapColor 实现占位（阶段后续可扩展）
            return new BlockColorPalette(new MapColorResolver());
        }
        return new BlockColorPalette(new MapColorResolver());
    }
}
