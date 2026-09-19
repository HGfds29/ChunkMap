package com.geek.chunkmap.event;

import com.geek.chunkmap.ChunkMapMod;
import com.geek.chunkmap.ui.ChunkMapScreen;
import com.geek.chunkmap.ui.ConfigurationScreen;
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

    /**
     * 只注册一个 M 键：
     *   - 单独按：打开区块地图 UI
     *   - Alt + M：打开配置界面
     * 之前注册两个 M 会导致按键绑定界面出现两条 "M"，现合并为一个。
     */
    private static final KeyMapping OPEN_MAP_KEY = KeyBindingHelper.registerKeyBinding(
            new KeyMapping("key.chunkmap.open_map",
                    InputConstants.KEY_M, KeyMapping.Category.MISC));

    public static void register() {
        var dispatcher = ChunkMapMod.getDispatcher();
        var logger = ChunkMapMod.getLogger();

        logger.info("[Event] 开始注册客户端事件...");

        // 1. 区块加载
        ClientChunkEvents.CHUNK_LOAD.register((ClientLevel level, LevelChunk chunk) -> {
            dispatcher.setCurrentDimension(level.dimension());
            dispatcher.enqueueRender(chunk.getPos());
        });

        // 2. 区块卸载
        ClientChunkEvents.CHUNK_UNLOAD.register((ClientLevel level, LevelChunk chunk) -> {
            if (ChunkMapMod.getConfig().deleteOnUnload()) {
                var storage = ChunkMapMod.getStorage();
                if (storage != null) {
                    storage.deleteTile(level.dimension(), chunk.getPos());
                }
            }
        });

        // 3. 每 tick 处理脏区块
        ClientTickEvents.END_WORLD_TICK.register((ClientLevel level) -> {
            if (!DirtyChunkTracker.isEmpty()) {
                var dirty = DirtyChunkTracker.drainDirty();
                for (var pos : dirty) {
                    dispatcher.enqueueRender(pos);
                }
            }
        });

        // 4. 维度切换
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((Minecraft client, ClientLevel level) -> {
            dispatcher.setCurrentDimension(level.dimension());
        });

        // 5. 客户端停止
        ClientLifecycleEvents.CLIENT_STOPPING.register((Minecraft client) -> {
            dispatcher.shutdown();
            logger.close();
        });

        // 6. 按键：M / Alt+M 共用一个 KeyMapping，用 alt 分流
        ClientTickEvents.END_CLIENT_TICK.register((Minecraft client) -> {
            boolean altDown = client.hasAltDown();

            while (OPEN_MAP_KEY.consumeClick()) {
                if (client.level == null) continue;

                if (altDown) {
                    // Alt + M：配置界面（可从任意屏幕打开）
                    client.setScreen(ConfigurationScreen.createConfigScreen(client.screen));
                } else if (client.screen == null) {
                    // 单独 M：地图界面（只在无屏时打开，避免误触）
                    client.setScreen(new ChunkMapScreen(
                            dispatcher.getCache(),
                            logger));
                }
            }
        });

        logger.info("[Event] 客户端事件注册完成");
    }
}