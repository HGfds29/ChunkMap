package com.geek.chunkmap.config;

public record TileMapConfig(
        int tileResolution,
        String outputDir,
        ColorMode colorMode,
        boolean shadeByHeight,
        boolean uiAnimation,
        boolean deleteOnUnload,
        int renderThreads,
        int logRetentionDays
) {
    public enum ColorMode { MAP_COLOR, TEXTURE_AVERAGE }

    /**
     * 瓦片分辨率：只允许 16 / 32 / 64 三档。
     * 配置界面用这个枚举渲染下拉框，避免用户乱填数值。
     */
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