package com.geek.chunkmap.ui;

import com.geek.chunkmap.ChunkMapMod;
import com.geek.chunkmap.config.ConfigLoader;
import com.geek.chunkmap.config.DebugKey;
import com.geek.chunkmap.config.TileMapConfig;
import com.geek.chunkmap.util.FileLogger;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Method;

public class ConfigurationScreen {

    public static Screen createConfigScreen(Screen parent) {
        TileMapConfig current = ConfigLoader.load();
        final MutableConfig holder = new MutableConfig(current);

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("chunkmap.config.title"))
                .setSavingRunnable(() -> {
                    ConfigLoader.save(holder.toRecord().validated());
                    ChunkMapMod.reload();
                })
                .setAfterInitConsumer(screen -> {
                    // 左下角：版本号，点 5 次弹解锁界面
                    VersionClickWidget verWidget = new VersionClickWidget(
                            6,
                            screen.height - 14,
                            "ChunkMap " + ChunkMapMod.VERSION + "  （点击 5 次解锁调试）",
                            () -> {
                                Minecraft mc = Minecraft.getInstance();
                                if (mc == null) return;
                                mc.setScreen(new DebugUnlockScreen(() ->
                                        mc.setScreen(ConfigurationScreen.createConfigScreen(parent))));
                            });
                    addWidgetReflectively(screen, verWidget);

                    // 右上角：已解锁时显示 DEBUG 徽章
                    TileMapConfig now = ChunkMapMod.getConfig();
                    if (now != null && now.debugMode()) {
                        addWidgetReflectively(screen,
                                new DebugBadgeWidget(screen.width - 84, 4));
                    }
                });

        ConfigEntryBuilder e = builder.entryBuilder();

        // ================= 通用 =================
        ConfigCategory general = builder.getOrCreateCategory(
                Component.translatable("chunkmap.config.category.general"));

        general.addEntry(e.startEnumSelector(
                        Component.translatable("chunkmap.config.tileResolution"),
                        TileMapConfig.Resolution.class,
                        TileMapConfig.Resolution.fromPx(current.tileResolution()))
                .setDefaultValue(TileMapConfig.Resolution.R32)
                .setTooltip(Component.translatable("chunkmap.config.tileResolution.tooltip"))
                .setSaveConsumer(v -> holder.tileResolution = v.px())
                .build());

        general.addEntry(e.startStrField(
                        Component.translatable("chunkmap.config.outputDir"),
                        current.outputDir())
                .setDefaultValue("chunkmap-output")
                .setTooltip(Component.translatable("chunkmap.config.outputDir.tooltip"))
                .setSaveConsumer(v -> holder.outputDir = v)
                .build());

        general.addEntry(e.startEnumSelector(
                        Component.translatable("chunkmap.config.colorMode"),
                        TileMapConfig.ColorMode.class,
                        current.colorMode())
                .setDefaultValue(TileMapConfig.ColorMode.MAP_COLOR)
                .setTooltip(Component.translatable("chunkmap.config.colorMode.tooltip"))
                .setSaveConsumer(v -> holder.colorMode = v)
                .build());

        general.addEntry(e.startBooleanToggle(
                        Component.translatable("chunkmap.config.shadeByHeight"),
                        current.shadeByHeight())
                .setDefaultValue(true)
                .setTooltip(Component.translatable("chunkmap.config.shadeByHeight.tooltip"))
                .setSaveConsumer(v -> holder.shadeByHeight = v)
                .build());

        general.addEntry(e.startBooleanToggle(
                        Component.translatable("chunkmap.config.uiAnimation"),
                        current.uiAnimation())
                .setDefaultValue(true)
                .setTooltip(Component.translatable("chunkmap.config.uiAnimation.tooltip"))
                .setSaveConsumer(v -> holder.uiAnimation = v)
                .build());

        general.addEntry(e.startBooleanToggle(
                        Component.translatable("chunkmap.config.deleteOnUnload"),
                        current.deleteOnUnload())
                .setDefaultValue(false)
                .setTooltip(Component.translatable("chunkmap.config.deleteOnUnload.tooltip"))
                .setSaveConsumer(v -> holder.deleteOnUnload = v)
                .build());

        // ================= 反馈 =================
        ConfigCategory feedback = builder.getOrCreateCategory(
                Component.translatable("chunkmap.config.category.feedback"));

        feedback.addEntry(e.startStrField(
                        Component.translatable("chunkmap.config.githubToken"),
                        current.githubToken())
                .setDefaultValue("")
                .setTooltip(Component.translatable("chunkmap.config.githubToken.tooltip"))
                .setSaveConsumer(v -> holder.githubToken = v)
                .build());

