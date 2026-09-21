package com.geek.chunkmap.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * 可点击的版本号文本组件。
 * 连续点击 N 次（默认 5 次）触发回调；超过 resetMs 未点击则重置计数。
 *
 * MC 1.21.11 的 AbstractWidget 没有 onClick(double, double)，
 * 点击要重写 mouseClicked(MouseButtonEvent, boolean)。
 */
public class VersionClickWidget extends AbstractWidget {

    public interface Handler { void onTriggered(); }

    private static final int  DEFAULT_REQUIRED = 5;
    private static final long DEFAULT_RESET_MS = 3000L;

    private final String text;
    private final Handler handler;
    private final int required;
    private final long resetMs;

    private int clickCount = 0;
    private long firstClickAt = 0L;

    public VersionClickWidget(int x, int y, String text, Handler handler) {
        this(x, y, text, handler, DEFAULT_REQUIRED, DEFAULT_RESET_MS);
    }

    public VersionClickWidget(int x, int y, String text, Handler handler,
                              int required, long resetMs) {
        super(x, y, widthOf(text), 12, Component.literal(text == null ? "" : text));
        this.text = text == null ? "" : text;
        this.handler = handler;
        this.required = Math.max(1, required);
        this.resetMs = Math.max(0L, resetMs);
    }

    private static int widthOf(String s) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.font != null) return mc.font.width(s == null ? "" : s);
        } catch (Throwable ignored) {}
        return 200;
    }

    public int getClickCount() { return clickCount; }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.font == null) return;

        int color = isHovered() ? 0xFFFFE082 : 0xFF8B949E;
        g.drawString(mc.font, text, getX(), getY(), color, true);

        if (clickCount > 0 && clickCount < required) {
            String hint = clickCount + "/" + required;
            g.drawString(mc.font, hint, getX() + getWidth() + 6, getY(), 0xFF6CB4FF, true);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return false;
        if (!isMouseOver(event.x(), event.y())) return false;

        long now = System.currentTimeMillis();
        if (now - firstClickAt > resetMs) clickCount = 0;
        firstClickAt = now;
        clickCount++;

        if (clickCount >= required) {
            clickCount = 0;
            if (handler != null) handler.onTriggered();
        }
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }
}