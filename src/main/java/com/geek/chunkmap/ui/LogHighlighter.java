package com.geek.chunkmap.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 日志行自动上色器。
 *
 * <p>思路：从左到右扫描文本，在每个位置按优先级依次尝试匹配一组正则 / 关键词；
 * 命中就切出一段带颜色的 Span，未命中就累积到缓冲区（使用 baseColor）。
 *
 * <p>只依赖纯文本，不依赖 Minecraft 类，方便单测。
 */
public final class LogHighlighter {

    private LogHighlighter() {}

    // ------------------------------------------------------------------
    // Span：一段带颜色的文本
    // ------------------------------------------------------------------
    public record Span(String text, int color) {}

    // ------------------------------------------------------------------
    // 调色板
    // ------------------------------------------------------------------
    public static final int C_NUMBER   = 0xFFB392F0;  // 普通数字：淡紫
    public static final int C_COORD    = 0xFF79C0FF;  // 坐标 (x, y, z)：浅蓝
    public static final int C_DURATION = 0xFFF0883E;  // 时长 123ms：橙
    public static final int C_PATH     = 0xFF7EE787;  // 文件路径：绿
    public static final int C_NS_ID    = 0xFF56D4DD;  // 命名空间 minecraft:overworld：青
    public static final int C_ARROW    = 0xFFFFA657;  // 箭头 → / -> ：橙
    public static final int C_QUOTED   = 0xFFA5D6FF;  // "引号字符串"：淡蓝
    public static final int C_CLASS    = 0xFFD2A8FF;  // a.b.C 类名：紫
    public static final int C_KV_KEY   = 0xFF79C0FF;  // key=（含等号）：浅蓝
    public static final int C_OK       = 0xFF7EE787;  // 成功词：绿
    public static final int C_ERR      = 0xFFFF7B72;  // 失败词：红
    public static final int C_WARN     = 0xFFE3B341;  // 警告词：黄

    // ------------------------------------------------------------------
    // 模式（按优先级顺序在 tryMatch 里依次尝试）
    // ------------------------------------------------------------------
    private static final Pattern COORD = Pattern.compile(
            "\\(\\s*-?\\d+\\s*,\\s*-?\\d+\\s*,\\s*-?\\d+\\s*\\)");

    private static final Pattern DURATION = Pattern.compile(
            "\\d+(?:\\.\\d+)?\\s*(?:ms|us|ns|sec|seconds|s|MB|KB|GB)");

    private static final Pattern NS_ID = Pattern.compile(
            "[a-z][a-z0-9_]*:[a-z0-9_/]+");

    private static final Pattern PATH = Pattern.compile(
            "(?:[A-Za-z]:)?(?:[\\w.-]+[\\\\/])+[\\w.-]+");

    private static final Pattern QUOTED = Pattern.compile("\"[^\"]*\"");

    private static final Pattern CLASSNAME = Pattern.compile(
            "[A-Z][a-zA-Z0-9_]*(?:\\.[A-Z][a-zA-Z0-9_]*)+");

    private static final Pattern ARROW = Pattern.compile("→|->|←|<-");

    private static final Pattern KV = Pattern.compile(
            "([A-Za-z_][A-Za-z0-9_]*)(=)");

    private static final Pattern NUMBER = Pattern.compile(
            "-?\\d+(?:\\.\\d+)?");

    // 关键词（全部小写；只有 ASCII 关键词才启用单词边界检查）
    private static final String[] KW_OK = {
            "成功", "完成", "通过", "就绪", "解锁",
            "ok", "done", "ready", "success", "unlocked", "passed", "ready"
    };
    private static final String[] KW_ERR = {
            "失败", "错误", "异常", "崩溃", "拒绝", "无法", "中断", "致命",
            "error", "fail", "failed", "failure", "exception", "crash",
            "denied", "rejected", "fatal", "abort"
    };
    private static final String[] KW_WARN = {
            "警告", "注意", "请勿", "已被弃用",
            "warn", "warning", "deprecated", "caution"
    };

    // ------------------------------------------------------------------
    // 对外入口
    // ------------------------------------------------------------------

    /**
     * 将整行文本按内容自动切分为带颜色的 Span 列表。
     *
     * @param text      待着色的文本
     * @param baseColor 未命中任何模式时的默认颜色（一般取该行的日志级别色）
     * @return Span 列表；永不返回 null
     */
    public static List<Span> highlight(String text, int baseColor) {
        List<Span> result = new ArrayList<>();
        if (text == null || text.isEmpty()) return result;

        StringBuilder buf = new StringBuilder();
        int i = 0;
        int n = text.length();

        while (i < n) {
            Match m = tryMatch(text, i);
            if (m == null) {
                buf.append(text.charAt(i));
                i++;
                continue;
            }
            if (buf.length() > 0) {
                result.add(new Span(buf.toString(), baseColor));
                buf.setLength(0);
            }
            result.add(new Span(text.substring(i, i + m.length), m.color));
            i += m.length;
        }
        if (buf.length() > 0) {
            result.add(new Span(buf.toString(), baseColor));
        }
        return result;
    }

