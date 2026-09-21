package com.geek.chunkmap.event;

import com.geek.chunkmap.ChunkMapMod;
import com.geek.chunkmap.util.FileLogger;
import net.minecraft.world.level.ChunkPos;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DirtyChunkTracker {
    private static final Set<ChunkPos> dirty = ConcurrentHashMap.newKeySet();

    public static void markDirty(ChunkPos pos) {
        boolean added = dirty.add(pos);
        FileLogger lg = ChunkMapMod.getLogger();
        if (lg != null) {
            if (added) {
                lg.count("dirty.marked");
                if (lg.isEnabled(FileLogger.Level.TRACE)) {
                    lg.trace("[Dirty] mark " + pos + " total=" + dirty.size());
                }
            } else if (lg.isEnabled(FileLogger.Level.TRACE)) {
                lg.trace("[Dirty] dup  " + pos);
            }
        }
    }

    public static Set<ChunkPos> drainDirty() {
        Set<ChunkPos> snapshot = new java.util.HashSet<>(dirty);
        dirty.removeAll(snapshot);
        FileLogger lg = ChunkMapMod.getLogger();
        if (lg != null && !snapshot.isEmpty() && lg.isEnabled(FileLogger.Level.DEBUG)) {
            lg.debug("[Dirty] drain " + snapshot.size() + " 个");
        }
        return snapshot;
    }

    public static boolean isEmpty() { return dirty.isEmpty(); }
    public static int size() { return dirty.size(); }
}