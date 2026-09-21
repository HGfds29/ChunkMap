package com.geek.chunkmap.tile;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 磁盘瓦片拼接器。
 */
public final class TileMapStitcher {
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    // 限制画布总像素数（防止 OOM）：~100M 像素 ≈ 400MB
    private static final long MAX_PIXELS = 100_000_000L;

    private TileMapStitcher() {}

    public static Path stitch(String outputDir, Identifier dimId, int tileRes) throws IOException {
        Path tileRoot = Paths.get(outputDir, dimId.getNamespace(), dimId.getPath());
        if (!Files.isDirectory(tileRoot)) {
            return null;
        }

        Map<ChunkPos, BufferedImage> tiles = new HashMap<>();
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        try (Stream<Path> files = Files.list(tileRoot)) {
            for (Path p : files.filter(f -> f.getFileName().toString().endsWith(".png")).toList()) {
                String name = p.getFileName().toString();
                int dot = name.lastIndexOf('.');
                int us = name.lastIndexOf('_');
                if (us <= 0 || us > dot) continue;
                try {
                    int x = Integer.parseInt(name.substring(0, us));
                    int z = Integer.parseInt(name.substring(us + 1, dot));
                    ChunkPos pos = new ChunkPos(x, z);

                    BufferedImage img = ImageIO.read(p.toFile());
                    if (img == null) {
                        // 文件损坏/格式不支持，跳过
                        continue;
                    }
                    // 尺寸不匹配的也跳过，防止越界写入
                    if (img.getWidth() != tileRes || img.getHeight() != tileRes) {
                        continue;
                    }

                    tiles.put(pos, img);
                    minX = Math.min(minX, x); minZ = Math.min(minZ, z);
                    maxX = Math.max(maxX, x); maxZ = Math.max(maxZ, z);
                } catch (NumberFormatException ignored) {
                }
            }
        }

        if (tiles.isEmpty()) return null;

        int cols = maxX - minX + 1;
        int rows = maxZ - minZ + 1;
        long pixels = (long) cols * tileRes * rows * tileRes;
        if (pixels > MAX_PIXELS) {
            throw new IOException("瓦片区域过大 (" + cols + "x" + rows + " 区块)，无法拼接为单张大图");
        }

        BufferedImage canvas = new BufferedImage(cols * tileRes, rows * tileRes, BufferedImage.TYPE_INT_ARGB);

        Graphics2D g = canvas.createGraphics();
        g.setColor(new Color(0xFFB0B0B0, true));
        g.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
        g.setColor(new Color(0xFF9E9E9E, true));
        for (int i = 0; i <= cols; i++) {
            g.drawLine(i * tileRes, 0, i * tileRes, canvas.getHeight());
        }
        for (int j = 0; j <= rows; j++) {
            g.drawLine(0, j * tileRes, canvas.getWidth(), j * tileRes);
        }
        g.dispose();

        for (Map.Entry<ChunkPos, BufferedImage> e : tiles.entrySet()) {
            int dx = (e.getKey().x - minX) * tileRes;
            int dz = (e.getKey().z - minZ) * tileRes;
            BufferedImage tile = e.getValue();
            for (int i = 0; i < tileRes; i++) {
                for (int j = 0; j < tileRes; j++) {
                    int argb = tile.getRGB(i, j);
                    if (((argb >>> 24) & 0xFF) != 0) {
                        canvas.setRGB(dx + i, dz + j, argb);
                    }
                }
            }
        }

        Path outDir = Paths.get(outputDir, "stitched");
        Files.createDirectories(outDir);
        String stamp = LocalDateTime.now().format(TS);
        Path result = outDir.resolve(dimId.getPath() + "_" + stamp + ".png");
        ImageIO.write(canvas, "PNG", result.toFile());
        return result;
    }
}