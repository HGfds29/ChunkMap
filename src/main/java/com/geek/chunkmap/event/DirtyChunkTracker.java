package com.geek.chunkmap.event;

import com.geek.chunkmap.ChunkMapMod;
import net.minecraft.world.level.ChunkPos;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DirtyChunkTracker {
    private static final Set<ChunkPos> dirty = ConcurrentHashMap.newKeySet();

    public static void markDirty(ChunkPos pos) {
        boolean added = dirty.add(pos);
        if (added && ChunkMapMod.getLogger() != null) {
            ChunkMapMod.getLogger().trace("[Dirty] mark " + pos + " total=" + dirty.size());
        }
    }

    public static Set<ChunkPos> drainDirty() {
        Set<ChunkPos> snapshot = new java.util.HashSet<>(dirty);
        dirty.removeAll(snapshot);
        return snapshot;
    }

    public static boolean isEmpty() {
        return dirty.isEmpty();
    }

    public static int size() {
        return dirty.size();
    }
}