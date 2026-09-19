package com.geek.chunkmap.config;

public record TileMapConfig(
        int tileResolution,
        String outputDir,
        ColorMode colorMode,
        boolean shadeByHeight,
        boolean uiAnimation,       // 新增：是否启用 UI 动画
        boolean deleteOnUnload,
        int renderThreads,
        int logRetentionDays
) {
    public enum ColorMode { MAP_COLOR, TEXTURE_AVERAGE }

    public static TileMapConfig defaults() {
        return new TileMapConfig(
                32,
                "chunkmap-output",
                ColorMode.MAP_COLOR,
                true,
                true,              // uiAnimation 默认开
                false,
                2,
                7
        );
    }

    public TileMapConfig validated() {
        int res = (tileResolution == 16 || tileResolution == 32 || tileResolution == 64)
                ? tileResolution : 32;
        int threads = renderThreads > 0 && renderThreads <= 8 ? renderThreads : 2;
        int retention = logRetentionDays >= 0 ? logRetentionDays : 7;
        String dir = (outputDir == null || outputDir.isBlank()) ? "chunkmap-output" : outputDir;
        ColorMode mode = colorMode != null ? colorMode : ColorMode.MAP_COLOR;
        return new TileMapConfig(res, dir, mode, shadeByHeight, uiAnimation,
                deleteOnUnload, threads, retention);
    }
}