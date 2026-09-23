package com.geek.chunkmap.ui;

import com.geek.chunkmap.ChunkMapMod;
import com.geek.chunkmap.config.TileMapConfig;
import com.geek.chunkmap.tile.TileMapCache;
import com.geek.chunkmap.tile.TileMapStitcher;
import com.geek.chunkmap.util.FileLogger;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ForkJoinPool;

public class ChunkMapScreen extends Screen {

    private static final Identifier TEXTURE_ID =
            Identifier.fromNamespaceAndPath("chunkmap", "map_texture");

    private static final double MIN_ZOOM  = 0.25;
    private static final double MAX_ZOOM  = 8.0;
    private static final double ZOOM_STEP = 1.25;

    private static final int C_BG         = 0xFF0F1216;
    private static final int C_MAP_BG     = 0xFFB8B8B8;
    private static final int C_GRID_LIGHT = 0x18FFFFFF;
    private static final int C_GRID_DARK  = 0x16000000;
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
    private static final int C_PLAYER     = 0xFFFFFFFF;
    private static final int C_PLAYER_OUT = 0xFF000000;
    private static final int C_TOAST_BG   = 0xD01A1F26;
    private static final int C_TOAST_OK   = 0xFF7EE787;
    private static final int C_TOAST_ERR  = 0xFFFF7B72;

    private static final int C_STAR       = 0xFFD4A017;
    private static final int C_STAR_HI    = 0xFFFFE082;

    private static final int TOPBAR_H = 30;
    private static final int BOTBAR_H = 20;
    private static final int BTN_H    = 18;

    private static final long OPEN_ANIM_MS = 320;
    private static final long TOAST_MS = 5000;

    private final TileMapCache cache;
    private final FileLogger logger;

    private int tileRes = 32;
    private int lastTileRes = -1;

    private boolean uiAnim = true;

    private double zoom = 1.0;
    private float  displayZoom = 1.0f;
    private int offsetX = 0, offsetZ = 0;

    private DynamicTexture texture;
    private int texSize = 0;
    private int radius = 0;
    private long lastVersion = -1;
    private ResourceKey<Level> lastDim;

    private boolean dragging = false;
    private int lastPlayerChunkX = Integer.MIN_VALUE;
    private int lastPlayerChunkZ = Integer.MIN_VALUE;

    private long zoomToastUntil = 0;

    private String exportMsg = "";
    private long exportMsgStart = 0;
    private long exportMsgUntil = 0;
    private boolean exportSuccess = false;
    private boolean exporting = false;

    private boolean pendingReload = false;

    /** 最近一次纹理重建耗时（ms），调试覆盖层使用。 */
    private long lastRebuildMillis = 0;

    private final List<Btn> buttons = new ArrayList<>();
    private Btn btnExport, btnClose, btnFeedback, btnLogs, btnStar;

    private long openTime = 0;

    public ChunkMapScreen(TileMapCache cache, FileLogger logger) {
        super(Component.literal("ChunkMap"));
        this.cache = cache;
        this.logger = logger;
    }

    // ---------- 小工具：安全打日志 ----------

    private void logInfo(String msg) {
        if (logger != null) logger.info(msg);
    }

    private void logDebug(String msg) {
        if (logger != null && logger.isEnabled(FileLogger.Level.DEBUG)) logger.debug(msg);
    }

    private void logTrace(String msg) {
        if (logger != null && logger.isEnabled(FileLogger.Level.TRACE)) logger.trace(msg);
    }

