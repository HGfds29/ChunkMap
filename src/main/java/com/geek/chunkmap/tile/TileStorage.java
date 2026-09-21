package com.geek.chunkmap.tile;

import com.geek.chunkmap.ChunkMapMod;
import com.geek.chunkmap.util.FileLogger;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class TileStorage {
    private final Path baseDir;

    public TileStorage(String outputDir) {
        this.baseDir = Paths.get(outputDir);
        FileLogger lg = ChunkMapMod.getLogger();
        if (lg != null) lg.debug("[Storage] 创建 baseDir=" + baseDir.toAbsolutePath());
    }

    public Path tilePath(ResourceKey<Level> dimension, ChunkPos pos) {
        String dir = dimensionDirName(dimension);
        return baseDir.resolve(dir).resolve(pos.x + "_" + pos.z + ".png");
    }

    private String dimensionDirName(ResourceKey<Level> dim) {
        var loc = dim.identifier();
        return loc.getNamespace() + "/" + loc.getPath();
    }

    public void deleteTile(ResourceKey<Level> dimension, ChunkPos pos) {
        Path p = tilePath(dimension, pos);
        FileLogger lg = ChunkMapMod.getLogger();
        try {
            boolean deleted = Files.deleteIfExists(p);
            if (lg != null) {
                lg.count("storage.delete");
                if (lg.isEnabled(FileLogger.Level.DEBUG))
                    lg.debug("[Storage] delete " + p + " ok=" + deleted);
            }
        } catch (IOException e) {
            if (lg != null) lg.warn("[Storage] 删除失败 " + p + ": " + e.getMessage());
        }
    }

    public Path getBaseDir() { return baseDir; }
}