package com.geek.chunkmap.event;

import com.geek.chunkmap.ChunkMapMod;
import com.geek.chunkmap.ui.ChunkMapScreen;
import com.geek.chunkmap.ui.ConfigurationScreen;
import com.geek.chunkmap.util.FileLogger;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.chunk.LevelChunk;

public class ClientEventHandler {

    private static final KeyMapping OPEN_MAP_KEY = KeyBindingHelper.registerKeyBinding(
            new KeyMapping("key.chunkmap.open_map",
                    InputConstants.KEY_M, KeyMapping.Category.MISC));

    public static void register() {
        var dispatcher = ChunkMapMod.getDispatcher();
        FileLogger lg = ChunkMapMod.getLogger();

        lg.info("[Event] 开始注册客户端事件...");

        ClientChunkEvents.CHUNK_LOAD.register((ClientLevel level, LevelChunk chunk) -> {
            if (lg.isEnabled(FileLogger.Level.DEBUG)) {
                lg.debug("[Event] CHUNK_LOAD dim=" + level.dimension().identifier()
                        + " pos=" + chunk.getPos());
            }
            lg.count("event.chunk_load");
            dispatcher.setCurrentDimension(level.dimension());
            dispatcher.enqueueRender(chunk.getPos());
        });

        ClientChunkEvents.CHUNK_UNLOAD.register((ClientLevel level, LevelChunk chunk) -> {
            if (lg.isEnabled(FileLogger.Level.DEBUG)) {
                lg.debug("[Event] CHUNK_UNLOAD dim=" + level.dimension().identifier()
                        + " pos=" + chunk.getPos());
            }
            lg.count("event.chunk_unload");
            if (ChunkMapMod.getConfig().deleteOnUnload()) {
                var storage = ChunkMapMod.getStorage();
                if (storage != null) {
                    lg.info("[Event] deleteOnUnload → 删除瓦片 "
                            + level.dimension().identifier() + " " + chunk.getPos());
                    storage.deleteTile(level.dimension(), chunk.getPos());
                }
            }
        });

        ClientTickEvents.END_WORLD_TICK.register((ClientLevel level) -> {
            if (!DirtyChunkTracker.isEmpty()) {
                var dirty = DirtyChunkTracker.drainDirty();
                if (lg.isEnabled(FileLogger.Level.TRACE)) {
                    lg.trace("[Event] END_WORLD_TICK 脏区块 " + dirty.size() + " 个");
                }
                lg.count("event.dirty_drain", dirty.size());
                for (var pos : dirty) {
                    dispatcher.enqueueRender(pos);
                }
            }
        });

        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((Minecraft client, ClientLevel level) -> {
            lg.info("[Event] 维度切换 → " + level.dimension().identifier());
            lg.count("event.world_change");
            dispatcher.setCurrentDimension(level.dimension());
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register((Minecraft client) -> {
            lg.info("[Event] CLIENT_STOPPING");
            lg.dumpCounters();
            lg.info("[Event] 日志行数总计 = " + lg.getLineCount());
            dispatcher.shutdown();
            lg.close();
        });

        ClientTickEvents.END_CLIENT_TICK.register((Minecraft client) -> {
            boolean altDown = client.hasAltDown();
            while (OPEN_MAP_KEY.consumeClick()) {
                lg.info("[Key] M 键点击 alt=" + altDown
                        + " screen=" + (client.screen == null ? "null" : client.screen.getClass().getSimpleName())
                        + " level=" + (client.level == null ? "null" : "present"));
                lg.count("key.m_pressed");

                if (client.level == null) {
                    lg.debug("[Key] 忽略：无世界");
                    continue;
                }

                if (altDown) {
                    lg.info("[Key] 打开配置界面");
                    client.setScreen(ConfigurationScreen.createConfigScreen(client.screen));
                } else if (client.screen == null) {
                    lg.info("[Key] 打开地图界面");
                    client.setScreen(new ChunkMapScreen(dispatcher.getCache(), lg));
                } else {
                    lg.debug("[Key] 当前有屏幕，忽略 M（只响应 Alt+M）");
                }
            }
        });

        lg.info("[Event] 客户端事件注册完成");
    }
}