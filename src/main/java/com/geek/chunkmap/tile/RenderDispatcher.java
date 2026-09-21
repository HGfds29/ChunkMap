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
    private final AtomicLong skippedCount = new AtomicLong();

    private final AtomicLong workerGen = new AtomicLong();

    private volatile ExecutorService workers;
    private final ExecutorService ioExecutor;
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
        logger.debug("[Dispatcher] 构造完成");
    }

    public void updateConfig(TileMapConfig newConfig) {
        boolean outputChanged = !this.config.outputDir().equals(newConfig.outputDir());
        int oldThreads = this.config.renderThreads();
        this.config = newConfig;

        logger.info("[Dispatcher] updateConfig oldThreads=" + oldThreads
                + " newThreads=" + newConfig.renderThreads()
                + " outputChanged=" + outputChanged);

        if (outputChanged) {
            this.storage = new TileStorage(newConfig.outputDir());
            logger.info("[Dispatcher] outputDir 变化 → " + newConfig.outputDir());
        }

        if (running && newConfig.renderThreads() != oldThreads) {
            restartWorkers(newConfig.renderThreads());
        }
    }

    public TileMapConfig getConfig() { return config; }
    public TileStorage getStorage() { return storage; }

    public void setRenderer(ChunkTopDownRenderer r) {
        this.renderer = r;
        logger.info("[Dispatcher] renderer 替换 class=" + (r == null ? "null" : r.getClass().getSimpleName()));
    }
    public void setSnapshotter(ChunkSnapshotter s) {
        this.snapshotter = s;
        logger.info("[Dispatcher] snapshotter 替换 class=" + (s == null ? "null" : s.getClass().getSimpleName()));
    }

    public void enqueueRender(ChunkPos pos) {
        if (pos == null) return;
        if (!running) {
            logger.count("dispatch.enqueue.rejected.not_running");
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread()) {
            logger.count("dispatch.enqueue.hop_to_main");
            mc.execute(() -> enqueueRender(pos));
            return;
        }

        ClientLevel level = mc.level;
        if (level == null) {
            logger.count("dispatch.enqueue.rejected.no_level");
            return;
        }
        if (!pending.add(pos)) {
            logger.count("dispatch.enqueue.rejected.dup");
            if (logger.isEnabled(FileLogger.Level.TRACE))
                logger.trace("[Dispatch] dup skip " + pos);
            return;
        }

        ChunkSnapshot snap;
        try (var s = logger.scope("dispatch.snapshot")) {
            snap = snapshotter.snapshot(level, pos);
        }
        if (snap == null) {
            pending.remove(pos);
            skippedCount.incrementAndGet();
            logger.count("dispatch.enqueue.rejected.snapshot_null");
            if (logger.isEnabled(FileLogger.Level.TRACE))
                logger.trace("[Dispatch] snapshot null " + pos);
            return;
        }

        boolean offered = queue.offer(snap);
        if (offered) {
            enqueuedCount.incrementAndGet();
            logger.count("dispatch.enqueued");
            if (logger.isEnabled(FileLogger.Level.TRACE))
                logger.trace("[Dispatch] enqueued " + pos + " queue=" + queue.size());
        } else {
            logger.count("dispatch.enqueue.rejected.offer_failed");
        }
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
            final int id = i;
            workers.submit(() -> workerLoop(gen, id));
        }
        logger.info("[Dispatcher] 启动，工作线程数=" + n + " gen=" + gen);
    }

    private void restartWorkers(int n) {
        ExecutorService old = this.workers;
        long gen = workerGen.incrementAndGet();
        this.workers = buildWorkerPool(n);
        for (int i = 0; i < n; i++) {
            final int id = i;
            workers.submit(() -> workerLoop(gen, id));
        }
        if (old != null) old.shutdown();
        logger.info("[Dispatcher] 工作线程数 → " + n + " gen=" + gen);
    }

    public void shutdown() {
        logger.info("[Dispatcher] shutdown 开始 running=" + running
                + " queue=" + queue.size() + " pending=" + pending.size());
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
        logger.info("[Dispatcher] 关闭完成 enqueued=" + enqueuedCount.get()
                + " rendered=" + renderedCount.get()
                + " failed=" + failedCount.get()
                + " skipped=" + skippedCount.get());
    }

    public void setCurrentDimension(ResourceKey<Level> dim) {
        if (Objects.equals(this.currentDimension, dim)) return;
        ResourceKey<Level> old = this.currentDimension;
        this.currentDimension = dim;
        logger.info("[Dispatcher] 维度 " + (old == null ? "null" : old.identifier())
                + " → " + (dim == null ? "null" : dim.identifier()));
    }

    public ResourceKey<Level> getCurrentDimension() { return currentDimension; }

    private void workerLoop(long gen, int workerId) {
        if (logger.isEnabled(FileLogger.Level.DEBUG))
            logger.debug("[Worker-" + workerId + "] 启动 gen=" + gen);
        while (running && gen == workerGen.get()) {
            try {
                ChunkSnapshot snap = queue.poll(100, TimeUnit.MILLISECONDS);
                if (snap == null) continue;
                pending.remove(snap.pos());
                if (logger.isEnabled(FileLogger.Level.TRACE)) {
                    logger.trace("[Worker-" + workerId + "] 处理 " + snap.pos()
                            + " 剩余队列=" + queue.size());
                }
                processJob(snap);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.debug("[Worker-" + workerId + "] 中断退出");
                return;
            } catch (Throwable t) {
                logger.error("[Worker-" + workerId + "] 异常", t);
            }
        }
        if (logger.isEnabled(FileLogger.Level.DEBUG))
            logger.debug("[Worker-" + workerId + "] 退出 gen=" + gen);
    }

    private void processJob(ChunkSnapshot snap) {
        long t0 = System.nanoTime();
        try {
            ChunkTopDownRenderer r = this.renderer;
            if (r == null) {
                logger.warn("[Render] renderer 为 null，跳过 " + snap.pos());
                return;
            }
            int[] pixels = r.renderChunk(snap);
            cache.put(snap.dim(), snap.pos(), pixels);

            final int res = config.tileResolution();
            final TileStorage st = this.storage;
            final Path out = st.tilePath(snap.dim(), snap.pos());
            final int[] px = pixels;
            final ChunkPos pos = snap.pos();
            final FileLogger lg = this.logger;
            ioExecutor.submit(() -> {
                try {
                    TilePngWriter.write(px, res, res, out);
                } catch (IOException e) {
                    lg.error("[IO] 写入瓦片失败 " + pos + " → " + out
                            + ": " + e.getMessage(), e);
                } catch (Throwable t) {
                    lg.error("[IO] 写入瓦片异常 " + pos, t);
                }
            });

            renderedCount.incrementAndGet();
            logger.count("render.done");
            if (logger.isEnabled(FileLogger.Level.DEBUG)) {
                long ms = (System.nanoTime() - t0) / 1_000_000;
                logger.debug("[Render] " + snap.pos() + " 完成 " + ms + "ms");
            }
        } catch (Throwable t) {
            failedCount.incrementAndGet();
            logger.count("render.fail");
            logger.error("[Render] 失败 " + snap.pos(), t);
        }
    }

    public int queueSize() { return queue.size(); }
    public TileMapCache getCache() { return cache; }
}