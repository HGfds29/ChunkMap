package com.geek.chunkmap.config;

public record TileMapConfig(
        int tileResolution,
        String outputDir,
        ColorMode colorMode,
        boolean shadeByHeight,
        boolean uiAnimation,
        boolean deleteOnUnload,
        int renderThreads,
        int logRetentionDays,
        String githubToken,
        // ---- 调试基础 ----
        boolean debugMode,
        LogLevel debugLogLevel,
        boolean debugOverlay,
        // ---- 调试视觉 ----
        boolean debugShowChunkBorders,
        boolean debugShowTileCoords,
        boolean debugShowPlayerChunk,
        boolean debugShowOrigin,
        boolean debugShowHighlight,
        boolean debugNoGrid,
        boolean debugWireframe,
        // ---- 调试信息 ----
        boolean debugShowFps,
        boolean debugShowMemory,
        boolean debugShowQueue,
        boolean debugShowTimings,
        // ---- 调试行为 ----
        boolean debugForceRebuild
) {
    public enum ColorMode { MAP_COLOR, TEXTURE_AVERAGE }

    public enum LogLevel { TRACE, DEBUG, INFO, WARN, ERROR, OFF }

    public enum Resolution {
        R16(16), R32(32), R64(64);

        private final int px;
        Resolution(int px) { this.px = px; }
        public int px() { return px; }

        public static Resolution fromPx(int px) {
            return switch (px) {
                case 16 -> R16;
                case 64 -> R64;
                default -> R32;
            };
        }

        @Override
        public String toString() { return px + " × " + px; }
    }

    public static TileMapConfig defaults() {
        return new TileMapConfig(
                32,
                "chunkmap-output",
                ColorMode.MAP_COLOR,
                true,
                true,
                false,
                2,
                7,
                "",
                false,
                LogLevel.INFO,
                false,
                false, false, false, false, false, false, false,
                false, false, false, false,
                false
        );
    }

    public TileMapConfig validated() {
        int res = (tileResolution == 16 || tileResolution == 32 || tileResolution == 64)
                ? tileResolution : 32;
        int threads = renderThreads > 0 && renderThreads <= 8 ? renderThreads : 2;
        int retention = logRetentionDays >= 0 ? logRetentionDays : 7;
        String dir = (outputDir == null || outputDir.isBlank()) ? "chunkmap-output" : outputDir;
        ColorMode mode = colorMode != null ? colorMode : ColorMode.MAP_COLOR;
        String token = githubToken == null ? "" : githubToken.trim();
        LogLevel dlv = debugLogLevel != null ? debugLogLevel : LogLevel.INFO;
        return new TileMapConfig(res, dir, mode, shadeByHeight, uiAnimation,
                deleteOnUnload, threads, retention, token,
                debugMode, dlv, debugOverlay,
                debugShowChunkBorders, debugShowTileCoords, debugShowPlayerChunk,
                debugShowOrigin, debugShowHighlight, debugNoGrid, debugWireframe,
                debugShowFps, debugShowMemory, debugShowQueue, debugShowTimings,
                debugForceRebuild);
    }

    /** ★ 关键方法：只改 debugMode / debugLogLevel，其余全部保留。 */
    public TileMapConfig withDebug(boolean enabled, LogLevel level) {
        return new TileMapConfig(tileResolution, outputDir, colorMode, shadeByHeight,
                uiAnimation, deleteOnUnload, renderThreads, logRetentionDays, githubToken,
                enabled, level, debugOverlay,
                debugShowChunkBorders, debugShowTileCoords, debugShowPlayerChunk,
                debugShowOrigin, debugShowHighlight, debugNoGrid, debugWireframe,
                debugShowFps, debugShowMemory, debugShowQueue, debugShowTimings,
                debugForceRebuild);
    }
}