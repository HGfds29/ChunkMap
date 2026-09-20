package com.geek.chunkmap.ui;

import com.geek.chunkmap.ChunkMapMod;
import com.geek.chunkmap.util.FileLogger;
import com.google.gson.Gson;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FeedbackScreen extends Screen {

    private static final int TOPBAR_H = 30;
    private static final int BOTBAR_H = 22;
    private static final int BTN_H    = 18;

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
    private static final int C_OK         = 0xFF7EE787;
    private static final int C_ERR        = 0xFFFF7B72;
    private static final int C_FIELD_BG   = 0xFF15191F;

    private static final long STATUS_MS = 6000;
    private static final long DAY_MS = 24L * 3600L * 1000L;

    private static final Path RATE_FILE = Path.of("config", "chunkmap-feedback.time");

    private static final DateTimeFormatter TITLE_FMT =
            DateTimeFormatter.ofPattern("MM-dd HH:mm");
    private static final DateTimeFormatter FULL_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private enum Option { LOG, SYS, MODS, CONFIG, CRASH }

    private final Screen parent;

    private EditBox titleBox;
    private EditBox contentBox;
    private final List<Checkbox> checkboxes = new ArrayList<>();
    private final List<Btn> buttons = new ArrayList<>();

    private String status = "";
    private boolean statusOk = true;
    private long statusUntil = 0;
    private boolean submitting = false;

    public FeedbackScreen(Screen parent) {
        super(Component.literal("ChunkMap 反馈"));
        this.parent = parent;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    protected void init() {
        int marginX = 20;
        int fieldW = width - marginX * 2;
        int y = TOPBAR_H + 22;

        // ---- 标题 ----
        titleBox = new EditBox(font, marginX, y, fieldW, 20, Component.literal("标题"));
        titleBox.setMaxLength(200);
        titleBox.setHint(Component.literal("一句话标题，例如：导出 PNG 时崩溃"));
        titleBox.setValue("");
        y += 20 + 14;

        // ---- 内容 ----
        int contentH = Math.max(50, Math.min(140, height - y - BOTBAR_H - 130));
        contentBox = new EditBox(font, marginX, y, fieldW, contentH, Component.literal("反馈内容"));
        contentBox.setMaxLength(2000);
        contentBox.setHint(Component.literal("详细描述你遇到的问题、复现步骤…"));
        contentBox.setValue("");
        contentBox.setFocused(true);
        y += contentH + 10;

        // ---- 勾选框 ----
        checkboxes.clear();
        checkboxes.add(new Checkbox(marginX, y, font.width("附上最近 100 行日志") + 22,
                "附上最近 100 行日志", Option.LOG, true));
        y += 16;
        checkboxes.add(new Checkbox(marginX, y, font.width("附上系统信息（OS / Java / 内存）") + 22,
                "附上系统信息（OS / Java / 内存）", Option.SYS, true));
        y += 16;
        checkboxes.add(new Checkbox(marginX, y, font.width("附上已安装模组列表") + 22,
                "附上已安装模组列表", Option.MODS, true));
        y += 16;
        checkboxes.add(new Checkbox(marginX, y, font.width("附上 ChunkMap 当前配置") + 22,
                "附上 ChunkMap 当前配置", Option.CONFIG, true));
        y += 16;
        checkboxes.add(new Checkbox(marginX, y, font.width("附上最新崩溃报告（如有）") + 22,
                "附上最新崩溃报告（如有）", Option.CRASH, false));

        layoutButtons();
    }

    private void layoutButtons() {
        buttons.clear();
        int y = (TOPBAR_H - BTN_H) / 2;
        int x = width - 8;

        Btn btnBack = new Btn(x - 44, y, 44, BTN_H, "返回", () -> minecraft.setScreen(parent));
        x -= 44 + 4;
        Btn btnIssues = new Btn(x - 76, y, 76, BTN_H, "打开 Issues", this::openIssues);
        x -= 76 + 4;
        Btn btnCopy = new Btn(x - 60, y, 60, BTN_H, "复制内容", this::copyContent);
        x -= 60 + 4;
        Btn btnSubmit = new Btn(x - 60, y, 60, BTN_H, "提交反馈", this::submitToGitHub);

        buttons.add(btnBack);
        buttons.add(btnIssues);
        buttons.add(btnCopy);
        buttons.add(btnSubmit);
    }

    // ================ 构建反馈正文 ================

    private String buildFullBody() {
        StringBuilder sb = new StringBuilder();
        sb.append(contentBox.getValue()).append("\n\n---\n");
        sb.append("**版本**: ").append(ChunkMapMod.VERSION).append("\n");
        sb.append("**玩家**: ").append(minecraft.getUser().getName()).append("\n");
        sb.append("**时间**: ").append(LocalDateTime.now().format(FULL_FMT)).append("\n");

        for (Checkbox cb : checkboxes) {
            if (!cb.checked) continue;
            switch (cb.id) {
                case LOG -> {
                    sb.append("\n<details><summary>最近 100 行日志</summary>\n\n```\n");
                    sb.append(tailLog(100)).append("\n```\n</details>\n");
                }
                case SYS -> {
                    sb.append("\n<details><summary>系统信息</summary>\n\n");
                    sb.append(buildSystemInfo()).append("\n</details>\n");
                }
                case MODS -> {
                    sb.append("\n<details><summary>已安装模组</summary>\n\n");
                    sb.append(buildModList()).append("\n</details>\n");
                }
                case CONFIG -> {
                    sb.append("\n<details><summary>ChunkMap 配置</summary>\n\n");
                    sb.append(buildConfigInfo()).append("\n</details>\n");
                }
                case CRASH -> {
                    String crash = readLatestCrashReport();
                    if (crash != null) {
                        sb.append("\n<details><summary>最新崩溃报告</summary>\n\n```\n");
                        sb.append(crash).append("\n```\n</details>\n");
                    }
                }
            }
        }
        return sb.toString();
    }

    private static String buildSystemInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("- OS: ")
                .append(System.getProperty("os.name")).append(" ")
                .append(System.getProperty("os.version")).append(" (")
                .append(System.getProperty("os.arch")).append(")\n");
        sb.append("- Java: ")
                .append(System.getProperty("java.version")).append(" (")
                .append(System.getProperty("java.vendor")).append(")\n");
        Runtime rt = Runtime.getRuntime();
        long maxMb = rt.maxMemory() / 1024 / 1024;
        long totalMb = rt.totalMemory() / 1024 / 1024;
        long usedMb = totalMb - rt.freeMemory() / 1024 / 1024;
        sb.append("- Memory: ").append(usedMb).append("MB used / ")
                .append(totalMb).append("MB committed / ")
                .append(maxMb).append("MB max\n");
        return sb.toString();
    }

    private static String buildModList() {
        StringBuilder sb = new StringBuilder();
        try {
            var mods = FabricLoader.getInstance().getAllMods().stream()
                    .sorted((a, b) -> a.getMetadata().getId().compareTo(b.getMetadata().getId()))
                    .toList();
            for (var mod : mods) {
                sb.append("- ").append(mod.getMetadata().getName())
                        .append(" `").append(mod.getMetadata().getVersion().getFriendlyString()).append("`");
                String id = mod.getMetadata().getId();
                if (!id.equals(mod.getMetadata().getName())) {
                    sb.append(" (").append(id).append(")");
                }
                sb.append("\n");
            }
        } catch (Throwable t) {
            sb.append("(读取失败: ").append(t.getMessage()).append(")\n");
        }
        return sb.toString();
    }

    private static String buildConfigInfo() {
        var c = ChunkMapMod.getConfig();
        if (c == null) return "(无配置)\n";
        StringBuilder sb = new StringBuilder();
        sb.append("- tileResolution: ").append(c.tileResolution()).append("\n");
        sb.append("- outputDir: ").append(c.outputDir()).append("\n");
        sb.append("- colorMode: ").append(c.colorMode()).append("\n");
        sb.append("- shadeByHeight: ").append(c.shadeByHeight()).append("\n");
        sb.append("- uiAnimation: ").append(c.uiAnimation()).append("\n");
        sb.append("- deleteOnUnload: ").append(c.deleteOnUnload()).append("\n");
        sb.append("- renderThreads: ").append(c.renderThreads()).append("\n");
        sb.append("- logRetentionDays: ").append(c.logRetentionDays()).append("\n");
        boolean hasToken = c.githubToken() != null && !c.githubToken().isBlank();
        sb.append("- githubToken: ").append(hasToken ? "(已配置)" : "(未配置)").append("\n");
        return sb.toString();
    }

    private static String readLatestCrashReport() {
        try {
            Path crashDir = Path.of("crash-reports");
            if (!Files.isDirectory(crashDir)) return null;
            Path latest = Files.list(crashDir)
                    .filter(p -> p.getFileName().toString().endsWith(".txt"))
                    .max((a, b) -> {
                        try {
                            return Files.getLastModifiedTime(a).compareTo(Files.getLastModifiedTime(b));
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .orElse(null);
            if (latest == null) return null;
            List<String> lines = Files.readAllLines(latest, StandardCharsets.UTF_8);
            int limit = Math.min(120, lines.size());
            return String.join("\n", lines.subList(0, limit));
        } catch (Throwable t) {
            return null;
        }
    }

    private static String tailLog(int maxLines) {
        FileLogger logger = ChunkMapMod.getLogger();
        Path p = logger != null ? logger.getCurrentLogFile() : null;
        if (p == null || !Files.exists(p)) return "(无日志)";
        try {
            List<String> all;
            try {
                all = Files.readAllLines(p, StandardCharsets.UTF_8);
            } catch (java.nio.charset.MalformedInputException e) {
                all = Files.readAllLines(p, java.nio.charset.Charset.forName("GBK"));
            }
            int from = Math.max(0, all.size() - maxLines);
            return String.join("\n", all.subList(from, all.size()));
        } catch (IOException e) {
            return "(读取日志失败: " + e.getMessage() + ")";
        }
    }

    // ================ 提交 / 复制 ================

    private void submitToGitHub() {
        if (submitting) return;

        String content = contentBox.getValue();
        if (content == null || content.isBlank()) {
            setStatus("请先输入反馈内容", false);
            return;
        }

        if (!canSubmitToday()) {
            long remain = DAY_MS - (System.currentTimeMillis() - lastSubmitTime());
            setStatus("今日已提交过反馈，请 " + formatDuration(remain) + " 后再试", false);
            return;
        }

        String token = ChunkMapMod.getEffectiveToken();
        if (token.isBlank()) {
            setStatus("未配置 GitHub Token，或点击\"打开 Issues\"手动提交", false);
            return;
        }

        submitting = true;
        setStatus("正在提交…", true);

        final String playerName = minecraft.getUser().getName();
        String userTitle = titleBox.getValue().trim();
        if (userTitle.isEmpty()) {
            userTitle = playerName + " @ " + LocalDateTime.now().format(TITLE_FMT);
        }
        final String title = "[反馈] " + userTitle;
        final String body = buildFullBody();

        Thread t = new Thread(() -> {
            String result = postIssue(token, title, body);
            minecraft.execute(() -> {
                submitting = false;
                boolean ok = result.startsWith("✅");
                setStatus(result, ok);
                if (ok) {
                    recordSubmit();
                    contentBox.setValue("");
                    titleBox.setValue("");
                }
            });
        }, "ChunkMap-Feedback");
        t.setDaemon(true);
        t.start();
    }

    private static String postIssue(String token, String title, String body) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(15))
                    .build();
            String json = new Gson().toJson(Map.of("title", title, "body", body));
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(ChunkMapMod.GITHUB_API_ISSUES))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github+json")
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "ChunkMap-Mod/" + ChunkMapMod.VERSION)
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            return switch (resp.statusCode()) {
                case 201 -> "✅ 提交成功，感谢反馈！";
                case 401 -> "❌ Token 无效或已过期";
                case 403 -> "❌ Token 权限不足 / 已达频率限制";
                case 404 -> "❌ 仓库不存在（Token 无权访问）";
                case 422 -> "❌ 内容格式不合法";
                default  -> "❌ 提交失败 HTTP " + resp.statusCode();
            };
        } catch (Throwable t) {
            return "❌ 提交失败: " + t.getMessage();
        }
    }

    private static long lastSubmitTime() {
        try {
            if (!Files.exists(RATE_FILE)) return 0L;
            return Long.parseLong(Files.readString(RATE_FILE).trim());
        } catch (Throwable t) {
            return 0L;
        }
    }

    private static boolean canSubmitToday() {
        return System.currentTimeMillis() - lastSubmitTime() >= DAY_MS;
    }

    private static void recordSubmit() {
        try {
            Files.createDirectories(RATE_FILE.getParent());
            Files.writeString(RATE_FILE, String.valueOf(System.currentTimeMillis()));
        } catch (IOException ignored) {}
    }

    private static String formatDuration(long ms) {
        if (ms <= 0) return "一会儿";
        long totalMin = ms / 60000;
        long h = totalMin / 60;
        long m = totalMin % 60;
        if (h > 0) return h + " 小时 " + m + " 分钟";
        return Math.max(1, m) + " 分钟";
    }

    private void copyContent() {
        String content = contentBox.getValue();
        if (content == null || content.isBlank()) {
            setStatus("请先输入反馈内容", false);
            return;
        }
        minecraft.keyboardHandler.setClipboard(buildFullBody());
        setStatus("已复制到剪贴板，粘贴到 Issues 即可", true);
    }

    private void openIssues() {
        copyContent();
        boolean ok = openUri(ChunkMapMod.GITHUB_ISSUES_URL);
        if (ok) {
            setStatus("已打开 Issues 页，内容已复制到剪贴板", true);
        } else {
            minecraft.keyboardHandler.setClipboard(ChunkMapMod.GITHUB_ISSUES_URL);
            setStatus("打开失败，链接已复制到剪贴板", false);
        }
    }

    private void setStatus(String msg, boolean ok) {
        this.status = msg;
        this.statusOk = ok;
        this.statusUntil = System.currentTimeMillis() + STATUS_MS;
    }

    // ================ 渲染 ================

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, C_BG);

        int marginX = 20;

        // 标签
        g.drawString(font, "标题", marginX, TOPBAR_H + 10, C_TEXT_DIM, false);
        g.drawString(font, "内容", marginX, titleBox.getY() + titleBox.getHeight() + 4, C_TEXT_DIM, false);

        // 输入框背景（EditBox 自带边框比较朴素，给它一个底色块更清晰）
        drawFieldBg(g, titleBox);
        drawFieldBg(g, contentBox);

        titleBox.render(g, mouseX, mouseY, delta);
        contentBox.render(g, mouseX, mouseY, delta);

        for (Checkbox cb : checkboxes) {
            drawCheckbox(g, cb, cb.contains(mouseX, mouseY));
        }

        if (!canSubmitToday()) {
            long remain = DAY_MS - (System.currentTimeMillis() - lastSubmitTime());
            g.drawString(font, "今日已提交过，还需 " + formatDuration(remain) + " 才能再次提交",
                    marginX, height - BOTBAR_H - 14, C_TEXT_DIM, false);
        } else if (ChunkMapMod.getEffectiveToken().isBlank()) {
            g.drawString(font, "未配置 GitHub Token → 可用「复制内容」+「打开 Issues」手动提交",
                    marginX, height - BOTBAR_H - 14, C_TEXT_DIM, false);
        }

        drawTopBar(g);
        drawBottomBar(g);

        for (Btn b : buttons) drawButton(g, b, b.contains(mouseX, mouseY));
    }

    private void drawFieldBg(GuiGraphics g, EditBox box) {
        // 就画个底色块在 EditBox 下面
        g.fill(box.getX() - 2, box.getY() - 2,
                box.getX() + box.getWidth() + 2, box.getY() + box.getHeight() + 2,
                0x40000000);
    }

    private void drawTopBar(GuiGraphics g) {
        g.fill(0, 0, width, TOPBAR_H, C_BAR);
        g.fill(0, TOPBAR_H - 1, width, TOPBAR_H, C_BAR_LINE);
        g.drawString(font, "ChunkMap 反馈", 10, (TOPBAR_H - 8) / 2, C_TEXT, true);
    }

    private void drawBottomBar(GuiGraphics g) {
        int y = height - BOTBAR_H;
        g.fill(0, y, width, height, C_BAR);
        g.fill(0, y, width, y + 1, C_BAR_LINE);

        int ty = y + (BOTBAR_H - 8) / 2;

        if (status.isEmpty() || System.currentTimeMillis() > statusUntil) {
            g.drawString(font, "Tab 切换输入框 · 回车提交 · ESC 返回", 10, ty, C_TEXT_DIM, true);
        } else {
            g.drawString(font, status, 10, ty, statusOk ? C_OK : C_ERR, true);
        }

        String hint = "版本 " + ChunkMapMod.VERSION;
        g.drawString(font, hint, width - font.width(hint) - 10, ty, C_TEXT_DIM, true);
    }

    private void drawCheckbox(GuiGraphics g, Checkbox cb, boolean hovered) {
        float target = hovered ? 1f : 0f;
        cb.hover += (target - cb.hover) * 0.25f;
        if (Math.abs(cb.hover - target) < 0.005f) cb.hover = target;

        int bx = cb.x;
        int by = cb.y + (Checkbox.H - Checkbox.BOX) / 2;
        int size = Checkbox.BOX;

        int border = lerpColor(C_BTN_BORDER, C_BTN_BORDER_HOVER, cb.hover);
        int bg = lerpColor(C_BTN_BG, C_BTN_HOVER, cb.hover);
        int textCol = lerpColor(C_TEXT_DIM, C_TEXT, cb.hover);

        g.fill(bx, by, bx + size, by + size, bg);
        g.fill(bx, by, bx + size, by + 1, border);
        g.fill(bx, by + size - 1, bx + size, by + size, border);
        g.fill(bx, by, bx + 1, by + size, border);
        g.fill(bx + size - 1, by, bx + size, by + size, border);

        if (cb.checked) {
            g.fill(bx + 2, by + 2, bx + size - 2, by + size - 2, C_ACCENT);
        }

        g.drawString(font, cb.label, bx + size + 6, cb.y + (Checkbox.H - 8) / 2, textCol, false);
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

    // ================ 输入事件 ================

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        for (Btn b : buttons) {
            if (b.contains(event.x(), event.y())) {
                b.action.run();
                return true;
            }
        }
        for (Checkbox cb : checkboxes) {
            if (cb.contains(event.x(), event.y())) {
                cb.checked = !cb.checked;
                return true;
            }
        }
        if (titleBox.mouseClicked(event, bl)) return true;
        if (contentBox.mouseClicked(event, bl)) return true;
        return super.mouseClicked(event, bl);
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (e.key() == InputConstants.KEY_ESCAPE) {
            minecraft.setScreen(parent);
            return true;
        }
        if (e.key() == InputConstants.KEY_TAB) {
            boolean titleFocused = titleBox.isFocused();
            titleBox.setFocused(!titleFocused);
            contentBox.setFocused(titleFocused);
            return true;
        }
        if (e.key() == InputConstants.KEY_RETURN) {
            submitToGitHub();
            return true;
        }
        if (titleBox.isFocused() && titleBox.keyPressed(e)) return true;
        if (contentBox.isFocused() && contentBox.keyPressed(e)) return true;
        return super.keyPressed(e);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (titleBox.isFocused() && titleBox.charTyped(event)) return true;
        if (contentBox.isFocused() && contentBox.charTyped(event)) return true;
        return super.charTyped(event);
    }

    // ================ 工具 ================

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

    private static class Checkbox {
        static final int BOX = 10;
        static final int H = 14;
        final int x, y, w;
        final String label;
        final Option id;
        boolean checked;
        float hover = 0f;

        Checkbox(int x, int y, int w, String label, Option id, boolean checked) {
            this.x = x; this.y = y; this.w = w;
            this.label = label; this.id = id; this.checked = checked;
        }

        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + H;
        }
    }

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