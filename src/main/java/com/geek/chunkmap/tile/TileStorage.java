package com.geek.chunkmap.tile;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 瓦片文件目录组织。
 * 结构：<outputDir>/<dim_namespace>/<dim_path>/<chunkX>_<chunkZ>.png
 * 例：chunkmap-output/minecraft/overworld/15_-23.png
 *     chunkmap-output/minecraft/the_nether/0_0.png
 */
public class TileStorage {
    private final Path baseDir;

    public TileStorage(String outputDir) {
        this.baseDir = Paths.get(outputDir);
    }

    /** 计算瓦片输出路径。 */
    public Path tilePath(ResourceKey<Level> dimension, ChunkPos pos) {
        String dir = dimensionDirName(dimension);
        return baseDir.resolve(dir).resolve(pos.x + "_" + pos.z + ".png");
    }

    /** 维度目录名：namespace/path（冒号转斜杠，安全文件名）。 */
    private String dimensionDirName(ResourceKey<Level> dim) {
        var loc = dim.identifier();
        return loc.getNamespace() + "/" + loc.getPath();
    }

    /** 删除瓦片（用于 unload 删除场景）。 */
    public void deleteTile(ResourceKey<Level> dimension, ChunkPos pos) {
        Path p = tilePath(dimension, pos);
        try {
            Files.deleteIfExists(p);
        } catch (IOException ignored) {
            // 删除失败不影响主流程
        }
    }

    public Path getBaseDir() { return baseDir; }
}
