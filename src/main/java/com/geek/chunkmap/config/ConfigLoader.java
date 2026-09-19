package com.geek.chunkmap.config;

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
        Path path = configPath();
        boolean exists = Files.exists(path);

        if (exists) {
            try (Reader r = Files.newBufferedReader(path)) {
                JsonObject obj = GSON.fromJson(r, JsonObject.class);
                if (obj != null) {
                    return fromJson(obj).validated();
                }
            } catch (Exception e) {
                System.err.println("[ChunkMap] 配置读取失败，使用默认值: " + e.getMessage());
            }
        }

        TileMapConfig def = TileMapConfig.defaults();
        if (!exists) {
            save(def);
        }
        return def;
    }

    public static void save(TileMapConfig config) {
        Path path = configPath();
        try {
            Files.createDirectories(path.getParent());
            JsonObject obj = toJson(config);
            try (Writer w = Files.newBufferedWriter(path)) {
                GSON.toJson(obj, w);
            }
        } catch (IOException e) {
            System.err.println("[ChunkMap] 配置保存失败: " + e.getMessage());
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
                getString(o, "githubToken", "")
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
        return o;
    }
}