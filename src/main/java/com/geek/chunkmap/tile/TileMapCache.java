package com.geek.chunkmap.tile;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 内存瓦片缓存：维度 + 区块坐标 → ARGB 像素数组。
 *
 * 渲染线程写入（RenderDispatcher.processJob），
 * UI 线程读取（ChunkMapScreen 拼接地图）。
 * version 仅在内容真正变化时自增，避免 UI 每帧重建纹理。
 */
public class TileMapCache {
    private final Map<ResourceKey<Level>, Map<ChunkPos, int[]>> tilesByDim = new ConcurrentHashMap<>();
    private final AtomicLong version = new AtomicLong();

    public void put(ResourceKey<Level> dim, ChunkPos pos, int[] pixels) {
        Map<ChunkPos, int[]> m = tilesByDim.computeIfAbsent(dim, k -> new ConcurrentHashMap<>());
        int[] old = m.put(pos, pixels);
        if (old == null || !Arrays.equals(old, pixels)) {
            version.incrementAndGet();
        }
    }

    public int[] get(ResourceKey<Level> dim, ChunkPos pos) {
        Map<ChunkPos, int[]> m = tilesByDim.get(dim);
        return m == null ? null : m.get(pos);
    }

    public boolean has(ResourceKey<Level> dim, ChunkPos pos) {
        Map<ChunkPos, int[]> m = tilesByDim.get(dim);
        return m != null && m.containsKey(pos);
    }

    /** 数据版本号：任一瓦片内容变化都会 +1。 */
    public long version() {
        return version.get();
    }

    public int size(ResourceKey<Level> dim) {
        Map<ChunkPos, int[]> m = tilesByDim.get(dim);
        return m == null ? 0 : m.size();
    }

    public void clear() {
        tilesByDim.clear();
        version.incrementAndGet();
    }
}