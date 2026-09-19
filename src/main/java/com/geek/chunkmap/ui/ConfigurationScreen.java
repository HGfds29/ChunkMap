package com.geek.chunkmap.ui;

import com.geek.chunkmap.ChunkMapMod;
import com.geek.chunkmap.config.ConfigLoader;
import com.geek.chunkmap.config.TileMapConfig;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 基于 Cloth Config 的配置界面。
 *
 * 打开方式：
 *   1. Alt + M 快捷键（在 ClientEventHandler 中注册）
 *   2. Mod Menu 模组列表 → ChunkMap → 配置按钮
 */
public class ConfigurationScreen implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ConfigurationScreen::createConfigScreen;
    }

    public static Screen createConfigScreen(Screen parent) {
        TileMapConfig current = ConfigLoader.load();
        final MutableConfig holder = new MutableConfig(current);

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("chunkmap.config.title"))
                .setSavingRunnable(() -> ConfigLoader.save(holder.toRecord()));

        ConfigEntryBuilder e = builder.entryBuilder();

        // ---------- 通用 ----------
        ConfigCategory general = builder.getOrCreateCategory(
                Component.translatable("chunkmap.config.category.general"));

        // 关键：改成枚举选择器，只有 16×16 / 32×32 / 64×64 三档
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

        // ---------- 性能 ----------
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

        // ---------- 关于 ----------
        ConfigCategory about = builder.getOrCreateCategory(
                Component.translatable("chunkmap.config.category.about"));

        about.addEntry(e.startTextDescription(
                Component.translatable("chunkmap.config.about.version",
                        ChunkMapMod.VERSION)).build());

        about.addEntry(e.startTextDescription(
                Component.translatable("chunkmap.config.about.hint")).build());

        return builder.build();
    }

    /** 可变配置持有对象，Cloth Config 的 setSaveConsumer 写入这里。 */
    private static class MutableConfig {
        int tileResolution;
        String outputDir;
        TileMapConfig.ColorMode colorMode;
        boolean shadeByHeight;
        boolean uiAnimation;
        boolean deleteOnUnload;
        int renderThreads;
        int logRetentionDays;

        MutableConfig(TileMapConfig c) {
            this.tileResolution = c.tileResolution();
            this.outputDir = c.outputDir();
            this.colorMode = c.colorMode();
            this.shadeByHeight = c.shadeByHeight();
            this.uiAnimation = c.uiAnimation();
            this.deleteOnUnload = c.deleteOnUnload();
            this.renderThreads = c.renderThreads();
            this.logRetentionDays = c.logRetentionDays();
        }

        TileMapConfig toRecord() {
            return new TileMapConfig(tileResolution, outputDir, colorMode,
                    shadeByHeight, uiAnimation, deleteOnUnload,
                    renderThreads, logRetentionDays);
        }
    }
}