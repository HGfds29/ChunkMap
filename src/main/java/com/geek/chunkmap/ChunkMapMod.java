package com.geek.chunkmap;

import com.geek.chunkmap.config.ConfigLoader;
import com.geek.chunkmap.config.TileMapConfig;
import com.geek.chunkmap.event.ClientEventHandler;
import com.geek.chunkmap.render.BlockColorPalette;
import com.geek.chunkmap.render.ChunkSnapshotter;
import com.geek.chunkmap.render.ChunkTopDownRenderer;
import com.geek.chunkmap.tile.RenderDispatcher;
import com.geek.chunkmap.tile.TileStorage;
import com.geek.chunkmap.util.FileLogger;
import net.fabricmc.api.ClientModInitializer;

public class ChunkMapMod implements ClientModInitializer {
    public static final String MOD_ID = "chunkmap";

    /** 唯一版本号，UI / 配置界面统一引用。 */
    public static final String VERSION = "1.0.0.0-beta.2+1.21.11";

    private static TileMapConfig config;
    private static FileLogger logger;
    private static BlockColorPalette palette;
    private static ChunkTopDownRenderer renderer;
    private static ChunkSnapshotter snapshotter;
    private static RenderDispatcher dispatcher;
    private static TileStorage storage;

    @Override
    public void onInitializeClient() {
        long t0 = System.currentTimeMillis();

        config = ConfigLoader.load();

        logger = new FileLogger();
        logger.init();
        // 允许 -Dchunkmap.logLevel=TRACE 打开详细日志，默认 INFO
        logger.setLevel(FileLogger.levelFromProperty("chunkmap.logLevel", FileLogger.Level.INFO));

        logger.cleanOldLogs(config.logRetentionDays());
        logger.info("===== ChunkMap 启动 =====");
        logger.info("版本 " + VERSION);
        logger.info("配置: " + config);
        logger.info("日志文件: " + logger.getCurrentLogFile().toAbsolutePath());
        logger.info("======================");

        palette = BlockColorPalette.create(config.colorMode());
        renderer = new ChunkTopDownRenderer(config.tileResolution(), config.shadeByHeight());
        snapshotter = new ChunkSnapshotter(palette);
        snapshotter.setLogger(logger);
        storage = new TileStorage(config.outputDir());

        dispatcher = new RenderDispatcher(config, logger, renderer, snapshotter);
        dispatcher.start();

        ClientEventHandler.register();
        logger.info("===== ChunkMap 初始化完成，用时 " + (System.currentTimeMillis() - t0) + "ms =====");
    }

    /**
     * 重新加载配置并重建整条渲染链路。
     * 注意：dispatcher 内的 config / storage 也会一起同步。
     */
    public static void reload() {
        if (logger == null) return;

        TileMapConfig old = config;
        config = ConfigLoader.load();

        logger.info("===== ChunkMap 重载配置 =====");
        logger.info("旧: " + old);
        logger.info("新: " + config);
        logger.info("shadeByHeight: " + (old == null ? "?" : old.shadeByHeight())
                + " → " + config.shadeByHeight());
        logger.info("============================");

        palette = BlockColorPalette.create(config.colorMode());
        renderer = new ChunkTopDownRenderer(config.tileResolution(), config.shadeByHeight());
        snapshotter = new ChunkSnapshotter(palette);
        snapshotter.setLogger(logger);

        if (dispatcher != null) {
            dispatcher.setRenderer(renderer);
            dispatcher.setSnapshotter(snapshotter);
            // 关键：同步 config 与 storage（outputDir 变了会重建 storage）
            dispatcher.updateConfig(config);
        }

        // 外部 storage 只在 outputDir 变化时重建，避免与 dispatcher 内的 storage 指向不同目录
        if (old == null || !old.outputDir().equals(config.outputDir())) {
            storage = new TileStorage(config.outputDir());
        }
    }

    public static TileMapConfig getConfig() { return config; }
    public static FileLogger getLogger() { return logger; }
    public static BlockColorPalette getPalette() { return palette; }
    public static ChunkTopDownRenderer getRenderer() { return renderer; }
    public static RenderDispatcher getDispatcher() { return dispatcher; }
    public static TileStorage getStorage() { return storage; }
}