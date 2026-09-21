package com.geek.chunkmap.ui;

import com.geek.chunkmap.ChunkMapMod;
import com.geek.chunkmap.config.ConfigLoader;
import com.geek.chunkmap.config.DebugKey;
import com.geek.chunkmap.config.TileMapConfig;
import com.geek.chunkmap.util.FileLogger;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * 调试模式解锁界面。
 */
public class DebugUnlockScreen extends Screen {

    private final Runnable onClose;

    private EditBox input;
    private String status = "";
    private int statusColor = 0xFF8B949E;

    public DebugUnlockScreen(Runnable onClose) {
        super(Component.literal("解锁调试模式"));
        this.onClose = onClose;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    protected void init() {
        int cx = width / 2;
        int cy = height / 2;

        input = new EditBox(font, cx - 110, cy - 10, 220, 20,
                Component.literal("调试密码"));
        input.setMaxLength(200);
        input.setHint(Component.literal("输入调试密码后按回车或点确认"));
        input.setFocused(true);
        addRenderableWidget(input);

        addRenderableWidget(Button.builder(
                        Component.literal("确认"),
                        b -> tryUnlock())
                .bounds(cx - 110, cy + 20, 106, 20).build());

        addRenderableWidget(Button.builder(
                        Component.literal("取消"),
                        b -> close())
                .bounds(cx + 4, cy + 20, 106, 20).build());

        FileLogger lg = ChunkMapMod.getLogger();
        if (lg != null) lg.info("[Debug] 打开解锁界面");
    }

    private void tryUnlock() {
        String v = input.getValue() == null ? "" : input.getValue().trim();
        FileLogger lg = ChunkMapMod.getLogger();
        if (lg != null) lg.info("[Debug] 尝试解锁，输入长度=" + v.length());

        if (DebugKey.VALUE.equals(v)) {
            TileMapConfig old = ChunkMapMod.getConfig();
            if (old == null) old = ConfigLoader.load();

            // ★ 用 withDebug 保留其他调试开关
            TileMapConfig neu = old.withDebug(true, TileMapConfig.LogLevel.DEBUG);
            ConfigLoader.save(neu.validated());
            ChunkMapMod.reload();

            if (lg != null) lg.info("[Debug] 解锁成功，debugMode=true, logLevel=DEBUG");
            status = "✅ 已解锁调试模式";
            statusColor = 0xFF7EE787;
            close();
        } else {
            if (lg != null) lg.warn("[Debug] 密码错误");
            status = "❌ 密码错误";
            statusColor = 0xFFFF7B72;
            input.setValue("");
        }
    }

    private void close() {
        if (onClose != null) onClose.run();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0xC0101317);

        g.drawCenteredString(font, "解锁调试模式", width / 2, height / 2 - 46, 0xFFFFFFFF);
        g.drawCenteredString(font, "输入调试密码后，配置页面将显示调试分类",
                width / 2, height / 2 - 32, 0xFF8B949E);

        super.render(g, mouseX, mouseY, delta);

        if (!status.isEmpty()) {
            g.drawCenteredString(font, status, width / 2, height / 2 + 52, statusColor);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (e.key() == InputConstants.KEY_ESCAPE) {
            close();
            return true;
        }
        if (e.key() == InputConstants.KEY_RETURN) {
            tryUnlock();
            return true;
        }
        return super.keyPressed(e);
    }
}