    private void count(String key) {
        if (logger != null) logger.count(key);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private static int currentTileRes() {
        var c = ChunkMapMod.getConfig();
        return c != null ? c.tileResolution() : 32;
    }

    private static String currentOutputDir() {
        var c = ChunkMapMod.getConfig();
        return c != null ? c.outputDir() : "chunkmap-output";
    }

    /**
     * 取消边界：不再有固定的最大半径。
     * 以“最小缩放时恰好覆盖全屏”为基准计算 radius，
     * 并以 NativeImage 8192px 边长为硬上限防止 OOM。
     */
    private void recomputeLayout() {
        double minCellPx = Math.max(1.0, tileRes * MIN_ZOOM);
        int baseRadius = (int) Math.ceil(Math.max(width, height) / 2.0 / minCellPx) + 2;

        // NativeImage 安全上限：纹理边长 ≤ 8192 px
        int maxRadius = Math.max(1, 8192 / Math.max(1, tileRes) / 2 - 1);

        radius = Math.max(1, Math.min(baseRadius, maxRadius));
    }

    @Override
    protected void init() {
        lastVersion = -1;
        lastDim = null;
        lastTileRes = -1;
        openTime = System.currentTimeMillis();
        displayZoom = (float) zoom;

        tileRes = currentTileRes();
        recomputeLayout();

        var cfg = ChunkMapMod.getConfig();
        uiAnim = (cfg == null) || cfg.uiAnimation();
        if (!uiAnim) {
            openTime = 0;
            displayZoom = (float) zoom;
        }

        layoutButtons();

        logInfo("[UI] 打开地图界面 " + width + "x" + height
                + " tileRes=" + tileRes + " radius=" + radius
                + " uiAnim=" + uiAnim + " zoom=" + zoom);
        count("ui.map.open");
    }

    private void layoutButtons() {
        buttons.clear();
        int y = (TOPBAR_H - BTN_H) / 2;
        int x = width - 8;

        btnClose = new Btn(x - 22, y, 22, BTN_H, "X", this::onClose, false);
        x -= 22 + 4;

        btnExport = new Btn(x - 44, y, 44, BTN_H, "导出", this::exportStitchedMap, false);
        x -= 44 + 4;

        btnLogs = new Btn(x - 44, y, 44, BTN_H, "日志", this::openLogs, false);
        x -= 44 + 4;

        btnFeedback = new Btn(x - 44, y, 44, BTN_H, "反馈", this::openFeedback, false);
        x -= 44 + 4;

        btnStar = new Btn(x - 62, y, 62, BTN_H, "★ Star", this::openStarPage, true);

        buttons.add(btnClose);
        buttons.add(btnExport);
        buttons.add(btnLogs);
        buttons.add(btnFeedback);
        buttons.add(btnStar);
    }

    private void openFeedback() {
        logInfo("[UI] 打开反馈界面");
        count("ui.feedback.open");
        minecraft.setScreen(new FeedbackScreen(this));
    }

    private void openLogs() {
        logInfo("[UI] 打开日志界面");
        count("ui.logs.open");
        minecraft.setScreen(new LogViewerScreen(this));
    }

    private void openStarPage() {
        logInfo("[UI] 打开 GitHub Star 页");
        count("ui.star.click");
        openUrl(ChunkMapMod.GITHUB_URL, "已打开 GitHub，感谢 Star ⭐");
    }

    private void openUrl(String url, String okMsg) {
        logInfo("[UI] 尝试打开 URL: " + url);
        boolean ok = openUri(url);
        if (ok) {
            showExportMsg(okMsg, true);
            logInfo("[UI] 打开 URL 成功: " + url);
        } else {
            minecraft.keyboardHandler.setClipboard(url);
            showExportMsg("已复制链接到剪贴板: " + url, false);
            if (logger != null) logger.warn("[UI] 打开 URL 失败，已复制到剪贴板: " + url);
        }
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

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        if (pendingReload) {
            pendingReload = false;
            doReload();
        }

        int curRes = currentTileRes();
        if (curRes != tileRes) {
            logInfo("[UI] tileRes 变化 " + tileRes + " → " + curRes);
            tileRes = curRes;
            recomputeLayout();
            lastTileRes = -1;
        }

        float dt = Math.min(delta, 0.1f);

        float openProgress = 1f;
        if (uiAnim) {
            long elapsed = System.currentTimeMillis() - openTime;
            openProgress = Math.min(1f, elapsed / (float) OPEN_ANIM_MS);
        }

        if (uiAnim) {
            displayZoom += (float) ((zoom - displayZoom) * Math.min(1f, dt * 18f));
            if (Math.abs(displayZoom - zoom) < 0.005f) displayZoom = (float) zoom;
        } else {
            displayZoom = (float) zoom;
        }

        g.fill(0, 0, width, height, C_BG);

        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null) {
            String s = "未进入世界";
            g.drawString(font, s, (width - font.width(s)) / 2, height / 2, C_TEXT_DIM, true);
            return;
        }

        var cfg = ChunkMapMod.getConfig();
        boolean dbg = cfg != null && cfg.debugMode();
        boolean dbgForceRebuild = dbg && cfg.debugForceRebuild();
        boolean dbgWireframe    = dbg && cfg.debugWireframe();
        boolean dbgShowGrid     = !(dbg && cfg.debugNoGrid());

        ResourceKey<Level> dim = level.dimension();
        long v = cache.version();
        ChunkPos curChunk = new ChunkPos(player.blockPosition());
        boolean movedChunk = curChunk.x != lastPlayerChunkX || curChunk.z != lastPlayerChunkZ;
        boolean dimChanged = lastDim == null || !dim.equals(lastDim);
        boolean resChanged = lastTileRes != tileRes;

        if (texture == null || v != lastVersion || movedChunk || dimChanged || resChanged || dbgForceRebuild) {
            lastPlayerChunkX = curChunk.x;
            lastPlayerChunkZ = curChunk.z;
            lastDim = dim;
            lastTileRes = tileRes;
            long t0 = System.nanoTime();
            rebuildTexture(v, dim);
            lastRebuildMillis = (System.nanoTime() - t0) / 1_000_000;
        }

        if (texture != null) {
            if (!dbgWireframe) drawMapBody(g);
            if (dbgShowGrid) drawGrid(g);
            drawPlayerMarker(g, player, curChunk);

            // ★ 注意：record 访问器是方法，必须带 ()
            if (dbg && cfg.debugShowChunkBorders()) drawDebugChunkBorders(g, curChunk);
            if (dbg && cfg.debugShowPlayerChunk())  drawDebugPlayerChunk(g);
            if (dbg && cfg.debugShowOrigin())       drawDebugOrigin(g);
            if (dbg && cfg.debugShowTileCoords())   drawDebugTileCoords(g, curChunk);
            if (dbg && cfg.debugShowHighlight())    drawDebugHighlight(g, mouseX, mouseY);
        } else {
            String s = dbgWireframe ? "（线框模式：等待数据…）" : "正在渲染…";
            g.drawString(font, s, (width - font.width(s)) / 2, height / 2, C_TEXT_DIM, true);
        }

        if (uiAnim && openProgress < 1f) {
            int cover = (int) (0xFF * (1 - openProgress));
            g.fill(0, TOPBAR_H, width, height - BOTBAR_H, (cover << 24));
        }

        drawTopBar(g, player, dim, openProgress);
        drawBottomBar(g, dim, openProgress);
        drawZoomToast(g, openProgress);
        drawExportToast(g, openProgress);

        for (Btn b : buttons) {
            drawButton(g, b, b.contains(mouseX, mouseY), dt, openProgress);
        }

        if (dbg && cfg.debugOverlay()) {   // ★ 同样加 ()
            drawDebugOverlay(g, cfg);
        }
    }

