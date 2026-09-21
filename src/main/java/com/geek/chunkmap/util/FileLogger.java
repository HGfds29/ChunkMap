package com.geek.chunkmap.util;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * 详细日志系统。
 *
 * 特性：
 *   - 六级：TRACE / DEBUG / INFO / WARN / ERROR / OFF
 *   - 运行时切换级别：setLevel(...)
 *   - 记录线程名
 *   - 计时器：beginTimer / endTimer / scope
 *   - 计数器：count / counterValue / dumpCounters
 *   - 单文件超过 MAX_FILE_BYTES 自动轮转
 *   - 自动清理 N 天前的旧日志
 *   - UTF-8、线程安全、每行 flush
 */
public class FileLogger {

    public enum Level {
        TRACE(0), DEBUG(1), INFO(2), WARN(3), ERROR(4), OFF(5);
        final int prio;
        Level(int p) { this.prio = p; }
        public boolean allows(Level lv) { return lv.prio >= this.prio; }
    }

    private static final DateTimeFormatter NAME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final DateTimeFormatter LINE_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    /** 单文件最大字节数，超过则轮转。 */
    private static final long MAX_FILE_BYTES = 16L * 1024 * 1024;
    /** 检查文件大小的最小间隔，避免每条日志都 stat。 */
    private static final long ROTATE_CHECK_MS = 10_000L;

    private final Path logDir = Paths.get("logs", "chunkmap");
    private final Object lock = new Object();
    private Writer writer;
    private Path currentFile;
    private volatile Level level = Level.INFO;

    private long lastRotateCheck = 0L;
    private int rotationIndex = 0;

    private final AtomicLong lineCount = new AtomicLong();
    private final ConcurrentHashMap<String, AtomicLong> counters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> timers = new ConcurrentHashMap<>();

    /** 从系统属性读取等级：-Dchunkmap.logLevel=TRACE */
    public static Level levelFromProperty(String key, Level def) {
        try {
            String v = System.getProperty(key);
            if (v == null || v.isBlank()) return def;
            return Level.valueOf(v.trim().toUpperCase(Locale.ROOT));
        } catch (Throwable t) {
            return def;
        }
    }

    public void setLevel(Level lv) {
        this.level = lv;
        info("[Logger] 日志级别 → " + lv);
    }
    public Level getLevel() { return level; }
    public boolean isEnabled(Level lv) { return level.allows(lv); }

    public void init() {
        synchronized (lock) {
            if (writer != null) return;
            try {
                Files.createDirectories(logDir);
                currentFile = newLogFile();
                openWriter();
                info("[Logger] 初始化，文件=" + currentFile.toAbsolutePath());
            } catch (IOException e) {
                System.err.println("[ChunkMap] 无法初始化日志文件: " + e.getMessage());
            }
        }
    }

    private Path newLogFile() {
        String name = "chunkmap_" + LocalDateTime.now().format(NAME_FMT);
        if (rotationIndex > 0) name += "_" + rotationIndex;
        return logDir.resolve(name + ".log");
    }

