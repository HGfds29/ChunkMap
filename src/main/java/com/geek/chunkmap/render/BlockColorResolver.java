package com.geek.chunkmap.render;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * BlockState → ARGB 颜色的解析接口。
 */
@FunctionalInterface
public interface BlockColorResolver {
    /** 返回 ARGB int（0xAARRGGBB）。 */
    int resolve(BlockState state, BlockGetter level, BlockPos pos);
}
