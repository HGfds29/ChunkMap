package com.geek.chunkmap.render;

import com.geek.chunkmap.util.FileLogger;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

public class ChunkSnapshotter {
    private final BlockColorPalette palette;
    private FileLogger logger;

    public ChunkSnapshotter(BlockColorPalette palette) {
        this.palette = palette;
    }

    public void setLogger(FileLogger logger) { this.logger = logger; }

    public ChunkSnapshot snapshot(ClientLevel level, ChunkPos pos) {
        if (level == null || !level.hasChunk(pos.x, pos.z)) return null;

        LevelChunk chunk = level.getChunk(pos.x, pos.z);
        int[] colors = new int[ChunkSnapshot.COLUMNS];
        int[] ys = new int[ChunkSnapshot.COLUMNS];
        int baseX = pos.getMinBlockX();
        int baseZ = pos.getMinBlockZ();
        int minY = level.getMinY();

        // 用 LevelChunk 自带的本地高度图（客户端根据收到的方块数据算），
        // 避免逐层扫描 384 层，快 ~300 倍。
        Heightmap surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE);

        int emptyCols = 0;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int idx = z * 16 + x;
                int topY = surface.getFirstAvailable(x, z) - 1;

                if (topY < minY) {
                    colors[idx] = 0;
                    ys[idx] = Integer.MIN_VALUE;
                    emptyCols++;
                    continue;
                }
                BlockPos bpos = new BlockPos(baseX + x, topY, baseZ + z);
                BlockState st = chunk.getBlockState(bpos);
                colors[idx] = palette.getColor(st, level, bpos);
                ys[idx] = topY;
            }
        }

        if (logger != null) {
            logger.trace("[Snapshot] " + pos + " minY=" + minY + " 空列=" + emptyCols);
        }

        return new ChunkSnapshot(pos, level.dimension(), colors, ys);
    }
}