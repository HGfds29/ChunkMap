package com.geek.chunkmap.tile;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 内存瓦片缓存：维度 + 区块坐标 → (分辨率, ARGB 像素数组)。
 *
 * 渲染线程写入（RenderDispatcher.processJob），
 * UI 线程读取（ChunkMapScreen 拼接地图）。
 *
 * 关键点：缓存中同时记录每份瓦片的分辨率（边长像素数），
 * 读取时要求分辨率与调用方期望值一致，避免"改了 tileResolution 后
 * 拿旧 16×16 瓦片当成 32×32 读"造成越界。
 *
 * version 仅在内容真正变化时自增，避免 UI 每帧重建纹理。
 */
public class TileMapCache {

    /** 一份瓦片：resolution 为边长像素（16/32/64），pixels.length 必须 = resolution²。 */
    public record CachedTile(int resolution, int[] pixels) {}

    private final Map<ResourceKey<Level>, Map<ChunkPos, CachedTile>> tilesByDim = new ConcurrentHashMap<>();
    private final AtomicLong version = new AtomicLong();

    public void put(ResourceKey<Level> dim, ChunkPos pos, int resolution, int[] pixels) {
        if (dim == null || pos == null || pixels == null) return;
        Map<ChunkPos, CachedTile> m = tilesByDim.computeIfAbsent(dim, k -> new ConcurrentHashMap<>());
        CachedTile neu = new CachedTile(resolution, pixels);
        CachedTile old = m.put(pos, neu);
        if (old == null
                || old.resolution() != resolution
                || !Arrays.equals(old.pixels(), pixels)) {
            version.incrementAndGet();
        }
    }

    /**
     * 读取瓦片。
     * 只有缓存中的分辨率与 expectedRes 一致时才返回，否则返回 null。
     * 这样即使配置改了 tileResolution，也不会把旧尺寸的数据当成新尺寸用。
     */
    public int[] get(ResourceKey<Level> dim, ChunkPos pos, int expectedRes) {
        Map<ChunkPos, CachedTile> m = tilesByDim.get(dim);
        if (m == null) return null;
        CachedTile t = m.get(pos);
        if (t == null || t.resolution() != expectedRes) return null;
        return t.pixels();
    }

    public boolean has(ResourceKey<Level> dim, ChunkPos pos) {
        Map<ChunkPos, CachedTile> m = tilesByDim.get(dim);
        return m != null && m.containsKey(pos);
    }

    /** 数据版本号：任一瓦片内容变化都会 +1。 */
    public long version() {
        return version.get();
    }

    public int size(ResourceKey<Level> dim) {
        Map<ChunkPos, CachedTile> m = tilesByDim.get(dim);
        return m == null ? 0 : m.size();
    }

    public void clear() {
        tilesByDim.clear();
        version.incrementAndGet();
    }
}