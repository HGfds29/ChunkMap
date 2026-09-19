package com.geek.chunkmap.tile;

import com.geek.chunkmap.ChunkMapMod;
import com.geek.chunkmap.util.FileLogger;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class TilePngWriter {
    private TilePngWriter() {}

    public static void write(int[] pixels, int width, int height, Path output) throws IOException {
        long t0 = System.nanoTime();
        Files.createDirectories(output.getParent());
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, width, height, pixels, 0, width);
        ImageIO.write(img, "PNG", output.toFile());

        FileLogger lg = ChunkMapMod.getLogger();
        if (lg != null) {
            lg.trace("[Png] 写入 " + output.getFileName()
                    + " " + width + "x" + height
                    + " 耗时=" + ((System.nanoTime() - t0) / 1_000_000) + "ms");
        }
    }
}