        feedback.addEntry(e.startTextDescription(
                Component.translatable("chunkmap.config.githubToken.hint")).build());

        // ================= 性能 =================
        ConfigCategory perf = builder.getOrCreateCategory(
                Component.translatable("chunkmap.config.category.performance"));

        perf.addEntry(e.startIntSlider(
                        Component.translatable("chunkmap.config.renderThreads"),
                        current.renderThreads(), 1, 8)
                .setDefaultValue(2)
                .setTextGetter(v -> Component.literal(v + " 线程"))
                .setTooltip(Component.translatable("chunkmap.config.renderThreads.tooltip"))
                .setSaveConsumer(v -> holder.renderThreads = v)
                .build());

        perf.addEntry(e.startIntSlider(
                        Component.translatable("chunkmap.config.logRetentionDays"),
                        current.logRetentionDays(), 0, 30)
                .setDefaultValue(7)
                .setTextGetter(v -> v == 0
                        ? Component.translatable("chunkmap.config.logRetentionDays.never")
                        : Component.literal(v + " 天"))
                .setTooltip(Component.translatable("chunkmap.config.logRetentionDays.tooltip"))
                .setSaveConsumer(v -> holder.logRetentionDays = v)
                .build());

        // ================= 调试 =================
        ConfigCategory debug = builder.getOrCreateCategory(
                Component.translatable("chunkmap.config.category.debug"));

