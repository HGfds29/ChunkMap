package com.geek.chunkmap.config;

import com.geek.chunkmap.ChunkMapMod;
import com.geek.chunkmap.util.FileLogger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigLoader {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static Path configPath() {
        return Path.of("config", "chunkmap.json");
    }

    public static TileMapConfig load() {
        FileLogger lg = ChunkMapMod.getLogger();
        Path path = configPath();
        boolean exists = Files.exists(path);

        if (lg != null) {
            lg.info("[Config] load path=" + path.toAbsolutePath()
                    + " exists=" + exists);
        }

        if (exists) {
            try (Reader r = Files.newBufferedReader(path)) {
                JsonObject obj = GSON.fromJson(r, JsonObject.class);
                if (obj != null) {
                    TileMapConfig c = fromJson(obj).validated();
                    if (lg != null) lg.info("[Config] 加载成功: " + c);
                    return c;
                }
            } catch (Exception e) {
                if (lg != null) lg.error("[Config] 读取失败，使用默认值", e);
                else System.err.println("[ChunkMap] 配置读取失败: " + e.getMessage());
            }
        }

        TileMapConfig def = TileMapConfig.defaults();
        if (!exists) {
            if (lg != null) lg.info("[Config] 首次运行，写入默认配置");
            save(def);
        }
        return def;
    }

    public static void save(TileMapConfig config) {
        FileLogger lg = ChunkMapMod.getLogger();
        Path path = configPath();
        try {
            Files.createDirectories(path.getParent());
            JsonObject obj = toJson(config);
            try (Writer w = Files.newBufferedWriter(path)) {
                GSON.toJson(obj, w);
            }
            if (lg != null) lg.info("[Config] 保存成功: " + path.toAbsolutePath());
        } catch (IOException e) {
            if (lg != null) lg.error("[Config] 保存失败", e);
            else System.err.println("[ChunkMap] 配置保存失败: " + e.getMessage());
        }
    }

    private static TileMapConfig fromJson(JsonObject o) {
        return new TileMapConfig(
                getInt(o, "tileResolution", 32),
                getString(o, "outputDir", "chunkmap-output"),
                getColorMode(o),
                getBool(o, "shadeByHeight", true),
                getBool(o, "uiAnimation", true),
                getBool(o, "deleteOnUnload", false),
                getInt(o, "renderThreads", 2),
                getInt(o, "logRetentionDays", 7),
                getString(o, "githubToken", ""),
                getBool(o, "debugMode", false),
                getLogLevel(o),
                getBool(o, "debugOverlay", false),
                // 新增调试开关
                getBool(o, "debugShowChunkBorders", false),
                getBool(o, "debugShowTileCoords", false),
                getBool(o, "debugShowPlayerChunk", false),
                getBool(o, "debugShowOrigin", false),
                getBool(o, "debugShowHighlight", false),
                getBool(o, "debugNoGrid", false),
                getBool(o, "debugWireframe", false),
                getBool(o, "debugShowFps", false),
                getBool(o, "debugShowMemory", false),
                getBool(o, "debugShowQueue", false),
                getBool(o, "debugShowTimings", false),
                getBool(o, "debugForceRebuild", false)
        );
    }

    private static int getInt(JsonObject o, String k, int def) {
        try { return o.has(k) ? o.get(k).getAsInt() : def; } catch (Exception e) { return def; }
    }
    private static boolean getBool(JsonObject o, String k, boolean def) {
        try { return o.has(k) ? o.get(k).getAsBoolean() : def; } catch (Exception e) { return def; }
    }
    private static String getString(JsonObject o, String k, String def) {
        try { return o.has(k) ? o.get(k).getAsString() : def; } catch (Exception e) { return def; }
    }
    private static TileMapConfig.ColorMode getColorMode(JsonObject o) {
        try {
            return o.has("colorMode")
                    ? TileMapConfig.ColorMode.valueOf(o.get("colorMode").getAsString())
                    : TileMapConfig.ColorMode.MAP_COLOR;
        } catch (Exception e) {
            return TileMapConfig.ColorMode.MAP_COLOR;
        }
    }

    private static TileMapConfig.LogLevel getLogLevel(JsonObject o) {
        try {
            return o.has("debugLogLevel")
                    ? TileMapConfig.LogLevel.valueOf(o.get("debugLogLevel").getAsString())
                    : TileMapConfig.LogLevel.INFO;
        } catch (Exception e) {
            return TileMapConfig.LogLevel.INFO;
        }
    }

    private static JsonObject toJson(TileMapConfig c) {
        JsonObject o = new JsonObject();
        o.addProperty("tileResolution", c.tileResolution());
        o.addProperty("outputDir", c.outputDir());
        o.addProperty("colorMode", c.colorMode().name());
        o.addProperty("shadeByHeight", c.shadeByHeight());
        o.addProperty("uiAnimation", c.uiAnimation());
        o.addProperty("deleteOnUnload", c.deleteOnUnload());
        o.addProperty("renderThreads", c.renderThreads());
        o.addProperty("logRetentionDays", c.logRetentionDays());
        o.addProperty("githubToken", c.githubToken());
        o.addProperty("debugMode", c.debugMode());
        o.addProperty("debugLogLevel", c.debugLogLevel().name());
        o.addProperty("debugOverlay", c.debugOverlay());
        // 新增调试开关
        o.addProperty("debugShowChunkBorders", c.debugShowChunkBorders());
        o.addProperty("debugShowTileCoords", c.debugShowTileCoords());
        o.addProperty("debugShowPlayerChunk", c.debugShowPlayerChunk());
        o.addProperty("debugShowOrigin", c.debugShowOrigin());
        o.addProperty("debugShowHighlight", c.debugShowHighlight());
        o.addProperty("debugNoGrid", c.debugNoGrid());
        o.addProperty("debugWireframe", c.debugWireframe());
        o.addProperty("debugShowFps", c.debugShowFps());
        o.addProperty("debugShowMemory", c.debugShowMemory());
        o.addProperty("debugShowQueue", c.debugShowQueue());
        o.addProperty("debugShowTimings", c.debugShowTimings());
        o.addProperty("debugForceRebuild", c.debugForceRebuild());
        return o;
    }
}