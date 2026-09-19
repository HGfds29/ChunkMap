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
    public static final String VERSION = "1.0.0.0-beta.4+1.21.11";

    public static final String GITHUB_REPO = "HGfds29/ChunkMap";
    public static final String GITHUB_URL = "https://github.com/" + GITHUB_REPO;
    public static final String GITHUB_ISSUES_URL = GITHUB_URL + "/issues/new";
    public static final String GITHUB_API_ISSUES =
            "https://api.github.com/repos/" + GITHUB_REPO + "/issues";

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

        logger.cleanOldLogs(config.logRetentionDays());

        palette = BlockColorPalette.create(config.colorMode());
        renderer = new ChunkTopDownRenderer(config.tileResolution(), config.shadeByHeight());
        snapshotter = new ChunkSnapshotter(palette);
        snapshotter.setLogger(logger);

        if (dispatcher != null) {
            dispatcher.setRenderer(renderer);
            dispatcher.setSnapshotter(snapshotter);
            dispatcher.updateConfig(config);
        }

        if (old == null || !old.outputDir().equals(config.outputDir())) {
            storage = new TileStorage(config.outputDir());
        }
    }

    /**
     * 获取当前生效的 GitHub Token。
     * 只从配置中读取；未配置时返回空字符串。
     * （不再有硬编码的兜底 token，避免源码泄露风险。）
     */
    public static String getEffectiveToken() {
        if (config != null && config.githubToken() != null && !config.githubToken().isBlank()) {
            return config.githubToken().trim();
        }
        return "";
    }

    public static TileMapConfig getConfig() { return config; }
    public static FileLogger getLogger() { return logger; }
    public static BlockColorPalette getPalette() { return palette; }
    public static ChunkTopDownRenderer getRenderer() { return renderer; }
    public static RenderDispatcher getDispatcher() { return dispatcher; }
    public static TileStorage getStorage() { return storage; }
}