    // ------------------------------------------------------------------
    // 匹配核心
    // ------------------------------------------------------------------

    private record Match(int length, int color) {}

    private static Match tryMatch(String text, int pos) {
        Match m;

        // 1) (x, y, z) 坐标
        if ((m = tryPattern(COORD, text, pos, C_COORD)) != null) return m;

        // 2) 时长：123ms / 4.5s / 16MB
        if ((m = tryPattern(DURATION, text, pos, C_DURATION)) != null) return m;

        // 3) 命名空间：minecraft:overworld
        if ((m = tryPattern(NS_ID, text, pos, C_NS_ID)) != null) return m;

        // 4) 路径：config/chunkmap.json / logs\chunkmap\xxx.log
        if ((m = tryPattern(PATH, text, pos, C_PATH)) != null) return m;

        // 5) 引号字符串
        if ((m = tryPattern(QUOTED, text, pos, C_QUOTED)) != null) return m;

        // 6) 类名：com.geek.chunkmap.ChunkMapMod
        if ((m = tryPattern(CLASSNAME, text, pos, C_CLASS)) != null) return m;

        // 7) 箭头
        if ((m = tryPattern(ARROW, text, pos, C_ARROW)) != null) return m;

        // 8) key= （只染色键名和等号，值交给后续规则）
        if ((m = tryPattern(KV, text, pos, C_KV_KEY)) != null) return m;

        // 9) 关键词
        if ((m = tryKeyword(text, pos)) != null) return m;

        // 10) 普通数字（要求左边界，避免污染 abc123）
        if (isLeftBoundary(text, pos)) {
            if ((m = tryPattern(NUMBER, text, pos, C_NUMBER)) != null) return m;
        }

        return null;
    }

    private static Match tryPattern(Pattern p, String text, int pos, int color) {
        Matcher m = p.matcher(text);
        m.region(pos, text.length());
        if (m.lookingAt()) {
            int len = m.end() - pos;
            if (len > 0) return new Match(len, color);
        }
        return null;
    }

    private static Match tryKeyword(String text, int pos) {
        int bestLen = 0;
        int bestColor = 0;

        bestLen = scanKeywords(text, pos, KW_ERR, C_ERR, bestLen, bestColor);
        if (bestLen > 0) bestColor = C_ERR;

        int wl = scanKeywords(text, pos, KW_WARN, C_WARN, 0, 0);
        if (wl > bestLen) { bestLen = wl; bestColor = C_WARN; }

        int ol = scanKeywords(text, pos, KW_OK, C_OK, 0, 0);
        if (ol > bestLen) { bestLen = ol; bestColor = C_OK; }

        if (bestLen > 0) return new Match(bestLen, bestColor);
        return null;
    }

    private static int scanKeywords(String text, int pos, String[] kws,
                                    int color, int curLen, int curColor) {
        int best = curLen;
        for (String kw : kws) {
            int end = pos + kw.length();
            if (end > text.length()) continue;
            if (!matchesIgnoreCase(text, pos, kw)) continue;
            if (!hasWordBoundary(text, pos, kw)) continue;
            if (kw.length() > best) best = kw.length();
        }
        return best;
    }

    private static boolean matchesIgnoreCase(String text, int pos, String kw) {
        for (int i = 0; i < kw.length(); i++) {
            char a = Character.toLowerCase(text.charAt(pos + i));
            char b = Character.toLowerCase(kw.charAt(i));
            if (a != b) return false;
        }
        return true;
    }

    /**
     * ASCII 关键词需要单词边界（避免 "token" 命中 "ok"）。
     * 含非 ASCII 字符的关键词（中文）不做边界检查。
     */
    private static boolean hasWordBoundary(String text, int pos, String kw) {
        for (int i = 0; i < kw.length(); i++) {
            if (kw.charAt(i) > 127) return true; // 含中文 → 无需边界
        }
        if (pos > 0) {
            char prev = text.charAt(pos - 1);
            if (Character.isLetterOrDigit(prev) || prev == '_') return false;
        }
        int end = pos + kw.length();
        if (end < text.length()) {
            char next = text.charAt(end);
            if (Character.isLetterOrDigit(next) || next == '_') return false;
        }
        return true;
    }

    private static boolean isLeftBoundary(String text, int pos) {
        if (pos == 0) return true;
        char prev = text.charAt(pos - 1);
        return !(Character.isLetterOrDigit(prev) || prev == '_' || prev == '.');
    }
}