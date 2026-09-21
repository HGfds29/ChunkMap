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
        if (level == null || !level.hasChunk(pos.x, pos.z)) {
            if (logger != null && logger.isEnabled(FileLogger.Level.TRACE))
                logger.trace("[Snapshot] 无区块 " + pos);
            return null;
        }

        long t0 = System.nanoTime();
        LevelChunk chunk = level.getChunk(pos.x, pos.z);
        int[] colors = new int[ChunkSnapshot.COLUMNS];
        int[] ys = new int[ChunkSnapshot.COLUMNS];
        int baseX = pos.getMinBlockX();
        int baseZ = pos.getMinBlockZ();
        int minY = level.getMinY();

        Heightmap surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE);

        int emptyCols = 0;
        int nonEmpty = 0;
        int maxY = Integer.MIN_VALUE;
        int minSurfaceY = Integer.MAX_VALUE;

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
                nonEmpty++;
                if (topY > maxY) maxY = topY;
                if (topY < minSurfaceY) minSurfaceY = topY;
            }
        }

        if (logger != null) {
            logger.count("snapshot.created");
            if (logger.isEnabled(FileLogger.Level.TRACE)) {
                long ms = (System.nanoTime() - t0) / 1_000_000;
                logger.trace("[Snapshot] " + pos + " minY=" + minY
                        + " 空列=" + emptyCols + " 非空=" + nonEmpty
                        + " 顶Y范围=[" + minSurfaceY + "," + maxY + "]"
                        + " 耗时=" + ms + "ms");
            }
        }

        return new ChunkSnapshot(pos, level.dimension(), colors, ys);
    }
}