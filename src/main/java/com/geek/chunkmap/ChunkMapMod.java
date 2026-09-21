package com.geek.chunkmap;

import com.geek.chunkmap.config.ConfigLoader;
import com.geek.chunkmap.config.DevToken;
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
    public static final String VERSION = "1.0.0.0-beta.5-Preview.1+1.21.11";

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
        FileLogger lg;

        try {
            config = ConfigLoader.load();

            lg = new FileLogger();
            lg.init();
            lg.setLevel(FileLogger.levelFromProperty("chunkmap.logLevel", FileLogger.Level.INFO));
            logger = lg;

            lg.info("========================================");
            lg.info(" ChunkMap 启动");
            lg.info(" 版本      : " + VERSION);
            lg.info(" 仓库      : " + GITHUB_REPO);
            lg.info(" Java      : " + System.getProperty("java.version"));
            lg.info(" OS        : " + System.getProperty("os.name") + " " + System.getProperty("os.arch"));
            lg.info(" 用户目录  : " + System.getProperty("user.dir"));
            lg.info(" 日志级别  : " + lg.getLevel());
            lg.info(" 配置文件  : " + ConfigLoader.configPath().toAbsolutePath());
            lg.info(" 日志文件  : " + lg.getCurrentLogFile().toAbsolutePath());
            lg.info("----------------------------------------");
            lg.info(" 配置内容  : " + config);
            lg.info("========================================");

            lg.cleanOldLogs(config.logRetentionDays());

            try (var s = lg.scope("init.palette")) {
                palette = BlockColorPalette.create(config.colorMode());
                lg.info("[Init] palette 颜色模式=" + config.colorMode()
                        + " class=" + palette.getClass().getSimpleName());
            }
            try (var s = lg.scope("init.renderer")) {
                renderer = new ChunkTopDownRenderer(config.tileResolution(), config.shadeByHeight());
                lg.info("[Init] renderer res=" + config.tileResolution()
                        + " shadeByHeight=" + config.shadeByHeight());
            }
            try (var s = lg.scope("init.snapshotter")) {
                snapshotter = new ChunkSnapshotter(palette);
                snapshotter.setLogger(lg);
            }
            try (var s = lg.scope("init.storage")) {
                storage = new TileStorage(config.outputDir());
                lg.info("[Init] storage baseDir=" + storage.getBaseDir().toAbsolutePath());
            }
            try (var s = lg.scope("init.dispatcher")) {
                dispatcher = new RenderDispatcher(config, lg, renderer, snapshotter);
                dispatcher.start();
            }
            try (var s = lg.scope("init.events")) {
                ClientEventHandler.register();
            }

            lg.info("===== ChunkMap 初始化完成，用时 "
                    + (System.currentTimeMillis() - t0) + "ms =====");
        } catch (Throwable t) {
            if (logger != null) logger.error("[Init] 致命异常", t);
            else t.printStackTrace();
            throw t;
        }
    }

    public static void reload() {
        if (logger == null) return;

        FileLogger lg = logger;
        lg.info("========================================");
        lg.info(" ChunkMap 重载配置");
        lg.info("----------------------------------------");

        TileMapConfig old = config;
        try (var s = lg.scope("reload.config.load")) {
            config = ConfigLoader.load();
        }

        lg.info(" 旧配置 : " + old);
        lg.info(" 新配置 : " + config);
        if (old != null) {
            lg.info(" tileResolution : " + old.tileResolution() + " → " + config.tileResolution());
            lg.info(" outputDir      : " + old.outputDir() + " → " + config.outputDir());
            lg.info(" colorMode      : " + old.colorMode() + " → " + config.colorMode());
            lg.info(" shadeByHeight  : " + old.shadeByHeight() + " → " + config.shadeByHeight());
            lg.info(" uiAnimation    : " + old.uiAnimation() + " → " + config.uiAnimation());
            lg.info(" deleteOnUnload : " + old.deleteOnUnload() + " → " + config.deleteOnUnload());
            lg.info(" renderThreads  : " + old.renderThreads() + " → " + config.renderThreads());
            lg.info(" logRetention   : " + old.logRetentionDays() + " → " + config.logRetentionDays());
        }
        lg.info("----------------------------------------");

        lg.cleanOldLogs(config.logRetentionDays());

        try (var s = lg.scope("reload.palette")) {
            palette = BlockColorPalette.create(config.colorMode());
        }
        try (var s = lg.scope("reload.renderer")) {
            renderer = new ChunkTopDownRenderer(config.tileResolution(), config.shadeByHeight());
        }
        try (var s = lg.scope("reload.snapshotter")) {
            snapshotter = new ChunkSnapshotter(palette);
            snapshotter.setLogger(lg);
        }

        if (dispatcher != null) {
            dispatcher.setRenderer(renderer);
            dispatcher.setSnapshotter(snapshotter);
            dispatcher.updateConfig(config);
        }

        if (old == null || !old.outputDir().equals(config.outputDir())) {
            storage = new TileStorage(config.outputDir());
            lg.info("[Reload] storage 重建 → " + storage.getBaseDir().toAbsolutePath());
        }

        lg.info("===== 重载完成 =====");
        lg.info("========================================");
    }

    /**
     * 读取优先级：
     *   1. config/chunkmap.json 的 githubToken
     *   2. DevToken.VALUE
     */
    public static String getEffectiveToken() {
        if (config != null
                && config.githubToken() != null
                && !config.githubToken().isBlank()) {
            if (logger != null && logger.isEnabled(FileLogger.Level.DEBUG))
                logger.debug("[Token] 使用 config 中的 token");
            return config.githubToken().trim();
        }
        try {
            String dev = DevToken.VALUE;
            if (dev != null && !dev.isBlank()) {
                if (logger != null && logger.isEnabled(FileLogger.Level.DEBUG))
                    logger.debug("[Token] 使用 DevToken.VALUE");
                return dev.trim();
            }
        } catch (Throwable ignored) {}
        if (logger != null && logger.isEnabled(FileLogger.Level.DEBUG))
            logger.debug("[Token] 无可用 token");
        return "";
    }

    public static TileMapConfig getConfig() { return config; }
    public static FileLogger getLogger() { return logger; }
    public static BlockColorPalette getPalette() { return palette; }
    public static ChunkTopDownRenderer getRenderer() { return renderer; }
    public static RenderDispatcher getDispatcher() { return dispatcher; }
    public static TileStorage getStorage() { return storage; }
}