    private double[] computeMapRect() {
        if (texSize <= 0) return new double[]{0, 0, 0, 0};
        double cx = width / 2.0 + offsetX;
        double cz = height / 2.0 + offsetZ;
        double w = texSize * zoom;
        double h = texSize * zoom;
        return new double[]{cx - w / 2.0, cz - h / 2.0, w, h};
    }

    private void drawMapBody(GuiGraphics g) {
        if (texture == null || texSize <= 0) return;
        double[] r = computeMapRect();

        g.enableScissor(0, TOPBAR_H, width, height - BOTBAR_H);
        int bgL = (int) Math.floor(r[0]);
        int bgT = (int) Math.floor(r[1]);
        int bgR = (int) Math.ceil(r[0] + r[2]);
        int bgB = (int) Math.ceil(r[1] + r[3]);
        g.fill(bgL, bgT, bgR, bgB, C_MAP_BG);

        var pose = g.pose();
        pose.pushMatrix();
        pose.translate((float) r[0], (float) r[1]);
        pose.scale((float) zoom, (float) zoom);

        g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE_ID,
                0, 0, 0.0F, 0.0F,
                texSize, texSize, texSize, texSize);

        pose.popMatrix();
        g.disableScissor();
    }

    private void drawGrid(GuiGraphics g) {
        if (texture == null || texSize <= 0) return;

        double cellF = tileRes * zoom;
        if (cellF < 4) return;

        double[] r = computeMapRect();
        double drawX = r[0], drawY = r[1], drawW = r[2], drawH = r[3];

        int top   = Math.max(TOPBAR_H, (int) Math.ceil(drawY));
        int bot   = Math.min(height - BOTBAR_H, (int) Math.floor(drawY + drawH));
        int left  = Math.max(0, (int) Math.ceil(drawX));
        int right = Math.min(width, (int) Math.floor(drawX + drawW));
        if (top >= bot || left >= right) return;

        int color = cellF >= 22 ? C_GRID_LIGHT : C_GRID_DARK;

        g.enableScissor(left, top, right, bot);

        int startI = (int) Math.floor((left - drawX) / cellF);
        int endI   = (int) Math.ceil((right - drawX) / cellF);
        for (int i = startI; i <= endI; i++) {
            int x = (int) Math.round(drawX + i * cellF);
            if (x < left || x >= right) continue;
            g.fill(x, top, x + 1, bot, color);
        }

        int startJ = (int) Math.floor((top - drawY) / cellF);
        int endJ   = (int) Math.ceil((bot - drawY) / cellF);
        for (int j = startJ; j <= endJ; j++) {
            int y = (int) Math.round(drawY + j * cellF);
            if (y < top || y >= bot) continue;
            g.fill(left, y, right, y + 1, color);
        }

        g.disableScissor();
    }

    private void drawPlayerMarker(GuiGraphics g, LocalPlayer player, ChunkPos chunk) {
        double[] r = computeMapRect();
        if (r[2] <= 0 || r[3] <= 0) return;

        double mapCx = r[0] + r[2] / 2.0;
        double mapCz = r[1] + r[3] / 2.0;
        double pxPerBlock = r[2] / ((2 * radius + 1) * 16.0);

        double worldCx = chunk.x * 16 + 8;
        double worldCz = chunk.z * 16 + 8;
        int px = (int) Math.round(mapCx + (player.getX() - worldCx) * pxPerBlock);
        int py = (int) Math.round(mapCz + (player.getZ() - worldCz) * pxPerBlock);

        if (py < TOPBAR_H || py >= height - BOTBAR_H) return;

        if (uiAnim) {
            long t = System.currentTimeMillis() % 1400L;
            float phase = t / 1400f;
            int glowR = (int) (8 + phase * 12);
            int glowA = (int) (0x55 * (1f - phase));
            if (glowA > 0) {
                g.fill(px - glowR, py - glowR, px + glowR, py + glowR,
                        (glowA << 24) | (C_ACCENT & 0xFFFFFF));
            }
        }

        g.fill(px - 6, py - 6, px + 7, py + 7, 0x30000000);
        g.fill(px - 5, py - 1, px + 6, py + 2, C_PLAYER_OUT);
        g.fill(px - 1, py - 5, px + 2, py + 6, C_PLAYER_OUT);
        g.fill(px - 4, py, px + 5, py + 1, C_PLAYER);
        g.fill(px, py - 4, px + 1, py + 5, C_PLAYER);
        g.fill(px - 1, py - 1, px + 2, py + 2, C_PLAYER_OUT);
        g.fill(px, py, px + 1, py + 1, C_ACCENT);
    }

    private void drawTopBar(GuiGraphics g, LocalPlayer player, ResourceKey<Level> dim, float alpha) {
        g.fill(0, 0, width, TOPBAR_H, withAlpha(C_BAR, alpha));
        g.fill(0, TOPBAR_H - 1, width, TOPBAR_H, withAlpha(C_BAR_LINE, alpha));

        int ty = (TOPBAR_H - 8) / 2;
        int tx = 10;
        g.drawString(font, "ChunkMap", tx, ty, withAlpha(C_TEXT, alpha), true);
        tx += font.width("ChunkMap") + 14;
        g.fill(tx - 6, ty, tx - 5, ty + 8, withAlpha(C_BAR_LINE, alpha));

        String pos = String.format("%s  ·  X %d  Y %d  Z %d",
                dim.identifier().getPath(),
                player.blockPosition().getX(),
                player.blockPosition().getY(),
                player.blockPosition().getZ());
        g.drawString(font, pos, tx, ty, withAlpha(C_TEXT_DIM, alpha), true);
    }

    private void drawBottomBar(GuiGraphics g, ResourceKey<Level> dim, float alpha) {
        int y = height - BOTBAR_H;
        g.fill(0, y, width, height, withAlpha(C_BAR, alpha));
        g.fill(0, y, width, y + 1, withAlpha(C_BAR_LINE, alpha));

        int ty = y + (BOTBAR_H - 8) / 2;
        int tx = 10;

        String zs = String.format("x%.2f", displayZoom);
        g.drawString(font, zs, tx, ty, withAlpha(C_ACCENT, alpha), true);
        tx += font.width(zs) + 14;

        int size = cache.size(dim);
        String sc = "区块 " + size;
        g.drawString(font, sc, tx, ty, withAlpha(C_TEXT_DIM, alpha), true);
        tx += font.width(sc) + 14;

        var disp = ChunkMapMod.getDispatcher();
        if (disp != null && disp.queueSize() > 0) {
            g.drawString(font, "队列 " + disp.queueSize(), tx, ty, withAlpha(C_TEXT_DIM, alpha), true);
        }

        String hint = "滚轮缩放 · 拖拽平移 · E 导出 · C 回中 · Z 1:1 · R 重载 · ESC 关闭";
        g.drawString(font, hint, width - font.width(hint) - 10, ty, withAlpha(C_TEXT_DIM, alpha), true);
    }

    private void drawZoomToast(GuiGraphics g, float alphaMul) {
        long now = System.currentTimeMillis();
        if (now > zoomToastUntil) return;

        float alpha;
        if (uiAnim) {
            long appeared = 900 - (zoomToastUntil - now);
            float a = Math.min(1f, appeared / 200f);
            float b = Math.min(1f, (zoomToastUntil - now) / 300f);
            alpha = Math.min(a, b) * alphaMul;
        } else {
            alpha = alphaMul;
        }
        if (alpha <= 0.01f) return;

        String s = String.format("x%.2f", displayZoom);
        int tw = font.width(s);
        int bx = width / 2 - tw / 2 - 8;
        int by = height / 2 - 42;

        g.fill(bx + 1, by + 1, bx + tw + 13, by + 17, withAlpha(0x40000000, alpha));
        g.fill(bx, by, bx + tw + 12, by + 16, withAlpha(0xD0000000, alpha));
        g.fill(bx, by, bx + 2, by + 16, withAlpha(C_ACCENT, alpha));
        g.drawString(font, s, bx + 8, by + 4, withAlpha(C_TEXT, alpha), true);
    }

    private void drawExportToast(GuiGraphics g, float alphaMul) {
        long now = System.currentTimeMillis();
        if (exportMsg.isEmpty() || now > exportMsgUntil) return;

        float alpha;
        int slideIn = 0;
        if (uiAnim) {
            long elapsed = now - exportMsgStart;
            long remaining = exportMsgUntil - now;
            float fadeIn = Math.min(1f, elapsed / 220f);
            float fadeOut = Math.min(1f, remaining / 400f);
            alpha = Math.min(fadeIn, fadeOut) * alphaMul;
            slideIn = (int) ((1 - fadeIn) * 24);
        } else {
            alpha = alphaMul;
        }
        if (alpha <= 0.01f) return;

        int tw = font.width(exportMsg);
        int bx = width - tw - 20 + slideIn;
        int by = height - BOTBAR_H - 28;

        g.fill(bx - 8, by - 5, width - 8 + slideIn, by + 17, withAlpha(0x40000000, alpha));
        g.fill(bx - 9, by - 6, width - 9 + slideIn, by + 16, withAlpha(C_TOAST_BG, alpha));

        int accent = exportSuccess ? C_TOAST_OK : (exporting ? C_ACCENT : C_TOAST_ERR);
        g.fill(bx - 9, by - 6, bx - 7, by + 16, withAlpha(accent, alpha));

        int color = exportSuccess ? C_TOAST_OK : (exporting ? C_TEXT : C_TOAST_ERR);
        g.drawString(font, exportMsg, bx, by + 2, withAlpha(color, alpha), true);
    }

    private void drawButton(GuiGraphics g, Btn b, boolean hovered, float dt, float openProgress) {
        b.tick(hovered, dt, uiAnim);
        float h = b.hover;
        float alpha = openProgress;

        int bg      = lerpColor(C_BTN_BG, C_BTN_HOVER, h);
        int border  = b.star
                ? lerpColor(C_STAR, C_STAR_HI, h)
                : lerpColor(C_BTN_BORDER, C_BTN_BORDER_HOVER, h);
        int textCol = b.star
                ? lerpColor(C_STAR, C_STAR_HI, h)
                : lerpColor(C_TEXT_DIM, C_TEXT, h);

        g.fill(b.x + 1, b.y + 1, b.x + b.w + 1, b.y + b.h + 1, withAlpha(0x40000000, alpha));
        g.fill(b.x, b.y, b.x + b.w, b.y + b.h, withAlpha(bg, alpha));

        g.fill(b.x, b.y, b.x + b.w, b.y + 1, withAlpha(border, alpha));
        g.fill(b.x, b.y + b.h - 1, b.x + b.w, b.y + b.h, withAlpha(border, alpha));
        g.fill(b.x, b.y, b.x + 1, b.y + b.h, withAlpha(border, alpha));
        g.fill(b.x + b.w - 1, b.y, b.x + b.w, b.y + b.h, withAlpha(border, alpha));

        int topBarH = 1 + (int) Math.round(h * 2f);
        if (topBarH > 0) {
            int accent = b.star
                    ? lerpColor(C_STAR, C_STAR_HI, h)
                    : lerpColor(C_ACCENT, C_ACCENT_HI, h);
            g.fill(b.x + 1, b.y + 1, b.x + b.w - 1, b.y + 1 + topBarH,
                    withAlpha(accent, alpha));
        }

        int tw = font.width(b.label);
        int tx = b.x + (b.w - tw) / 2;
        int ty = b.y + (b.h - 8) / 2;
        g.drawString(font, b.label, tx, ty, withAlpha(textCol, alpha), false);
    }

    // ==================== 调试绘制 ====================

    private void drawDebugOverlay(GuiGraphics g, TileMapConfig cfg) {
        List<String> lines = new ArrayList<>();
        lines.add("§b[DEBUG] §7" + ChunkMapMod.VERSION);
        lines.add("tileRes=" + tileRes + "  radius=" + radius
                + "  zoom=" + String.format("%.2f", displayZoom));

        if (cfg.debugShowFps()) {
            lines.add("FPS: " + Minecraft.getInstance().getFps());
        }
        if (cfg.debugShowMemory()) {
            Runtime rt = Runtime.getRuntime();
            long used = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
            long max  = rt.maxMemory() / 1024 / 1024;
            lines.add("Mem: " + used + " / " + max + " MB");
        }
        if (cfg.debugShowQueue()) {
            var disp = ChunkMapMod.getDispatcher();
            if (disp != null) {
                lines.add("queue=" + disp.queueSize()
                        + "  cache=" + cache.size(lastDim == null ? Level.OVERWORLD : lastDim));
            }
        }
        if (cfg.debugShowTimings()) {
            lines.add("rebuild: " + lastRebuildMillis + " ms");
        }
        if (cfg.debugForceRebuild()) lines.add("§e每帧强制重建");
        if (cfg.debugWireframe())    lines.add("§e线框模式");
        if (cfg.debugNoGrid())       lines.add("§e网格已禁用");

        int lineH = 10;
        int pad = 4;
        int w = 0;
        for (String s : lines) w = Math.max(w, font.width(stripColor(s)));
        w += pad * 2;

        int x = 8;
        int y = TOPBAR_H + 6;
        int h = lines.size() * lineH + pad * 2;

        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0x50000000);
        g.fill(x, y, x + w, y + h, 0xC0101317);
        g.fill(x, y, x + 2, y + h, C_ACCENT);

        int ty = y + pad;
        for (String s : lines) {
            g.drawString(font, s, x + pad, ty, C_TEXT, false);
            ty += lineH;
        }
    }

    private void drawDebugChunkBorders(GuiGraphics g, ChunkPos center) {
        double[] r = computeMapRect();
        if (r[2] <= 0 || r[3] <= 0) return;

        double cell = tileRes * zoom;
        if (cell < 4) return;

        g.enableScissor(0, TOPBAR_H, width, height - BOTBAR_H);
        int top = Math.max(TOPBAR_H, (int) Math.ceil(r[1]));
        int bot = Math.min(height - BOTBAR_H, (int) Math.floor(r[1] + r[3]));
        int left = Math.max(0, (int) Math.ceil(r[0]));
        int right = Math.min(width, (int) Math.floor(r[0] + r[2]));
        if (top >= bot || left >= right) {
            g.disableScissor();
            return;
        }

        int color = 0x80FF00FF;

        int startI = (int) Math.floor((left - r[0]) / cell);
        int endI   = (int) Math.ceil((right - r[0]) / cell);
        for (int i = startI; i <= endI; i++) {
            int x = (int) Math.round(r[0] + i * cell);
            if (x < left || x >= right) continue;
            g.fill(x, top, x + 1, bot, color);
        }

        int startJ = (int) Math.floor((top - r[1]) / cell);
        int endJ   = (int) Math.ceil((bot - r[1]) / cell);
        for (int j = startJ; j <= endJ; j++) {
            int y = (int) Math.round(r[1] + j * cell);
            if (y < top || y >= bot) continue;
            g.fill(left, y, right, y + 1, color);
        }

        g.disableScissor();
    }

    /** 玩家所在区块 = 纹理中心瓦片（rebuildTexture 以玩家为中心）。 */
    private void drawDebugPlayerChunk(GuiGraphics g) {
        double[] r = computeMapRect();
        if (r[2] <= 0 || r[3] <= 0) return;

        double cell = tileRes * zoom;
        if (cell <= 0) return;

        double mapCx = r[0] + r[2] / 2.0;
        double mapCz = r[1] + r[3] / 2.0;

        double x0 = mapCx - cell / 2.0;
        double z0 = mapCz - cell / 2.0;

        int ix = (int) Math.round(x0);
        int iz = (int) Math.round(z0);
        int ex = (int) Math.round(x0 + cell);
        int ez = (int) Math.round(z0 + cell);

        g.enableScissor(0, TOPBAR_H, width, height - BOTBAR_H);
        g.fill(ix, iz, ex, iz + 2, 0xC0FFEA00);
        g.fill(ix, ez - 2, ex, ez, 0xC0FFEA00);
        g.fill(ix, iz, ix + 2, ez, 0xC0FFEA00);
        g.fill(ex - 2, iz, ex, ez, 0xC0FFEA00);
        g.disableScissor();
    }

    private void drawDebugOrigin(GuiGraphics g) {
        double[] r = computeMapRect();
        if (r[2] <= 0 || r[3] <= 0) return;
        int cx = (int) Math.round(r[0] + r[2] / 2.0);
        int cy = (int) Math.round(r[1] + r[3] / 2.0);
        g.enableScissor(0, TOPBAR_H, width, height - BOTBAR_H);
        g.fill(cx - 8, cy - 1, cx + 9, cy + 1, 0xFFFF3B3B);
        g.fill(cx - 1, cy - 8, cx + 1, cy + 9, 0xFFFF3B3B);
        g.disableScissor();
    }

    private void drawDebugTileCoords(GuiGraphics g, ChunkPos center) {
        double[] r = computeMapRect();
        if (r[2] <= 0 || r[3] <= 0) return;

        double cell = tileRes * zoom;
        if (cell < 32) return;

        g.enableScissor(0, TOPBAR_H, width, height - BOTBAR_H);

        int top = Math.max(TOPBAR_H, (int) Math.ceil(r[1]));
        int bot = Math.min(height - BOTBAR_H, (int) Math.floor(r[1] + r[3]));
        int left = Math.max(0, (int) Math.ceil(r[0]));
        int right = Math.min(width, (int) Math.floor(r[0] + r[2]));

        int startI = (int) Math.floor((left - r[0]) / cell);
        int endI   = (int) Math.ceil((right - r[0]) / cell);
        int startJ = (int) Math.floor((top - r[1]) / cell);
        int endJ   = (int) Math.ceil((bot - r[1]) / cell);

        int baseCX = center.x - radius;
        int baseCZ = center.z - radius;

        for (int i = startI; i <= endI; i++) {
            for (int j = startJ; j <= endJ; j++) {
                int x = (int) Math.round(r[0] + i * cell);
                int y = (int) Math.round(r[1] + j * cell);
                if (x < left || x >= right || y < top || y >= bot) continue;

                int ccx = baseCX + i;
                int ccz = baseCZ + j;
                String s = ccx + "," + ccz;
                g.fill(x + 1, y + 1, x + 1 + font.width(s) + 2, y + 11, 0xA0000000);
                g.drawString(font, s, x + 2, y + 2, 0xFFFFEA00, false);
            }
        }

        g.disableScissor();
    }

    private void drawDebugHighlight(GuiGraphics g, int mouseX, int mouseY) {
        if (mouseX < 0 || mouseY < TOPBAR_H || mouseY > height - BOTBAR_H) return;
        double[] r = computeMapRect();
        if (r[2] <= 0 || r[3] <= 0) return;

        double cell = tileRes * zoom;
        if (cell <= 0) return;

        int i = (int) Math.floor((mouseX - r[0]) / cell);
        int j = (int) Math.floor((mouseY - r[1]) / cell);

        int x  = (int) Math.round(r[0] + i * cell);
        int y  = (int) Math.round(r[1] + j * cell);
        int ex = (int) Math.round(x + cell);
        int ey = (int) Math.round(y + cell);

        g.enableScissor(0, TOPBAR_H, width, height - BOTBAR_H);
        g.fill(x, y, ex, y + 1, 0xFFFFFFFF);
        g.fill(x, ey - 1, ex, ey, 0xFFFFFFFF);
        g.fill(x, y, x + 1, ey, 0xFFFFFFFF);
        g.fill(ex - 1, y, ex, ey, 0xFFFFFFFF);
        g.disableScissor();
    }

    private static String stripColor(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '§' && i + 1 < s.length()) { i++; continue; }
            sb.append(c);
        }
        return sb.toString();
    }

    // ==================== 纹理重建 ====================

    private void rebuildTexture(long version, ResourceKey<Level> dim) {
        LocalPlayer player = minecraft.player;
        if (player == null) return;
        ChunkPos center = new ChunkPos(player.blockPosition());

        int size = (radius * 2 + 1) * tileRes;

        if (logger != null && logger.isEnabled(FileLogger.Level.DEBUG)) {
            logger.debug("[UI] rebuildTexture dim=" + dim.identifier()
                    + " center=" + center
                    + " radius=" + radius
                    + " tileRes=" + tileRes
                    + " size=" + size
                    + " version=" + version);
        }
        count("ui.texture.rebuild");

        long t0 = System.nanoTime();
        int drawnTiles = 0;
        int skippedTiles = 0;

        NativeImage img = new NativeImage(NativeImage.Format.RGBA, size, size, true);
        boolean handedOff = false;
        try {
            final int expectedLen = tileRes * tileRes;

            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    ChunkPos cp = new ChunkPos(center.x + dx, center.z + dz);

                    // ★ 传 tileRes 作为期望分辨率 —— 缓存会自动拒绝尺寸不匹配的瓦片。
                    int[] pixels = cache.get(dim, cp, tileRes);
                    if (pixels == null) continue;

                    // ★ 双保险：即使缓存实现变了，也在这里再校验一次长度，
                    //   避免出现 j*tileRes+i 越界（Index 256 out of bounds）。
                    if (pixels.length != expectedLen) {
                        skippedTiles++;
                        if (logger != null && logger.isEnabled(FileLogger.Level.DEBUG)) {
                            logger.debug("[UI] 跳过分辨率不匹配的瓦片 " + cp
                                    + " len=" + pixels.length + " 期望=" + expectedLen);
                        }
                        continue;
                    }

                    drawnTiles++;
                    int dstX = (dx + radius) * tileRes;
                    int dstZ = (dz + radius) * tileRes;
                    for (int i = 0; i < tileRes; i++) {
                        for (int j = 0; j < tileRes; j++) {
                            int argb = pixels[j * tileRes + i];
                            if ((argb >>> 24) == 0) continue;
                            img.setPixelABGR(dstX + i, dstZ + j, toABGR(argb));
                        }
                    }
                }
            }

            if (texture != null) {
                minecraft.getTextureManager().release(TEXTURE_ID);
                texture = null;
            }

            texture = new DynamicTexture(() -> "chunkmap", img);
            texture.upload();
            minecraft.getTextureManager().register(TEXTURE_ID, texture);
            handedOff = true;

            texSize = size;
            lastVersion = version;

            if (logger != null && logger.isEnabled(FileLogger.Level.DEBUG)) {
                long ms = (System.nanoTime() - t0) / 1_000_000;
                logger.debug("[UI] rebuildTexture 完成 tiles=" + drawnTiles
                        + " skipped=" + skippedTiles
                        + " 耗时=" + ms + "ms");
            }
        } finally {
            if (!handedOff) img.close();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double hAmount, double vAmount) {
        if (vAmount == 0) return super.mouseScrolled(mouseX, mouseY, hAmount, vAmount);

        double factor = vAmount > 0 ? ZOOM_STEP : 1.0 / ZOOM_STEP;
        double newZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom * factor));
        if (Math.abs(newZoom - zoom) < 1e-6) return true;

        logTrace("[UI] 缩放 " + zoom + " → " + newZoom);
        count("ui.zoom.scroll");
        zoom = newZoom;
        zoomToastUntil = System.currentTimeMillis() + 900;
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        for (Btn b : buttons) {
            if (b.contains(event.x(), event.y())) {
                logDebug("[UI] 按钮点击: " + b.label);
                count("ui.button." + b.label);
                b.action.run();
                return true;
            }
        }
        if (event.y() < TOPBAR_H || event.y() > height - BOTBAR_H) {
            return super.mouseClicked(event, bl);
        }
        if (event.button() == 0) {
            dragging = true;
            logTrace("[UI] 开始拖动 offset=(" + offsetX + "," + offsetZ + ")");
            return true;
        }
        return super.mouseClicked(event, bl);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (dragging && event.button() == 0) {
            offsetX += (int) Math.round(deltaX);
            offsetZ += (int) Math.round(deltaY);
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0) {
            if (dragging) logTrace("[UI] 结束拖动 offset=(" + offsetX + "," + offsetZ + ")");
            dragging = false;
        }
        return super.mouseReleased(event);
    }

    // ==================== 键盘 ====================
    @Override
    public boolean keyPressed(KeyEvent e) {
        if (e.key() == InputConstants.KEY_ESCAPE) {
            logInfo("[UI] 按键 ESC → 关闭地图");
            count("ui.key.esc");
            onClose();
            return true;
        }
        if (e.key() == InputConstants.KEY_E) {
            logInfo("[UI] 按键 E → 导出");
            count("ui.key.export");
            exportStitchedMap();
            return true;
        }
        if (e.key() == InputConstants.KEY_C) {
            logInfo("[UI] 按键 C → 回中");
            count("ui.key.center");
            offsetX = 0;
            offsetZ = 0;
            return true;
        }
        if (e.key() == InputConstants.KEY_Z) {
            logInfo("[UI] 按键 Z → 复位缩放 1:1");
            count("ui.key.zoom_reset");
            zoom = 1.0;
            zoomToastUntil = System.currentTimeMillis() + 900;
            return true;
        }
        if (e.key() == InputConstants.KEY_R) {
            logInfo("[UI] 按键 R → 触发重载");
            count("ui.key.reload");
            pendingReload = true;
            return true;
        }
        return super.keyPressed(e);
    }

    private void doReload() {
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null) return;

        long t0 = System.currentTimeMillis();
        logInfo("[UI] === 重载开始 ===");

        ChunkMapMod.reload();

        int newRes = currentTileRes();
        if (newRes != tileRes) {
            tileRes = newRes;
            recomputeLayout();
            lastTileRes = -1;
        }

        var dispatcher = ChunkMapMod.getDispatcher();
        if (dispatcher == null) {
            logInfo("[UI] dispatcher 为 null，重载中止");
            return;
        }
        dispatcher.getCache().clear();

        if (texture != null) {
            minecraft.getTextureManager().release(TEXTURE_ID);
            texture = null;
        }
        lastVersion = -1;
        lastDim = null;
        lastTileRes = -1;

        ChunkPos center = player.chunkPosition();
        int r = 10;
        int count = 0;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                dispatcher.enqueueRender(new ChunkPos(center.x + dx, center.z + dz));
                count++;
            }
        }

        long dt = System.currentTimeMillis() - t0;
        showExportMsg("重载中… 入队 " + count + " 区块 (" + dt + "ms)", true);
        logInfo("[UI] === 重载完成，入队 " + count + " 区块，耗时 " + dt + "ms ===");
    }

    // ==================== 导出 ====================
    private void exportStitchedMap() {
        ClientLevel level = minecraft.level;
        if (level == null) {
            showExportMsg("未进入世界", false);
            logInfo("[UI] 导出取消：未进入世界");
            return;
        }
        if (exporting) {
            showExportMsg("导出进行中...", false);
            logInfo("[UI] 导出取消：已有导出进行中");
            return;
        }

        exporting = true;
        final Identifier dim = level.dimension().identifier();
        final String dir = currentOutputDir();
        final int res = currentTileRes();
        showExportMsg("正在导出...", false);

        logInfo("[UI] 导出开始 dim=" + dim + " dir=" + dir + " res=" + res);
        count("ui.export.start");

        CompletableFuture.supplyAsync(() -> {
            try {
                return TileMapStitcher.stitch(dir, dim, res);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, ForkJoinPool.commonPool()).whenComplete((out, err) -> {
            minecraft.execute(() -> {
                exporting = false;
                if (err != null) {
                    Throwable cause = err.getCause() != null ? err.getCause() : err;
                    showExportMsg("导出失败: " + cause.getMessage(), false);
                    if (logger != null) logger.error("[UI] 导出异常", cause);
                    count("ui.export.fail");
                } else if (out == null) {
                    showExportMsg("没有可拼接的瓦片", false);
                    logInfo("[UI] 导出结束：无瓦片");
                    count("ui.export.empty");
                } else {
                    showExportMsg("已导出: " + out.getFileName(), true);
                    logInfo("[UI] 导出完成 " + out.toAbsolutePath());
                    count("ui.export.ok");
                }
            });
        });
    }

    private void showExportMsg(String msg, boolean success) {
        exportMsg = msg;
        exportMsgStart = System.currentTimeMillis();
        exportMsgUntil = exportMsgStart + TOAST_MS;
        exportSuccess = success;
    }

    @Override
    public void onClose() {
        logInfo("[UI] 关闭地图界面");
        count("ui.map.close");
        releaseTexture();
        super.onClose();
    }

    @Override
    public void removed() {
        logDebug("[UI] map removed()");
        releaseTexture();
        super.removed();
    }

    private void releaseTexture() {
        if (texture != null) {
            minecraft.getTextureManager().release(TEXTURE_ID);
            texture = null;
            logDebug("[UI] 释放地图纹理");
        }
    }

    private static int toABGR(int argb) {
        return (argb & 0xFF00FF00) | ((argb & 0xFF) << 16) | ((argb >>> 16) & 0xFF);
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

    private static int withAlpha(int color, float alpha) {
        if (alpha >= 1f) return color;
        int a = (int) (((color >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, alpha)));
        return (a << 24) | (color & 0xFFFFFF);
    }

    private static class Btn {
        final int x, y, w, h;
        final String label;
        final Runnable action;
        final boolean star;
        float hover = 0f;

        Btn(int x, int y, int w, int h, String label, Runnable action, boolean star) {
            this.x = x; this.y = y; this.w = w; this.h = h;
            this.label = label; this.action = action; this.star = star;
        }

        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }

        void tick(boolean hovered, float dt, boolean animEnabled) {
            float target = hovered ? 1f : 0f;
            if (!animEnabled) {
                hover = target;
                return;
            }
            hover += (target - hover) * Math.min(1f, dt * 18f);
            if (Math.abs(hover - target) < 0.005f) hover = target;
        }
    }
}