        if (!current.debugMode()) {
            debug.addEntry(e.startStrField(
                            Component.translatable("chunkmap.config.debug.password"),
                            "")
                    .setDefaultValue("")
                    .setTooltip(Component.translatable("chunkmap.config.debug.password.tooltip"))
                    .setSaveConsumer(v -> {
                        if (DebugKey.VALUE.equals(v == null ? "" : v.trim())) {
                            holder.debugMode = true;
                            holder.debugLogLevel = TileMapConfig.LogLevel.DEBUG;
                        } else {
                            holder.debugMode = false;
                        }
                    })
                    .build());

            debug.addEntry(e.startTextDescription(
                    Component.translatable("chunkmap.config.debug.locked")).build());
            debug.addEntry(e.startTextDescription(
                    Component.translatable("chunkmap.config.debug.password.hint")).build());
        } else {
            // ---------- 基础 ----------
            debug.addEntry(e.startEnumSelector(
                            Component.translatable("chunkmap.config.debug.logLevel"),
                            TileMapConfig.LogLevel.class,
                            current.debugLogLevel())
                    .setDefaultValue(TileMapConfig.LogLevel.INFO)
                    .setTooltip(Component.translatable("chunkmap.config.debug.logLevel.tooltip"))
                    .setSaveConsumer(v -> holder.debugLogLevel = v)
                    .build());

            debug.addEntry(e.startBooleanToggle(
                            Component.translatable("chunkmap.config.debug.overlay"),
                            current.debugOverlay())
                    .setDefaultValue(false)
                    .setTooltip(Component.translatable("chunkmap.config.debug.overlay.tooltip"))
                    .setSaveConsumer(v -> holder.debugOverlay = v)
                    .build());

            // ---------- 视觉 ----------
            debug.addEntry(e.startBooleanToggle(
                            Component.translatable("chunkmap.config.debug.showChunkBorders"),
                            current.debugShowChunkBorders())
                    .setDefaultValue(false)
                    .setTooltip(Component.translatable("chunkmap.config.debug.showChunkBorders.tooltip"))
                    .setSaveConsumer(v -> holder.debugShowChunkBorders = v)
                    .build());

            debug.addEntry(e.startBooleanToggle(
                            Component.translatable("chunkmap.config.debug.showTileCoords"),
                            current.debugShowTileCoords())
                    .setDefaultValue(false)
                    .setTooltip(Component.translatable("chunkmap.config.debug.showTileCoords.tooltip"))
                    .setSaveConsumer(v -> holder.debugShowTileCoords = v)
                    .build());

            debug.addEntry(e.startBooleanToggle(
                            Component.translatable("chunkmap.config.debug.showPlayerChunk"),
                            current.debugShowPlayerChunk())
                    .setDefaultValue(false)
                    .setTooltip(Component.translatable("chunkmap.config.debug.showPlayerChunk.tooltip"))
                    .setSaveConsumer(v -> holder.debugShowPlayerChunk = v)
                    .build());

            debug.addEntry(e.startBooleanToggle(
                            Component.translatable("chunkmap.config.debug.showOrigin"),
                            current.debugShowOrigin())
                    .setDefaultValue(false)
                    .setTooltip(Component.translatable("chunkmap.config.debug.showOrigin.tooltip"))
                    .setSaveConsumer(v -> holder.debugShowOrigin = v)
                    .build());

            debug.addEntry(e.startBooleanToggle(
                            Component.translatable("chunkmap.config.debug.showHighlight"),
                            current.debugShowHighlight())
                    .setDefaultValue(false)
                    .setTooltip(Component.translatable("chunkmap.config.debug.showHighlight.tooltip"))
                    .setSaveConsumer(v -> holder.debugShowHighlight = v)
                    .build());

            debug.addEntry(e.startBooleanToggle(
                            Component.translatable("chunkmap.config.debug.noGrid"),
                            current.debugNoGrid())
                    .setDefaultValue(false)
                    .setTooltip(Component.translatable("chunkmap.config.debug.noGrid.tooltip"))
                    .setSaveConsumer(v -> holder.debugNoGrid = v)
                    .build());

            debug.addEntry(e.startBooleanToggle(
                            Component.translatable("chunkmap.config.debug.wireframe"),
                            current.debugWireframe())
                    .setDefaultValue(false)
                    .setTooltip(Component.translatable("chunkmap.config.debug.wireframe.tooltip"))
                    .setSaveConsumer(v -> holder.debugWireframe = v)
                    .build());

            // ---------- 信息 ----------
            debug.addEntry(e.startBooleanToggle(
                            Component.translatable("chunkmap.config.debug.showFps"),
                            current.debugShowFps())
                    .setDefaultValue(false)
                    .setTooltip(Component.translatable("chunkmap.config.debug.showFps.tooltip"))
                    .setSaveConsumer(v -> holder.debugShowFps = v)
                    .build());

            debug.addEntry(e.startBooleanToggle(
                            Component.translatable("chunkmap.config.debug.showMemory"),
                            current.debugShowMemory())
                    .setDefaultValue(false)
                    .setTooltip(Component.translatable("chunkmap.config.debug.showMemory.tooltip"))
                    .setSaveConsumer(v -> holder.debugShowMemory = v)
                    .build());

            debug.addEntry(e.startBooleanToggle(
                            Component.translatable("chunkmap.config.debug.showQueue"),
                            current.debugShowQueue())
                    .setDefaultValue(false)
                    .setTooltip(Component.translatable("chunkmap.config.debug.showQueue.tooltip"))
                    .setSaveConsumer(v -> holder.debugShowQueue = v)
                    .build());

            debug.addEntry(e.startBooleanToggle(
                            Component.translatable("chunkmap.config.debug.showTimings"),
                            current.debugShowTimings())
                    .setDefaultValue(false)
                    .setTooltip(Component.translatable("chunkmap.config.debug.showTimings.tooltip"))
                    .setSaveConsumer(v -> holder.debugShowTimings = v)
                    .build());

            // ---------- 行为 ----------
            debug.addEntry(e.startBooleanToggle(
                            Component.translatable("chunkmap.config.debug.forceRebuild"),
                            current.debugForceRebuild())
                    .setDefaultValue(false)
                    .setTooltip(Component.translatable("chunkmap.config.debug.forceRebuild.tooltip"))
                    .setSaveConsumer(v -> holder.debugForceRebuild = v)
                    .build());

            debug.addEntry(e.startBooleanToggle(
                            Component.translatable("chunkmap.config.debug.lock"),
                            false)
                    .setDefaultValue(false)
                    .setTooltip(Component.translatable("chunkmap.config.debug.lock.tooltip"))
                    .setSaveConsumer(v -> {
                        if (v) {
                            holder.debugMode = false;
                            holder.debugLogLevel = TileMapConfig.LogLevel.INFO;
                            holder.debugOverlay = false;
                            holder.debugShowChunkBorders = false;
                            holder.debugShowTileCoords = false;
                            holder.debugShowPlayerChunk = false;
                            holder.debugShowOrigin = false;
                            holder.debugShowHighlight = false;
                            holder.debugNoGrid = false;
                            holder.debugWireframe = false;
                            holder.debugShowFps = false;
                            holder.debugShowMemory = false;
                            holder.debugShowQueue = false;
                            holder.debugShowTimings = false;
                            holder.debugForceRebuild = false;
                        }
                    })
                    .build());

            debug.addEntry(e.startTextDescription(
                    Component.translatable("chunkmap.config.debug.unlocked")).build());
        }