    private void openWriter() throws IOException {
        writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(currentFile.toFile(), true), StandardCharsets.UTF_8));
    }

    private void rotateIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastRotateCheck < ROTATE_CHECK_MS) return;
        lastRotateCheck = now;
        try {
            if (Files.exists(currentFile) && Files.size(currentFile) >= MAX_FILE_BYTES) {
                synchronized (lock) {
                    if (writer != null) { writer.flush(); writer.close(); writer = null; }
                    rotationIndex++;
                    currentFile = newLogFile();
                    openWriter();
                    writeRaw("[" + LocalDateTime.now().format(LINE_FMT) + "] "
                            + "[INFO] [Logger] 日志已轮转 → " + currentFile.getFileName());
                }
            }
        } catch (IOException ignored) {}
    }

    private void writeRaw(String line) {
        try {
            writer.write(line);
            writer.write(System.lineSeparator());
            writer.flush();
            lineCount.incrementAndGet();
        } catch (IOException e) {
            System.err.println(line);
        }
    }

    // ========================= 基础日志 =========================

    public void trace(String msg) { log(Level.TRACE, msg, null); }
    public void debug(String msg) { log(Level.DEBUG, msg, null); }
    public void info (String msg) { log(Level.INFO,  msg, null); }
    public void warn (String msg) { log(Level.WARN,  msg, null); }
    public void warn (String msg, Throwable t) { log(Level.WARN, msg, t); }
    public void error(String msg) { log(Level.ERROR, msg, null); }
    public void error(String msg, Throwable t) { log(Level.ERROR, msg, t); }

    public void log(Level lv, String msg, Throwable t) {
        if (!level.allows(lv)) return;
        String thread = Thread.currentThread().getName();
        StringBuilder sb = new StringBuilder(msg == null ? 0 : msg.length() + 64);
        sb.append('[').append(LocalDateTime.now().format(LINE_FMT)).append("] ")
          .append('[').append(lv.name()).append("] ")
          .append('[').append(thread).append("] ")
          .append(msg);
        synchronized (lock) {
            if (writer == null) {
                System.err.println(sb);
                if (t != null) t.printStackTrace();
                return;
            }
            writeRaw(sb.toString());
            if (t != null) writeRaw(stackTrace(t));
        }
        rotateIfNeeded();
    }

    private static String stackTrace(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    // ========================= 计时器 =========================

    /** 返回一个 nanoTime 起点；配合 endTimer 使用。 */
    public long beginTimer(String name) {
        long t = System.nanoTime();
        timers.put(name, t);
        if (level.allows(Level.TRACE)) trace("[Timer] → " + name);
        return t;
    }

    public void endTimer(String name) {
        Long start = timers.remove(name);
        if (start == null) return;
        long ms = (System.nanoTime() - start) / 1_000_000;
        if (level.allows(Level.DEBUG)) debug("[Timer] ← " + name + " (" + ms + "ms)");
    }

    /** 包一个代码块，自动记录起止和耗时。 */
    public TimerScope scope(String name) {
        return new TimerScope(this, name);
    }

    public static final class TimerScope implements AutoCloseable {
        private final FileLogger logger;
        private final String name;
        private final long start;

        TimerScope(FileLogger logger, String name) {
            this.logger = logger;
            this.name = name;
            this.start = System.nanoTime();
            if (logger.level.allows(Level.TRACE)) logger.trace("[Scope] → " + name);
        }

        @Override public void close() {
            long ms = (System.nanoTime() - start) / 1_000_000;
            if (logger.level.allows(Level.DEBUG)) logger.debug("[Scope] ← " + name + " (" + ms + "ms)");
        }
    }

    // ========================= 计数器 =========================

    public void count(String name) {
        counters.computeIfAbsent(name, k -> new AtomicLong()).incrementAndGet();
    }
    public void count(String name, long delta) {
        counters.computeIfAbsent(name, k -> new AtomicLong()).addAndGet(delta);
    }
    public long counterValue(String name) {
        AtomicLong a = counters.get(name);
        return a == null ? 0 : a.get();
    }
    public void dumpCounters() {
        if (counters.isEmpty()) return;
        info("===== 计数器汇总 =====");
        counters.forEach((k, v) -> info("  " + k + " = " + v.get()));
        info("=====================");
    }

    // ========================= 生命周期 =========================

    public void cleanOldLogs(int retentionDays) {
        if (retentionDays <= 0) return;
        LocalDate cutoff = LocalDate.now().minusDays(retentionDays);
        try (Stream<Path> paths = Files.list(logDir)) {
            paths.filter(p -> p.getFileName().toString().startsWith("chunkmap_")
                            && p.toString().endsWith(".log"))
                    .forEach(p -> {
                        try {
                            String fname = p.getFileName().toString();
                            String datePart = fname.substring("chunkmap_".length(),
                                    "chunkmap_".length() + 10);
                            LocalDate fileDate = LocalDate.parse(datePart);
                            if (fileDate.isBefore(cutoff)) {
                                Files.deleteIfExists(p);
                                info("[Logger] 已删除旧日志 " + fname);
                            }
                        } catch (Exception ignored) {}
                    });
        } catch (IOException ignored) {}
    }

    public void flush() {
        synchronized (lock) {
            if (writer != null) try { writer.flush(); } catch (IOException ignored) {}
        }
    }

    public void close() {
        synchronized (lock) {
            if (writer != null) {
                try { writer.flush(); writer.close(); } catch (IOException ignored) {}
                writer = null;
            }
        }
    }

    public Path getCurrentLogFile() { return currentFile; }
    public long getLineCount() { return lineCount.get(); }
}