package com.geek.chunkmap.tile;

import com.geek.chunkmap.config.TileMapConfig;
import com.geek.chunkmap.render.ChunkSnapshot;
import com.geek.chunkmap.render.ChunkSnapshotter;
import com.geek.chunkmap.render.ChunkTopDownRenderer;
import com.geek.chunkmap.util.FileLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class RenderDispatcher {
    // config / storage 需要支持热替换，改为 volatile 非 final
    private volatile TileMapConfig config;
    private final FileLogger logger;
    private volatile TileStorage storage;
    private final TileMapCache cache = new TileMapCache();

    private volatile ChunkTopDownRenderer renderer;
    private volatile ChunkSnapshotter snapshotter;

    private final BlockingQueue<ChunkSnapshot> queue = new LinkedBlockingQueue<>();
    private final Set<ChunkPos> pending = ConcurrentHashMap.newKeySet();
    private final AtomicLong enqueuedCount = new AtomicLong();
    private final AtomicLong renderedCount = new AtomicLong();
    private final AtomicLong failedCount = new AtomicLong();

    /** 每次重建线程池自增，旧线程看到 gen 不匹配就会自然退出。 */
    private final AtomicLong workerGen = new AtomicLong();

    private volatile ExecutorService workers;
    private final ExecutorService ioExecutor;   // 单线程 IO：所有 PNG 写盘都排队到这里
    private volatile ResourceKey<Level> currentDimension;
    private volatile boolean running = false;

    public RenderDispatcher(TileMapConfig config, FileLogger logger,
                            ChunkTopDownRenderer renderer, ChunkSnapshotter snapshotter) {
        this.config = config;
        this.logger = logger;
        this.renderer = renderer;
        this.snapshotter = snapshotter;
        this.storage = new TileStorage(config.outputDir());

        this.ioExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ChunkMap-IO");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 热替换配置。
     * - outputDir 变化 → 重建 storage
     * - renderThreads 变化 → 重建线程池（旧线程自然退出）
     */
    public void updateConfig(TileMapConfig newConfig) {
        boolean outputChanged = !this.config.outputDir().equals(newConfig.outputDir());
        int oldThreads = this.config.renderThreads();
        this.config = newConfig;

        if (outputChanged) {
            this.storage = new TileStorage(newConfig.outputDir());
            logger.info("[Dispatcher] outputDir 变化，重建 storage → " + newConfig.outputDir());
        }

        if (running && newConfig.renderThreads() != oldThreads) {
            restartWorkers(newConfig.renderThreads());
        }

        logger.info("[Dispatcher] config 已更新: " + newConfig);
    }

    public TileMapConfig getConfig() { return config; }
    public TileStorage getStorage() { return storage; }

    public void setRenderer(ChunkTopDownRenderer r) {
        this.renderer = r;
        logger.info("[Dispatcher] renderer 已替换");
    }

    public void setSnapshotter(ChunkSnapshotter s) {
        this.snapshotter = s;
        logger.info("[Dispatcher] snapshotter 已替换");
    }

    public void enqueueRender(ChunkPos pos) {
        if (pos == null || !running) return;

        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread()) {
            mc.execute(() -> enqueueRender(pos));
            return;
        }

        ClientLevel level = mc.level;
        if (level == null) return;
        if (!pending.add(pos)) return;

        ChunkSnapshot snap = snapshotter.snapshot(level, pos);
        if (snap == null) {
            pending.remove(pos);
            return;
        }

        queue.offer(snap);
        enqueuedCount.incrementAndGet();
    }

    private ExecutorService buildWorkerPool(int n) {
        return new ThreadPoolExecutor(
                n, n, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                r -> {
                    Thread t = new Thread(r, "ChunkMap-Worker");
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    public void start() {
        running = true;
        int n = config.renderThreads();
        long gen = workerGen.incrementAndGet();
        workers = buildWorkerPool(n);
        for (int i = 0; i < n; i++) {
            workers.submit(() -> workerLoop(gen));
        }
        logger.info("[Dispatcher] 启动, 工作线程数=" + n);
    }

    /** 运行时重建工作线程池：旧线程在下一轮循环检测到 gen 变化后自然退出。 */
    private void restartWorkers(int n) {
        ExecutorService old = this.workers;
        long gen = workerGen.incrementAndGet();
        this.workers = buildWorkerPool(n);
        for (int i = 0; i < n; i++) {
            workers.submit(() -> workerLoop(gen));
        }
        if (old != null) old.shutdown();
        logger.info("[Dispatcher] 工作线程数 → " + n);
    }

    public void shutdown() {
        running = false;
        if (workers != null) {
            workers.shutdown();
            try {
                if (!workers.awaitTermination(5, TimeUnit.SECONDS)) workers.shutdownNow();
            } catch (InterruptedException e) {
                workers.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        if (ioExecutor != null) {
            ioExecutor.shutdown();
            try {
                if (!ioExecutor.awaitTermination(3, TimeUnit.SECONDS)) ioExecutor.shutdownNow();
            } catch (InterruptedException e) {
                ioExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        logger.info("[Dispatcher] 已关闭");
    }

    public void setCurrentDimension(ResourceKey<Level> dim) {
        if (Objects.equals(this.currentDimension, dim)) return;
        this.currentDimension = dim;
        logger.info("[Dispatcher] 当前维度切换为 " + (dim == null ? "null" : dim.identifier()));
    }

    public ResourceKey<Level> getCurrentDimension() { return currentDimension; }

    private void workerLoop(long gen) {
        while (running && gen == workerGen.get()) {
            try {
                ChunkSnapshot snap = queue.poll(100, TimeUnit.MILLISECONDS);
                if (snap == null) continue;
                pending.remove(snap.pos());
                processJob(snap);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Throwable t) {
                logger.error("[Worker] 异常: " + t.getMessage(), t);
            }
        }
    }

    private void processJob(ChunkSnapshot snap) {
        try {
            ChunkTopDownRenderer r = this.renderer;
            if (r == null) return;
            int[] pixels = r.renderChunk(snap);
            cache.put(snap.dim(), snap.pos(), pixels);

            // 注意：这里读取的 config / storage 都是 volatile，重载后自动生效
            final int res = config.tileResolution();
            final TileStorage st = this.storage;
            final Path out = st.tilePath(snap.dim(), snap.pos());
            final int[] px = pixels;
            ioExecutor.submit(() -> {
                try {
                    TilePngWriter.write(px, res, res, out);
                } catch (IOException ignored) {}
            });

            renderedCount.incrementAndGet();
        } catch (Throwable t) {
            failedCount.incrementAndGet();
            logger.error("[Render] 失败 " + snap.pos() + ": " + t.getMessage(), t);
        }
    }

    public int queueSize() { return queue.size(); }
    public TileMapCache getCache() { return cache; }
}