        // ================= 关于 =================
        ConfigCategory about = builder.getOrCreateCategory(
                Component.translatable("chunkmap.config.category.about"));

        about.addEntry(e.startTextDescription(
                Component.translatable("chunkmap.config.about.version",
                        ChunkMapMod.VERSION)).build());

        about.addEntry(e.startTextDescription(
                Component.translatable("chunkmap.config.about.hint")).build());

        return builder.build();
    }

    // ==================== 反射工具 ====================

    /**
     * Screen.addRenderableWidget 是 protected，在 lambda 里访问不到。
     * 通过反射调用；失败也不崩。
     */
    private static void addWidgetReflectively(Screen screen, GuiEventListener widget) {
        try {
            Method m = Screen.class.getDeclaredMethod("addRenderableWidget", GuiEventListener.class);
            m.setAccessible(true);
            m.invoke(screen, widget);
        } catch (Throwable t) {
            FileLogger lg = ChunkMapMod.getLogger();
            if (lg != null) lg.warn("[UI] 添加组件失败: " + t.getMessage());
        }
    }

    // ==================== 内部组件 ====================

    /** 顶栏 DEBUG 徽章。 */
    private static class DebugBadgeWidget extends AbstractWidget {
        DebugBadgeWidget(int x, int y) {
            super(x, y, 80, 14, Component.literal("DEBUG"));
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float delta) {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.font == null) return;
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            g.fill(x, y, x + w, y + h, 0xFF7EE787);
            g.drawCenteredString(mc.font, "🔓 DEBUG", x + w / 2, y + 3, 0xFF101418);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
            defaultButtonNarrationText(out);
        }
    }

    // ==================== 可变配置 ====================

    private static class MutableConfig {
        int tileResolution;
        String outputDir;
        TileMapConfig.ColorMode colorMode;
        boolean shadeByHeight;
        boolean uiAnimation;
        boolean deleteOnUnload;
        int renderThreads;
        int logRetentionDays;
        String githubToken;
        boolean debugMode;
        TileMapConfig.LogLevel debugLogLevel;
        boolean debugOverlay;
        // ---- 新增 ----
        boolean debugShowChunkBorders;
        boolean debugShowTileCoords;
        boolean debugShowPlayerChunk;
        boolean debugShowOrigin;
        boolean debugShowHighlight;
        boolean debugNoGrid;
        boolean debugWireframe;
        boolean debugShowFps;
        boolean debugShowMemory;
        boolean debugShowQueue;
        boolean debugShowTimings;
        boolean debugForceRebuild;

        MutableConfig(TileMapConfig c) {
            this.tileResolution = c.tileResolution();
            this.outputDir = c.outputDir();
            this.colorMode = c.colorMode();
            this.shadeByHeight = c.shadeByHeight();
            this.uiAnimation = c.uiAnimation();
            this.deleteOnUnload = c.deleteOnUnload();
            this.renderThreads = c.renderThreads();
            this.logRetentionDays = c.logRetentionDays();
            this.githubToken = c.githubToken();
            this.debugMode = c.debugMode();
            this.debugLogLevel = c.debugLogLevel();
            this.debugOverlay = c.debugOverlay();
            this.debugShowChunkBorders = c.debugShowChunkBorders();
            this.debugShowTileCoords = c.debugShowTileCoords();
            this.debugShowPlayerChunk = c.debugShowPlayerChunk();
            this.debugShowOrigin = c.debugShowOrigin();
            this.debugShowHighlight = c.debugShowHighlight();
            this.debugNoGrid = c.debugNoGrid();
            this.debugWireframe = c.debugWireframe();
            this.debugShowFps = c.debugShowFps();
            this.debugShowMemory = c.debugShowMemory();
            this.debugShowQueue = c.debugShowQueue();
            this.debugShowTimings = c.debugShowTimings();
            this.debugForceRebuild = c.debugForceRebuild();
        }

        TileMapConfig toRecord() {
            return new TileMapConfig(tileResolution, outputDir, colorMode,
                    shadeByHeight, uiAnimation, deleteOnUnload,
                    renderThreads, logRetentionDays, githubToken,
                    debugMode, debugLogLevel, debugOverlay,
                    debugShowChunkBorders, debugShowTileCoords, debugShowPlayerChunk,
                    debugShowOrigin, debugShowHighlight, debugNoGrid, debugWireframe,
                    debugShowFps, debugShowMemory, debugShowQueue, debugShowTimings,
                    debugForceRebuild);
        }
    }
}