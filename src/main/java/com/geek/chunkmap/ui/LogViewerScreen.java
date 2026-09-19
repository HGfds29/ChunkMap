package com.geek.chunkmap.ui;

import com.geek.chunkmap.ChunkMapMod;
import com.geek.chunkmap.util.FileLogger;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LogViewerScreen extends Screen {

    private static final int TOPBAR_H  = 26;
    private static final int BOTBAR_H  = 20;
    private static final int LINE_H    = 10;
    private static final int MAX_LINES = 5000;

    private static final int C_BG         = 0xFF0F1216;
    private static final int C_BAR        = 0xE6101317;
    private static final int C_BAR_LINE   = 0xFF202429;
    private static final int C_TEXT       = 0xFFE6EAF0;
    private static final int C_TEXT_DIM   = 0xFF8B949E;
    private static final int C_ACCENT     = 0xFF4A9EFF;
    private static final int C_ACCENT_HI  = 0xFF6CB4FF;
    private static final int C_BTN_BG     = 0xFF1A1F26;
    private static final int C_BTN_HOVER  = 0xFF28323F;
    private static final int C_BTN_BORDER = 0xFF2A313B;
    private static final int C_BTN_BORDER_HOVER = 0xFF4A9EFF;

    private static final int C_TRACE = 0xFF6E7681;
    private static final int C_DEBUG = 0xFF8B949E;
    private static final int C_INFO  = 0xFFC9D1D9;
    private static final int C_WARN  = 0xFFE3B341;
    private static final int C_ERROR = 0xFFFF7B72;
    private static final int C_CONT  = 0xFF7A828C;

    private static final Pattern LINE_PATTERN = Pattern.compile(
            "^\\[(\\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\] \\[(TRACE|DEBUG|INFO|WARN|ERROR|OFF)\\] \\[([^\\]]+)\\] (.*)$"
    );

    private final Screen parent;
    private final List<LogEntry> lines = new ArrayList<>();
    private final List<Btn> buttons = new ArrayList<>();

    private int scroll = 0;
    private boolean autoScroll = true;
    private String status = "";

    public LogViewerScreen(Screen parent) {
        super(Component.literal("ChunkMap 日志"));
        this.parent = parent;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    protected void init() {
        reloadLog();
        layoutButtons();
    }

    private void layoutButtons() {
        buttons.clear();
        int y = (TOPBAR_H - 16) / 2;
        int x = width - 8;

        buttons.add(new Btn(x - 44, y, 44, 16, "返回", () -> minecraft.setScreen(parent)));
        x -= 44 + 4;
        buttons.add(new Btn(x - 66, y, 66, 16, "打开目录", this::openLogFolder));
        x -= 66 + 4;
        buttons.add(new Btn(x - 66, y, 66, 16, "复制全部", this::copyAll));
        x -= 66 + 4;
        buttons.add(new Btn(x - 44, y, 44, 16, "刷新", this::reloadLog));
    }

    private void reloadLog() {
        lines.clear();
        FileLogger logger = ChunkMapMod.getLogger();
        Path path = logger != null ? logger.getCurrentLogFile() : null;
        if (path == null || !Files.exists(path)) {
            status = "暂无日志文件";
            autoScroll = true;
            return;
        }
        try {
            List<String> raw = Files.readAllLines(path);
            int from = Math.max(0, raw.size() - MAX_LINES);
            FileLogger.Level last = FileLogger.Level.INFO;
            for (int i = from; i < raw.size(); i++) {
                String line = raw.get(i);
                Matcher m = LINE_PATTERN.matcher(line);
                if (m.matches()) {
                    FileLogger.Level lv;
                    try { lv = FileLogger.Level.valueOf(m.group(2)); }
                    catch (Throwable t) { lv = FileLogger.Level.INFO; }
                    last = lv;
                    lines.add(new LogEntry(lv, m.group(1), m.group(3), m.group(4), false));
                } else {
                    lines.add(new LogEntry(last, null, null, line, true));
                }
            }
            status = "共 " + raw.size() + " 行 · 显示最后 " + lines.size() + " 行";
            autoScroll = true;
        } catch (IOException e) {
            status = "读取失败: " + e.getMessage();
        }
    }

    private void openLogFolder() {
        try {
            FileLogger logger = ChunkMapMod.getLogger();
            Path p = logger != null ? logger.getCurrentLogFile() : null;
            Path dir = (p != null && p.getParent() != null) ? p.getParent() : Path.of("logs", "chunkmap");
            Files.createDirectories(dir);
            if (openUri(dir.toUri().toString())) {
                status = "已打开目录: " + dir.toAbsolutePath();
            } else {
                minecraft.keyboardHandler.setClipboard(dir.toAbsolutePath().toString());
                status = "打开失败，路径已复制到剪贴板: " + dir.toAbsolutePath();
            }
        } catch (Throwable t) {
            status = "打开失败: " + t.getMessage();
        }
    }

    private void copyAll() {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("=== ChunkMap 日志 ===\n");
            sb.append("版本: ").append(ChunkMapMod.VERSION).append('\n');
            sb.append("仓库: ").append(ChunkMapMod.GITHUB_URL).append('\n');
            sb.append("--------------------------------\n");
            for (LogEntry e : lines) sb.append(formatEntry(e)).append('\n');
            minecraft.keyboardHandler.setClipboard(sb.toString());
            status = "已复制 " + lines.size() + " 行到剪贴板";
        } catch (Throwable t) {
            status = "复制失败: " + t.getMessage();
        }
    }

    private static String formatEntry(LogEntry e) {
        if (e.continuation()) return e.text();
        return "[" + e.time() + "] [" + e.level() + "] [" + e.thread() + "] " + e.text();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, C_BG);

        int areaTop   = TOPBAR_H + 2;
        int areaBot   = height - BOTBAR_H - 2;
        int areaLeft  = 4;
        int areaRight = width - 6;

        int visible   = Math.max(1, (areaBot - areaTop) / LINE_H);
        int maxScroll = Math.max(0, lines.size() - visible);
        if (autoScroll) scroll = maxScroll;
        else scroll = Math.max(0, Math.min(scroll, maxScroll));

        g.enableScissor(areaLeft, areaTop, areaRight, areaBot);
        int y = areaTop;
        for (int i = scroll; i < lines.size(); i++) {
            if (y + LINE_H > areaBot) break;
            LogEntry e = lines.get(i);
            int color = colorOf(e.level(), e.continuation());
            g.drawString(font, formatEntry(e), areaLeft + 2, y, color, false);
            y += LINE_H;
        }
        g.disableScissor();

        if (maxScroll > 0) {
            int barX = width - 4;
            int trackH = areaBot - areaTop;
            int thumbH = Math.max(20, trackH * visible / Math.max(1, lines.size()));
            int thumbY = areaTop + (int) ((trackH - thumbH) * (scroll / (double) maxScroll));
            g.fill(barX, areaTop, barX + 2, areaBot, 0x30FFFFFF);
            g.fill(barX, thumbY, barX + 2, thumbY + thumbH, 0x90FFFFFF);
        }

        drawTopBar(g, mouseX, mouseY);
        drawBottomBar(g);
    }

    private void drawTopBar(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(0, 0, width, TOPBAR_H, C_BAR);
        g.fill(0, TOPBAR_H - 1, width, TOPBAR_H, C_BAR_LINE);
        g.drawString(font, "ChunkMap 日志", 10, (TOPBAR_H - 8) / 2, C_TEXT, true);

        for (Btn b : buttons) drawButton(g, b, b.contains(mouseX, mouseY));
    }

    private void drawBottomBar(GuiGraphics g) {
        int y = height - BOTBAR_H;
        g.fill(0, y, width, height, C_BAR);
        g.fill(0, y, width, y + 1, C_BAR_LINE);

        int ty = y + (BOTBAR_H - 8) / 2;
        g.drawString(font, status, 10, ty, C_TEXT_DIM, true);
        String hint = "滚轮滚动 · R 刷新 · C 复制 · ESC 返回";
        g.drawString(font, hint, width - font.width(hint) - 10, ty, C_TEXT_DIM, true);
    }

    private void drawButton(GuiGraphics g, Btn b, boolean hovered) {
        b.tick(hovered);
        float h = b.hover;
        int bg      = lerpColor(C_BTN_BG, C_BTN_HOVER, h);
        int border  = lerpColor(C_BTN_BORDER, C_BTN_BORDER_HOVER, h);
        int textCol = lerpColor(C_TEXT_DIM, C_TEXT, h);

        g.fill(b.x, b.y, b.x + b.w, b.y + b.h, bg);
        g.fill(b.x, b.y, b.x + b.w, b.y + 1, border);
        g.fill(b.x, b.y + b.h - 1, b.x + b.w, b.y + b.h, border);
        g.fill(b.x, b.y, b.x + 1, b.y + b.h, border);
        g.fill(b.x + b.w - 1, b.y, b.x + b.w, b.y + b.h, border);
        if (h > 0.01f) {
            g.fill(b.x + 1, b.y + 1, b.x + b.w - 1, b.y + 2,
                    lerpColor(C_ACCENT, C_ACCENT_HI, h));
        }

        int tw = font.width(b.label);
        g.drawString(font, b.label, b.x + (b.w - tw) / 2, b.y + (b.h - 8) / 2, textCol, false);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double hAmount, double vAmount) {
        if (vAmount == 0) return super.mouseScrolled(mouseX, mouseY, hAmount, vAmount);
        int delta = (int) -Math.signum(vAmount) * 3;

        int areaTop = TOPBAR_H + 2;
        int areaBot = height - BOTBAR_H - 2;
        int visible = Math.max(1, (areaBot - areaTop) / LINE_H);
        int maxScroll = Math.max(0, lines.size() - visible);

        scroll = Math.max(0, Math.min(maxScroll, scroll + delta));
        autoScroll = scroll >= maxScroll;
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (e.key() == InputConstants.KEY_ESCAPE) { minecraft.setScreen(parent); return true; }
        if (e.key() == InputConstants.KEY_R || e.key() == InputConstants.KEY_F5) {
            reloadLog(); return true;
        }
        if (e.key() == InputConstants.KEY_C) { copyAll(); return true; }
        if (e.key() == InputConstants.KEY_PAGEUP)   { scroll = Math.max(0, scroll - 10); autoScroll = false; return true; }
        if (e.key() == InputConstants.KEY_PAGEDOWN) { scroll += 10; autoScroll = false; return true; }
        if (e.key() == InputConstants.KEY_HOME)     { scroll = 0; autoScroll = false; return true; }
        if (e.key() == InputConstants.KEY_END)      { autoScroll = true; return true; }
        return super.keyPressed(e);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        for (Btn b : buttons) {
            if (b.contains(event.x(), event.y())) { b.action.run(); return true; }
        }
        return super.mouseClicked(event, bl);
    }

    private static boolean openUri(String url) {
        try {
            java.net.URI uri = java.net.URI.create(url);
            for (String cn : new String[]{
                    "net.minecraft.Util",
                    "net.minecraft.util.Util"
            }) {
                try {
                    Class<?> cls = Class.forName(cn);
                    Object platform = cls.getMethod("getPlatform").invoke(null);
                    platform.getClass()
                            .getMethod("openUri", java.net.URI.class)
                            .invoke(platform, uri);
                    return true;
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}

        try {
            java.net.URI uri = java.net.URI.create(url);
            if (java.awt.Desktop.isDesktopSupported()
                    && java.awt.Desktop.getDesktop()
                            .isSupported(java.awt.Desktop.Action.BROWSE)) {
                java.awt.Desktop.getDesktop().browse(uri);
                return true;
            }
        } catch (Throwable ignored) {}

        return false;
    }

    private static int colorOf(FileLogger.Level lv, boolean continuation) {
        if (continuation) return C_CONT;
        return switch (lv) {
            case TRACE -> C_TRACE;
            case DEBUG -> C_DEBUG;
            case INFO  -> C_INFO;
            case WARN  -> C_WARN;
            case ERROR -> C_ERROR;
            case OFF   -> C_TEXT;
        };
    }

    private static int lerpColor(int a, int b, float t) {
        if (t <= 0f) return a;
        if (t >= 1f) return b;
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int ra = (int) (aa + (ba - aa) * t);
        int rr = (int) (ar + (br - ar) * t);
        int rg = (int) (ag + (bg - ag) * t);
        int rb = (int) (ab + (bb - ab) * t);
        return (ra << 24) | (rr << 16) | (rg << 8) | rb;
    }

    private record LogEntry(FileLogger.Level level, String time, String thread,
                            String text, boolean continuation) {}

    private static class Btn {
        final int x, y, w, h;
        final String label;
        final Runnable action;
        float hover = 0f;

        Btn(int x, int y, int w, int h, String label, Runnable action) {
            this.x = x; this.y = y; this.w = w; this.h = h;
            this.label = label; this.action = action;
        }
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
        void tick(boolean hovered) {
            float target = hovered ? 1f : 0f;
            hover += (target - hover) * 0.25f;
            if (Math.abs(hover - target) < 0.005f) hover = target;
        }
    }
}