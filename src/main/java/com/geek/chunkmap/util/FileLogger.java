package com.geek.chunkmap.util;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.stream.Stream;

public class FileLogger {

    public enum Level {
        TRACE(0), DEBUG(1), INFO(2), WARN(3), ERROR(4), OFF(5);
        final int prio;
        Level(int p) { this.prio = p; }
    }

    private static final DateTimeFormatter NAME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final DateTimeFormatter LINE_FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final Path logDir = Paths.get("logs", "chunkmap");
    private final Object lock = new Object();
    private Writer writer;
    private Path currentFile;
    private volatile Level level = Level.INFO;

    /** 从系统属性读取等级，例：-Dchunkmap.logLevel=TRACE */
    public static Level levelFromProperty(String key, Level defaultLevel) {
        try {
            String v = System.getProperty(key);
            if (v == null || v.isBlank()) return defaultLevel;
            return Level.valueOf(v.trim().toUpperCase(Locale.ROOT));
        } catch (Throwable t) {
            return defaultLevel;
        }
    }

    public void setLevel(Level lv) {
        this.level = lv;
        info("[FileLogger] 日志级别设为 " + lv);
    }

    public Level getLevel() { return level; }

    public void init() {
        synchronized (lock) {
            if (writer != null) return;
            try {
                Files.createDirectories(logDir);
                String name = "chunkmap_" + LocalDateTime.now().format(NAME_FMT) + ".log";
                currentFile = logDir.resolve(name);
                writer = new FileWriter(currentFile.toFile(), true);
            } catch (IOException e) {
                System.err.println("[ChunkMap] 无法初始化日志文件: " + e.getMessage());
            }
        }
    }

    public void trace(String msg) { log(Level.TRACE, msg, null); }
    public void debug(String msg) { log(Level.DEBUG, msg, null); }
    public void info(String msg)  { log(Level.INFO, msg, null); }
    public void warn(String msg)  { log(Level.WARN, msg, null); }
    public void error(String msg, Throwable t) { log(Level.ERROR, msg, t); }

    private void log(Level lv, String msg, Throwable t) {
        if (lv.prio < level.prio) return;
        String thread = Thread.currentThread().getName();
        String line = "[" + LocalDateTime.now().format(LINE_FMT) + "] "
                + "[" + lv.name() + "] "
                + "[" + thread + "] "
                + msg;
        synchronized (lock) {
            if (writer != null) {
                try {
                    writer.write(line);
                    writer.write(System.lineSeparator());
                    if (t != null) {
                        writer.write(stackTrace(t));
                        writer.write(System.lineSeparator());
                    }
                    writer.flush();
                } catch (IOException ignored) {
                    System.err.println(line);
                }
            } else {
                System.err.println(line);
            }
        }
    }

    private String stackTrace(Throwable t) {
        java.io.StringWriter sw = new java.io.StringWriter();
        t.printStackTrace(new java.io.PrintWriter(sw));
        return sw.toString();
    }

    public void cleanOldLogs(int retentionDays) {
        if (retentionDays <= 0) return;
        LocalDate cutoff = LocalDate.now().minusDays(retentionDays);
        try (Stream<Path> paths = Files.list(logDir)) {
            paths.filter(p -> p.getFileName().toString().startsWith("chunkmap_")
                            && p.toString().endsWith(".log"))
                    .forEach(p -> {
                        try {
                            String fname = p.getFileName().toString();
                            String datePart = fname.substring("chunkmap_".length(), "chunkmap_".length() + 10);
                            LocalDate fileDate = LocalDate.parse(datePart);
                            if (fileDate.isBefore(cutoff)) Files.deleteIfExists(p);
                        } catch (Exception ignored) {}
                    });
        } catch (IOException ignored) {}
    }

    public void close() {
        synchronized (lock) {
            if (writer != null) {
                try { writer.close(); } catch (IOException ignored) {}
                writer = null;
            }
        }
    }

    public Path getCurrentLogFile() { return currentFile; }
}