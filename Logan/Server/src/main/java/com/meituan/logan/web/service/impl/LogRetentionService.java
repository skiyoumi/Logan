package com.meituan.logan.web.service.impl;

import com.meituan.logan.web.dto.LoganTaskDTO;
import com.meituan.logan.web.mapper.LogRetentionMapper;
import com.meituan.logan.web.util.FileUtil;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class LogRetentionService {
    private static final Logger LOGGER = Logger.getLogger(LogRetentionService.class);
    private static final int BATCH_SIZE = 500;
    private final LogRetentionMapper mapper;
    private final long retentionMillis;
    private final Path directory;
    private final Clock clock;

    @Autowired
    public LogRetentionService(LogRetentionMapper mapper, @Value("${logan.retention-days:3}") int days) {
        this(mapper, days, FileUtil.getLogDirectory().toPath(), Clock.systemUTC());
    }

    LogRetentionService(LogRetentionMapper mapper, int days, Path directory, Clock clock) {
        if (days <= 0) {
            throw new IllegalArgumentException("logan.retention-days must be a positive integer");
        }
        this.mapper = mapper;
        this.retentionMillis = TimeUnit.DAYS.toMillis(days);
        this.directory = directory.toAbsolutePath().normalize();
        this.clock = clock;
    }

    @Scheduled(initialDelayString = "${logan.retention.initial-delay-ms:60000}",
            fixedDelayString = "${logan.retention.cleanup-interval-ms:3600000}")
    public void cleanup() {
        long cutoff = clock.millis() - retentionMillis;
        runSafely("native tasks", () -> cleanupNativeTasks(cutoff));
        runSafely("web tasks", () -> cleanupWebTasks(cutoff));
        runSafely("orphan native details", () -> {
            while (!Thread.currentThread().isInterrupted() && mapper.deleteOrphanNativeDetails(BATCH_SIZE) > 0) { }
        });
        runSafely("orphan web details", () -> {
            while (!Thread.currentThread().isInterrupted() && mapper.deleteOrphanWebDetails(BATCH_SIZE) > 0) { }
        });
        runSafely("orphan files", () -> cleanupOrphanFiles(cutoff));
    }

    private void cleanupNativeTasks(long cutoff) {
        long afterId = 0;
        while (!Thread.currentThread().isInterrupted()) {
            List<LoganTaskDTO> tasks = mapper.expiredNativeTasks(cutoff, afterId, BATCH_SIZE);
            if (tasks.isEmpty()) {
                return;
            }
            List<Long> deleted = new ArrayList<>();
            for (LoganTaskDTO task : tasks) {
                if (deleteTaskFile(task.getLogFileName(), cutoff)) {
                    deleted.add(task.getId());
                }
                afterId = task.getId();
            }
            if (!deleted.isEmpty()) {
                mapper.deleteNativeTasks(deleted, cutoff);
            }
        }
    }

    private void cleanupWebTasks(long cutoff) {
        long afterId = 0;
        while (!Thread.currentThread().isInterrupted()) {
            List<Long> ids = mapper.expiredWebTasks(cutoff, afterId, BATCH_SIZE);
            if (ids.isEmpty()) {
                return;
            }
            mapper.deleteWebTasks(ids, cutoff);
            afterId = ids.get(ids.size() - 1);
        }
    }

    private boolean deleteTaskFile(String fileName, long cutoff) {
        if (fileName == null || fileName.isEmpty()) {
            return true;
        }
        try {
            Path file = directory.resolve(fileName).normalize();
            if (!directory.equals(file.getParent()) || fileName.contains("/") || fileName.contains("\\")) {
                throw new IOException("Log filename is outside the upload directory: " + fileName);
            }
            // Legacy uploads could reuse filenames. Preserve files still used by retained tasks.
            if (mapper.countFileReferences(fileName, cutoff) > 0) {
                return true;
            }
            verifyDirectory();
            BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!attrs.isRegularFile()) {
                throw new IOException("Refusing to delete a non-regular log file: " + file);
            }
            Files.deleteIfExists(file);
            return true;
        } catch (NoSuchFileException e) {
            // Missing files (including a previously removed upload directory) are already reclaimed.
            return true;
        } catch (Exception e) {
            LOGGER.error("Log retention could not remove file; task will be retried: " + fileName, e);
            return false;
        }
    }

    private void cleanupOrphanFiles(long cutoff) {
        try {
            verifyDirectory();
            try (DirectoryStream<Path> files = Files.newDirectoryStream(directory)) {
                for (Path file : files) {
                    if (Thread.currentThread().isInterrupted()) {
                        return;
                    }
                    try {
                        BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                        if (attrs.isRegularFile() && attrs.lastModifiedTime().toMillis() < cutoff &&
                                mapper.countFileReferences(file.getFileName().toString(), 0) == 0) {
                            Files.deleteIfExists(file);
                        }
                    } catch (NoSuchFileException ignored) {
                        // Another cleanup may have removed the file.
                    } catch (Exception e) {
                        LOGGER.error("Log retention could not remove orphan file: " + file, e);
                    }
                }
            }
        } catch (NoSuchFileException ignored) {
            // No native logs have been uploaded yet.
        } catch (IOException e) {
            throw new IllegalStateException("Cannot scan log upload directory", e);
        }
    }

    private void verifyDirectory() throws IOException {
        if (!Files.readAttributes(directory, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).isDirectory() ||
                !directory.toRealPath().equals(directory)) {
            throw new IOException("Refusing to clean a linked upload directory: " + directory);
        }
    }

    private void runSafely(String stage, Runnable cleanup) {
        try {
            cleanup.run();
        } catch (Exception e) {
            LOGGER.error("Log retention failed for " + stage + "; will retry on the next run", e);
        }
    }
}
