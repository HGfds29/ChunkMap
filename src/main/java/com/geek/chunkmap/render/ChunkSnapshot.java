package com.geek.chunkmap.render;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * 区块渲染快照：在主线程读取 ClientLevel 后打包，
 * 之后工作线程仅基于快照做像素运算，不再访问 ClientLevel，避免线程安全问题。
 *
 * colors[i] : 该列顶部方块的颜色（ARGB），0 表示空列
 * topYs[i]  : 该列顶部方块的 Y 坐标，空列时为 Integer.MIN_VALUE
 * i = z * 16 + x
 */
public record ChunkSnapshot(
        ChunkPos pos,
        ResourceKey<Level> dim,
        int[] colors,
        int[] topYs
) {
    public static final int COLUMNS = 16 * 16;
}