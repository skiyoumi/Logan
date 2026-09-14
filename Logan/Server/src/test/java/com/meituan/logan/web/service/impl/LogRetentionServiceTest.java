package com.meituan.logan.web.service.impl;

import com.meituan.logan.web.dto.LoganTaskDTO;
import com.meituan.logan.web.mapper.LogRetentionMapper;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class LogRetentionServiceTest {
    private static final long NOW = Instant.parse("2026-09-14T12:00:00Z").toEpochMilli();
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC);
    private static final long DAY = TimeUnit.DAYS.toMillis(1);
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private final Map<Long, LoganTaskDTO> tasks = new TreeMap<>();
    private final Map<Long, Long> webTasks = new TreeMap<>();
    private final List<Long> batchEnds = new ArrayList<>();
    private boolean failNativeQuery;
    private int nativeDetailPasses;
    private int webDetailPasses;

    @Test
    public void keepsExactCutoffAndNewUploadsRegardlessOfFileAgeAndUsesConfiguredDays() throws Exception {
        Path root = temporary.newFolder("logfile").toPath();
        addTask(1, "expired.log", NOW - 7 * DAY - 1);
        addTask(2, "boundary.log", NOW - 7 * DAY);
        addTask(3, "recent.log", NOW);
        for (LoganTaskDTO task : tasks.values()) {
            file(root, task.getLogFileName(), NOW - 30 * DAY);
        }
        new LogRetentionService(mapper(), 7, root, CLOCK).cleanup();
        assertFalse(Files.exists(root.resolve("expired.log")));
        assertTrue(Files.exists(root.resolve("boundary.log")));
        assertTrue(Files.exists(root.resolve("recent.log")));
        assertEquals(2, tasks.size());
        new LogRetentionService(mapper(), 3, root, CLOCK).cleanup();
        assertFalse(Files.exists(root.resolve("boundary.log")));
        assertTrue(tasks.containsKey(3L));
    }

    @Test
    public void failedFileDeletionIsRetriedWithoutBlockingOtherTasks() throws Exception {
        Path root = temporary.newFolder("logfile").toPath();
        Files.createDirectory(root.resolve("blocked.log"));
        addTask(1, "blocked.log", NOW - 8 * DAY);
        addTask(2, "later.log", NOW - 8 * DAY);
        file(root, "later.log", NOW);
        LogRetentionService service = new LogRetentionService(mapper(), 7, root, CLOCK);
        service.cleanup();
        assertTrue(tasks.containsKey(1L));
        assertFalse(tasks.containsKey(2L));
        Files.delete(root.resolve("blocked.log"));
        service.cleanup();
        assertTrue(tasks.isEmpty());
    }

    @Test
    public void preservesSharedFilesUntilEveryReferencingTaskExpires() throws Exception {
        Path root = temporary.newFolder("logfile").toPath();
        addTask(1, "legacy.log", NOW - 8 * DAY);
        addTask(2, "legacy.log", NOW);
        file(root, "legacy.log", NOW - 8 * DAY);
        new LogRetentionService(mapper(), 7, root, CLOCK).cleanup();
        assertFalse(tasks.containsKey(1L));
        assertTrue(tasks.containsKey(2L));
        assertTrue(Files.exists(root.resolve("legacy.log")));
    }

    @Test
    public void removesOnlyOldOrphanFilesAndDoesNotRecurseOrEscapeDirectory() throws Exception {
        Path root = temporary.newFolder("logfile").toPath();
        Path outside = file(root.getParent(), "outside.log", NOW - 8 * DAY);
        addTask(1, "../outside.log", NOW - 8 * DAY);
        addTask(2, outside.toString(), NOW - 8 * DAY);
        file(root, "old-orphan", NOW - 8 * DAY);
        file(root, "cutoff-orphan", NOW - 7 * DAY);
        file(root, "new-orphan", NOW);
        Path nested = Files.createDirectory(root.resolve("nested"));
        file(nested, "nested.log", NOW - 8 * DAY);
        new LogRetentionService(mapper(), 7, root, CLOCK).cleanup();
        assertFalse(Files.exists(root.resolve("old-orphan")));
        assertTrue(Files.exists(root.resolve("cutoff-orphan")));
        assertTrue(Files.exists(root.resolve("new-orphan")));
        assertTrue(Files.exists(nested.resolve("nested.log")));
        assertTrue(Files.exists(outside));
        assertEquals(2, tasks.size());
    }

    @Test
    public void doesNotFollowFileOrDirectorySymbolicLinks() throws Exception {
        org.junit.Assume.assumeFalse(System.getProperty("os.name").startsWith("Windows"));
        Path root = temporary.newFolder("logfile").toPath();
        Path outside = file(root.getParent(), "outside.log", NOW - 8 * DAY);
        Files.createSymbolicLink(root.resolve("linked.log"), outside);
        addTask(1, "linked.log", NOW - 8 * DAY);
        new LogRetentionService(mapper(), 7, root, CLOCK).cleanup();
        assertTrue(Files.exists(outside));
        assertTrue(Files.isSymbolicLink(root.resolve("linked.log")));
        assertTrue(tasks.containsKey(1L));
        Path linkedRoot = root.getParent().resolve("linked-root");
        Files.createSymbolicLink(linkedRoot, root);
        file(root, "old-orphan", NOW - 8 * DAY);
        new LogRetentionService(mapper(), 7, linkedRoot, CLOCK).cleanup();
        assertTrue(Files.exists(root.resolve("old-orphan")));
    }

    @Test
    public void batchesLargeBacklogsAndCleansDatabaseEvenWhenUploadDirectoryIsMissing() {
        Path root = temporary.getRoot().toPath().resolve("missing");
        for (long id = 1; id <= 1001; id++) {
            addTask(id, id + ".log", NOW - 8 * DAY);
            webTasks.put(id, NOW - 8 * DAY);
        }
        new LogRetentionService(mapper(), 7, root, CLOCK).cleanup();
        assertTrue(tasks.isEmpty());
        assertTrue(webTasks.isEmpty());
        assertEquals(java.util.Arrays.asList(500L, 1000L, 1001L), batchEnds);
    }

    @Test
    public void failedStageDoesNotStopOtherStagesOrFutureRuns() throws Exception {
        Path root = temporary.newFolder("logfile").toPath();
        addTask(1, "missing.log", NOW - 8 * DAY);
        webTasks.put(1L, NOW - 8 * DAY);
        failNativeQuery = true;
        LogRetentionService service = new LogRetentionService(mapper(), 7, root, CLOCK);
        service.cleanup();
        assertTrue(tasks.containsKey(1L));
        assertTrue(webTasks.isEmpty());
        assertEquals(1, nativeDetailPasses);
        assertEquals(1, webDetailPasses);
        failNativeQuery = false;
        service.cleanup();
        assertTrue(tasks.isEmpty());
    }

    @Test
    public void rejectsNonPositiveRetentionAndAcceptsLargePositiveValuesWithoutOverflow() {
        for (int days : new int[]{0, -1, Integer.MIN_VALUE}) {
            try {
                new LogRetentionService(mapper(), days, temporary.getRoot().toPath(), CLOCK);
                fail("Should reject " + days);
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage().contains("positive integer"));
            }
        }
        addTask(1, "missing.log", 1);
        new LogRetentionService(mapper(), Integer.MAX_VALUE, temporary.getRoot().toPath(), CLOCK).cleanup();
        assertTrue(tasks.containsKey(1L));
    }

    private void addTask(long id, String name, long time) {
        LoganTaskDTO task = new LoganTaskDTO();
        task.setId(id); task.setLogFileName(name); task.setAddTime(time);
        tasks.put(id, task);
    }

    private Path file(Path root, String name, long time) throws Exception {
        Path file = Files.write(root.resolve(name), new byte[]{1});
        Files.setLastModifiedTime(file, FileTime.fromMillis(time));
        return file;
    }

    @SuppressWarnings("unchecked")
    private LogRetentionMapper mapper() {
        return (LogRetentionMapper) Proxy.newProxyInstance(LogRetentionMapper.class.getClassLoader(),
                new Class<?>[]{LogRetentionMapper.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "expiredNativeTasks":
                            if (failNativeQuery) throw new IllegalStateException("database unavailable");
                            return tasks.values().stream().filter(t -> t.getAddTime() < (long) args[0] && t.getId() > (long) args[1])
                                    .limit((int) args[2]).collect(Collectors.toList());
                        case "expiredWebTasks":
                            return webTasks.entrySet().stream().filter(t -> t.getValue() < (long) args[0] && t.getKey() > (long) args[1])
                                    .limit((int) args[2]).map(Map.Entry::getKey).collect(Collectors.toList());
                        case "countFileReferences":
                            return (int) tasks.values().stream().filter(t -> args[0].equals(t.getLogFileName()) && t.getAddTime() >= (long) args[1]).count();
                        case "deleteNativeTasks":
                            List<Long> ids = (List<Long>) args[0];
                            assertTrue(ids.size() <= 500);
                            batchEnds.add(ids.get(ids.size() - 1));
                            ids.forEach(tasks::remove);
                            return ids.size();
                        case "deleteWebTasks":
                            ((List<Long>) args[0]).forEach(webTasks::remove);
                            return ((List<Long>) args[0]).size();
                        case "deleteOrphanNativeDetails": nativeDetailPasses++; return 0;
                        case "deleteOrphanWebDetails": webDetailPasses++; return 0;
                        default: throw new AssertionError(method.getName());
                    }
                });
    }
}
