package com.meituan.logan.web.service.impl;

import com.meituan.logan.web.mapper.LogRetentionMapper;
import com.meituan.logan.web.mapper.WebLogTaskMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/** Opt in with LOGAN_RETENTION_TEST_JDBC_URL pointing at a disposable logan_retention_test database. */
public class LogRetentionMySqlTest {
    private static final long NOW = Instant.parse("2026-09-14T12:00:00Z").toEpochMilli();
    private static final long CUTOFF = NOW - TimeUnit.DAYS.toMillis(7);
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private Connection connection;
    private SqlSession session;
    private LogRetentionMapper mapper;

    @Before
    public void setup() throws Exception {
        String url = System.getenv("LOGAN_RETENTION_TEST_JDBC_URL");
        Assume.assumeTrue("Requires an isolated MySQL test database", url != null && !url.isEmpty());
        DriverManagerDataSource source = new DriverManagerDataSource(url,
                System.getenv("LOGAN_RETENTION_TEST_DB_USER"), System.getenv("LOGAN_RETENTION_TEST_DB_PASSWORD"));
        connection = source.getConnection();
        assertEquals("Refusing to modify a non-test database", "logan_retention_test", connection.getCatalog());
        sql("DROP TABLE IF EXISTS logan_log_detail, web_detail, logan_task, web_task");
        sql("CREATE TABLE logan_task (id BIGINT UNSIGNED PRIMARY KEY, log_file_name VARCHAR(512), " +
                "add_time BIGINT UNSIGNED NOT NULL, log_date BIGINT, INDEX idx_file(log_file_name(191)), INDEX idx_time(add_time,id)) ENGINE=InnoDB");
        sql("CREATE TABLE web_task (id BIGINT UNSIGNED PRIMARY KEY, add_time BIGINT NOT NULL, " +
                "content MEDIUMTEXT, log_date BIGINT) ENGINE=InnoDB");
        sql("CREATE TABLE logan_log_detail (id BIGINT UNSIGNED PRIMARY KEY, task_id BIGINT UNSIGNED NOT NULL, " +
                "content MEDIUMTEXT, INDEX idx_task(task_id)) ENGINE=InnoDB");
        sql("CREATE TABLE web_detail (id BIGINT UNSIGNED PRIMARY KEY, task_id BIGINT NOT NULL, " +
                "content MEDIUMTEXT, INDEX idx_task(task_id)) ENGINE=InnoDB");
        Configuration configuration = new Configuration(new Environment("test", new JdbcTransactionFactory(), source));
        for (String name : new String[]{"LogRetentionMapper", "WebLogTaskMapper"}) {
            String resource = "sqlmap/" + name + ".xml";
            try (InputStream input = getClass().getResourceAsStream("/" + resource)) {
                new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
        session = new SqlSessionFactoryBuilder().build(configuration).openSession(true);
        mapper = session.getMapper(LogRetentionMapper.class);
    }

    @After
    public void close() throws Exception {
        if (session != null) session.close();
        if (connection != null) connection.close();
    }

    @Test
    public void cleansFilesAndBothTaskTypesWithDetailsAndPreservesExactBoundary() throws Exception {
        Path root = temporary.newFolder("logfile").toPath();
        for (int id = 1; id <= 3; id++) {
            long time = id == 1 ? CUTOFF - 1 : id == 2 ? CUTOFF : NOW;
            // Client dates and file timestamps must not determine task retention.
            sql("INSERT INTO logan_task VALUES (" + id + ", '" + id + ".log', " + time + ", 1)");
            sql("INSERT INTO web_task VALUES (" + id + ", " + time + ", 'web log', 1)");
            sql("INSERT INTO logan_log_detail VALUES (" + id + ", " + id + ", 'parsed native')");
            sql("INSERT INTO web_detail VALUES (" + id + ", " + id + ", 'parsed web')");
            Path file = Files.write(root.resolve(id + ".log"), new byte[]{1});
            Files.setLastModifiedTime(file, FileTime.fromMillis(1));
        }
        sql("INSERT INTO logan_task VALUES (4, 'missing.log', " + (CUTOFF - 1) + ", 1)");
        sql("INSERT INTO logan_log_detail VALUES (4, 4, 'missing file detail'), (99, 999, 'orphan')");
        sql("INSERT INTO web_detail VALUES (99, 999, 'orphan')");
        Path orphan = Files.write(root.resolve("orphan.log"), new byte[]{1});
        Files.setLastModifiedTime(orphan, FileTime.fromMillis(1));
        LogRetentionService service = new LogRetentionService(mapper, 7, root,
                Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC));
        service.cleanup();
        for (String table : new String[]{"logan_task", "web_task", "logan_log_detail", "web_detail"}) {
            assertEquals(table, 2, count("SELECT COUNT(*) FROM " + table));
            assertEquals(table, 0, count("SELECT COUNT(*) FROM " + table + " WHERE id NOT IN (2, 3)"));
        }
        assertFalse(Files.exists(root.resolve("1.log")));
        assertFalse(Files.exists(orphan));
        assertTrue(Files.exists(root.resolve("2.log")));
        assertTrue(Files.exists(root.resolve("3.log")));
        service.cleanup();
        assertEquals(2, count("SELECT COUNT(*) FROM logan_task"));
    }

    @Test
    public void refreshOfWebUploadBetweenSelectionAndDeletionPreservesTaskAndDetails() throws Exception {
        sql("INSERT INTO web_task VALUES (1, " + (CUTOFF - 1) + ", 'old', 1), (2, " + CUTOFF + ", 'boundary', 1)");
        sql("INSERT INTO web_detail VALUES (1, 1, 'detail'), (2, 2, 'boundary detail')");
        assertEquals(Arrays.asList(1L), mapper.expiredWebTasks(CUTOFF, 0, 500));
        session.getMapper(WebLogTaskMapper.class).updateContent(1, "re-uploaded", NOW);
        assertEquals(0, mapper.deleteWebTasks(Arrays.asList(1L, 2L), CUTOFF));
        assertEquals(NOW, count("SELECT add_time FROM web_task WHERE id=1"));
        assertEquals(2, count("SELECT COUNT(*) FROM web_detail"));
    }

    @Test
    public void batchesOrphanDetailsAndPreservesReferencedOnes() throws Exception {
        sql("INSERT INTO logan_task VALUES (1, 'retained.log', " + NOW + ", 1)");
        sql("INSERT INTO logan_log_detail VALUES (1, 1, 'keep'), (2, 99, 'orphan'), (3, 99, 'orphan')");
        sql("INSERT INTO web_task VALUES (1, " + NOW + ", 'keep', 1)");
        sql("INSERT INTO web_detail VALUES (1, 1, 'keep'), (2, 99, 'orphan'), (3, 99, 'orphan')");
        assertEquals(1, mapper.deleteOrphanNativeDetails(1));
        assertEquals(1, mapper.deleteOrphanNativeDetails(1));
        assertEquals(0, mapper.deleteOrphanNativeDetails(1));
        assertEquals(1, mapper.deleteOrphanWebDetails(1));
        assertEquals(1, mapper.deleteOrphanWebDetails(1));
        assertEquals(0, mapper.deleteOrphanWebDetails(1));
        assertEquals(1, mapper.countFileReferences("retained.log", 0));
        assertEquals(0, mapper.countFileReferences("retained.log", NOW + 1));
    }

    private void sql(String sql) throws Exception {
        try (Statement statement = connection.createStatement()) { statement.execute(sql); }
    }

    private long count(String sql) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getLong(1);
        }
    }
}
