package com.geek.chunkmap.ui;

import com.geek.chunkmap.ChunkMapMod;
import com.geek.chunkmap.tile.TileMapCache;
import com.geek.chunkmap.tile.TileMapStitcher;
import com.geek.chunkmap.util.FileLogger;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
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

    private static final int TOPBAR_H = 30;
    private static final int BOTBAR_H = 20;
    private static final int BTN_H    = 18;

    private static final long OPEN_ANIM_MS = 320;
    private static final long TOAST_MS = 5000;

    private final TileMapCache cache;
    private final FileLogger logger;

    // 动态字段：重载配置后会自动同步
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

    /** R 键触发的重载标记：下一帧 render 开头执行，避免在 keyPressed 里递归换屏幕。 */
    private boolean pendingReload = false;

    private final List<Btn> buttons = new ArrayList<>();
    private Btn btnExport, btnCenter, btnReset, btnClose;

    private long openTime = 0;

    public ChunkMapScreen(TileMapCache cache, FileLogger logger) {
        super(Component.literal("ChunkMap"));
        this.cache = cache;
        this.logger = logger;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ---------- 动态读取配置 ----------

    private static int currentTileRes() {
        var c = ChunkMapMod.getConfig();
        return c != null ? c.tileResolution() : 32;
    }

    private static String currentOutputDir() {
        var c = ChunkMapMod.getConfig();
        return c != null ? c.outputDir() : "chunkmap-output";
    }

    private void recomputeLayout() {
        int baseRadius = (int) Math.ceil(Math.max(width, height) / 2.0 / tileRes) + 2;
        radius = Math.max(1, Math.min(baseRadius, 32));
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

        if (logger != null) {
            logger.info("[UI] 打开 screen=" + width + "x" + height
                    + " tileRes=" + tileRes + " radius=" + radius + " uiAnim=" + uiAnim);
        }
    }

    private void layoutButtons() {
        buttons.clear();
        int y = (TOPBAR_H - BTN_H) / 2;
        int x = width - 8;

        btnClose = new Btn(x - 22, y, 22, BTN_H, "X", this::onClose);
        x -= 22 + 4;

        btnReset = new Btn(x - 24, y, 24, BTN_H, "1:1", () -> {
            zoom = 1.0;
            offsetX = 0;
            offsetZ = 0;
            zoomToastUntil = System.currentTimeMillis() + 900;
        });
        x -= 24 + 4;

        btnCenter = new Btn(x - 60, y, 60, BTN_H, "回到玩家", () -> {
            offsetX = 0;
            offsetZ = 0;
        });
        x -= 60 + 4;

        btnExport = new Btn(x - 44, y, 44, BTN_H, "导出", this::exportStitchedMap);

        buttons.add(btnClose);
        buttons.add(btnReset);
        buttons.add(btnCenter);
        buttons.add(btnExport);
    }

    // ---------- 渲染 ----------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        // 延迟重载：在本帧开头执行一次，避免在 keyPressed 里递归触发
        if (pendingReload) {
            pendingReload = false;
            doReload();
        }

        // 每帧同步 tileRes（用户可能刚重载过配置，改了 tileResolution）
        int curRes = currentTileRes();
        if (curRes != tileRes) {
            tileRes = curRes;
            recomputeLayout();
            lastTileRes = -1; // 强制下一帧重建纹理
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

        ResourceKey<Level> dim = level.dimension();
        long v = cache.version();
        ChunkPos curChunk = new ChunkPos(player.blockPosition());
        boolean movedChunk = curChunk.x != lastPlayerChunkX || curChunk.z != lastPlayerChunkZ;
        boolean dimChanged = lastDim == null || !dim.equals(lastDim);
        boolean resChanged = lastTileRes != tileRes;

        if (texture == null || v != lastVersion || movedChunk || dimChanged || resChanged) {
            lastPlayerChunkX = curChunk.x;
            lastPlayerChunkZ = curChunk.z;
            lastDim = dim;
            lastTileRes = tileRes;
            rebuildTexture(v, dim);
        }

        if (texture != null) {
            drawMapBody(g);
            drawGrid(g);
            drawPlayerMarker(g, player, curChunk);
        } else {
            String s = "正在渲染…";
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
    }

    private void drawMapBody(GuiGraphics g) {
        if (texture == null) return;

        int centerPx = (int) (width / 2.0 + offsetX);
        int centerPz = (int) (height / 2.0 + offsetZ);

        int drawW = (int) Math.round(texSize * zoom);
        int drawH = (int) Math.round(texSize * zoom);
        int drawX = centerPx - drawW / 2;
        int drawY = centerPz - drawH / 2;

        g.enableScissor(0, TOPBAR_H, width, height - BOTBAR_H);
        g.fill(drawX, drawY, drawX + drawW, drawY + drawH, C_MAP_BG);

        var pose = g.pose();
        pose.pushMatrix();
        pose.translate((float) drawX, (float) drawY);
        pose.scale((float) zoom, (float) zoom);

        g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE_ID,
                0, 0,
                0.0F, 0.0F,
                texSize, texSize,
                texSize, texSize);

        pose.popMatrix();
        g.disableScissor();
    }

    private void drawGrid(GuiGraphics g) {
        if (texture == null) return;
        double cellF = tileRes * zoom;
        if (cellF < 4) return;

        int centerPx = (int) (width / 2.0 + offsetX);
        int centerPz = (int) (height / 2.0 + offsetZ);
        int drawW = (int) Math.round(texSize * zoom);
        int drawH = (int) Math.round(texSize * zoom);
        int drawX = centerPx - drawW / 2;
        int drawY = centerPz - drawH / 2;

        int top = Math.max(TOPBAR_H, drawY);
        int bot = Math.min(height - BOTBAR_H, drawY + drawH);
        int left = Math.max(0, drawX);
        int right = Math.min(width, drawX + drawW);
        if (top >= bot || left >= right) return;

        int cell = (int) Math.round(cellF);
        int color = cell >= 22 ? C_GRID_LIGHT : C_GRID_DARK;

        g.enableScissor(left, top, right, bot);
        for (int x = drawX; x <= drawX + drawW; x += cell) {
            if (x < left || x >= right) continue;
            g.fill(x, top, x + 1, bot, color);
        }
        for (int y = drawY; y <= drawY + drawH; y += cell) {
            if (y < top || y >= bot) continue;
            g.fill(left, y, right, y + 1, color);
        }
        g.disableScissor();
    }

    private void drawPlayerMarker(GuiGraphics g, LocalPlayer player, ChunkPos chunk) {
        int cx = (int) (width / 2.0 + offsetX);
        int cy = (int) (height / 2.0 + offsetZ);

        double ccx = chunk.x * 16 + 8;
        double ccz = chunk.z * 16 + 8;
        double pxPerBlock = tileRes * zoom / 16.0;

        int px = (int) Math.round(cx + (player.getX() - ccx) * pxPerBlock);
        int py = (int) Math.round(cy + (player.getZ() - ccz) * pxPerBlock);

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

        String hint = "滚轮缩放 · 拖拽平移 · E 导出 · C 回中 · R 重载 · ESC 关闭";
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
        int border  = lerpColor(C_BTN_BORDER, C_BTN_BORDER_HOVER, h);
        int textCol = lerpColor(C_TEXT_DIM, C_TEXT, h);

        g.fill(b.x + 1, b.y + 1, b.x + b.w + 1, b.y + b.h + 1, withAlpha(0x40000000, alpha));
        g.fill(b.x, b.y, b.x + b.w, b.y + b.h, withAlpha(bg, alpha));

        g.fill(b.x, b.y, b.x + b.w, b.y + 1, withAlpha(border, alpha));
        g.fill(b.x, b.y + b.h - 1, b.x + b.w, b.y + b.h, withAlpha(border, alpha));
        g.fill(b.x, b.y, b.x + 1, b.y + b.h, withAlpha(border, alpha));
        g.fill(b.x + b.w - 1, b.y, b.x + b.w, b.y + b.h, withAlpha(border, alpha));

        int topBarH = 1 + (int) Math.round(h * 2f);
        if (topBarH > 0) {
            g.fill(b.x + 1, b.y + 1, b.x + b.w - 1, b.y + 1 + topBarH,
                    withAlpha(lerpColor(C_ACCENT, C_ACCENT_HI, h), alpha));
        }

        int tw = font.width(b.label);
        int tx = b.x + (b.w - tw) / 2;
        int ty = b.y + (b.h - 8) / 2;
        g.drawString(font, b.label, tx, ty, withAlpha(textCol, alpha), false);
    }

    // ---------- 纹理重建 ----------

    private void rebuildTexture(long version, ResourceKey<Level> dim) {
        LocalPlayer player = minecraft.player;
        if (player == null) return;
        ChunkPos center = new ChunkPos(player.blockPosition());

        int size = (radius * 2 + 1) * tileRes;

        NativeImage img = new NativeImage(NativeImage.Format.RGBA, size, size, true);
        boolean handedOff = false;
        try {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int[] pixels = cache.get(dim, new ChunkPos(center.x + dx, center.z + dz));
                    if (pixels == null) continue;
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
        } finally {
            if (!handedOff) img.close();
        }
    }

    // ---------- 交互 ----------

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double hAmount, double vAmount) {
        if (vAmount == 0) return super.mouseScrolled(mouseX, mouseY, hAmount, vAmount);

        double factor = vAmount > 0 ? ZOOM_STEP : 1.0 / ZOOM_STEP;
        double newZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom * factor));
        if (Math.abs(newZoom - zoom) < 1e-6) return true;

        zoom = newZoom;
        zoomToastUntil = System.currentTimeMillis() + 900;
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        for (Btn b : buttons) {
            if (b.contains(event.x(), event.y())) {
                b.action.run();
                return true;
            }
        }
        if (event.y() < TOPBAR_H || event.y() > height - BOTBAR_H) {
            return super.mouseClicked(event, bl);
        }
        if (event.button() == 0) {
            dragging = true;
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
        if (event.button() == 0) dragging = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (e.key() == InputConstants.KEY_ESCAPE) { onClose(); return true; }
        if (e.key() == InputConstants.KEY_E) { exportStitchedMap(); return true; }
        if (e.key() == InputConstants.KEY_C) { offsetX = 0; offsetZ = 0; return true; }
        if (e.key() == InputConstants.KEY_R) {
            pendingReload = true;
            return true;
        }
        return super.keyPressed(e);
    }

    // ---------- 重载 ----------

    private void doReload() {
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null) return;

        long t0 = System.currentTimeMillis();

        ChunkMapMod.reload();

        // 重载后同步 tileRes；若变化则重算布局
        int newRes = currentTileRes();
        if (newRes != tileRes) {
            tileRes = newRes;
            recomputeLayout();
            lastTileRes = -1;
        }

        var dispatcher = ChunkMapMod.getDispatcher();
        if (dispatcher == null) return;
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
        if (logger != null) {
            logger.info("[UI] R 重载：" + count + " 区块入队，同步耗时=" + dt + "ms");
        }
    }

    // ---------- 导出 ----------

    private void exportStitchedMap() {
        ClientLevel level = minecraft.level;
        if (level == null) { showExportMsg("未进入世界", false); return; }
        if (exporting) { showExportMsg("导出进行中...", false); return; }

        exporting = true;
        final Identifier dim = level.dimension().identifier();
        // 从最新配置动态读取（重载后自动生效）
        final String dir = currentOutputDir();
        final int res = currentTileRes();
        showExportMsg("正在导出...", false);

        if (logger != null) logger.info("[UI] 开始导出 dim=" + dim + " dir=" + dir + " res=" + res);

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
                } else if (out == null) {
                    showExportMsg("没有可拼接的瓦片", false);
                } else {
                    showExportMsg("已导出: " + out.getFileName(), true);
                    if (logger != null) logger.info("[UI] 导出完成 " + out.toAbsolutePath());
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

    // ---------- 生命周期 ----------

    @Override
    public void onClose() { releaseTexture(); super.onClose(); }

    @Override
    public void removed() { releaseTexture(); super.removed(); }

    private void releaseTexture() {
        if (texture != null) {
            minecraft.getTextureManager().release(TEXTURE_ID);
            texture = null;
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

    // ---------- 按钮内部类 ----------

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