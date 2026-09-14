package com.meituan.logan.web.mapper;

import com.meituan.logan.web.model.request.LoganTaskRequest;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.Test;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

public class LoganTaskPageSqlTest {
    @Test
    public void countAndPageUseTheSameFiltersAndBoundLimitOffset() throws Exception {
        Configuration config = new Configuration();
        try (InputStream input = getClass().getResourceAsStream("/sqlmap/LoganTaskMapper.xml")) {
            new XMLMapperBuilder(input, config, "sqlmap/LoganTaskMapper.xml", config.getSqlFragments()).parse();
        }
        Map<String, Object> args = new HashMap<>();
        LoganTaskRequest request = new LoganTaskRequest("device", 1L, 2L, 3);
        request.setAppId("app"); request.setAppVersion("2.1.6"); request.setUnionId("用户+甲&乙");
        args.put("request", request);
        args.put("limit", 20); args.put("offset", 40L);
        BoundSql page = config.getMappedStatement("com.meituan.logan.web.mapper.LoganTaskMapper.queryPage").getBoundSql(args);
        String count = config.getMappedStatement("com.meituan.logan.web.mapper.LoganTaskMapper.countPage").getBoundSql(args).getSql().replaceAll("\\s+", " ").trim();
        String sql = page.getSql().replaceAll("\\s+", " ").trim();
        assertTrue(sql.contains("app_id = ? AND app_version = ? AND union_id = ?"));
        assertEquals("request.appId", page.getParameterMappings().get(0).getProperty());
        assertEquals("request.appVersion", page.getParameterMappings().get(1).getProperty());
        assertEquals("request.unionId", page.getParameterMappings().get(2).getProperty());
        assertTrue(sql.endsWith("ORDER BY id DESC LIMIT ? OFFSET ?"));
        assertEquals(count.substring(count.indexOf("WHERE")), sql.substring(sql.indexOf("WHERE"), sql.indexOf(" ORDER BY")));
        assertEquals("limit", page.getParameterMappings().get(7).getProperty());
        assertEquals("offset", page.getParameterMappings().get(8).getProperty());
        args.put("request", new LoganTaskRequest(null, null, null, 0));
        assertFalse(config.getMappedStatement("com.meituan.logan.web.mapper.LoganTaskMapper.queryPage").getBoundSql(args).getSql().contains("WHERE"));
    }
    @Test
    public void wildcardUnionIdsUseBoundLikePatternsForBothCountAndPage() throws Exception {
        Configuration config = new Configuration();
        try (InputStream input = getClass().getResourceAsStream("/sqlmap/LoganTaskMapper.xml")) {
            new XMLMapperBuilder(input, config, "sqlmap/LoganTaskMapper.xml", config.getSqlFragments()).parse();
        }
        String[][] examples = {
                {"*张三*", "%张三%"}, {"%张三%", "%张三%"},
                {"138*", "138%"}, {"*张三", "%张三"}, {"138*张*", "138%张%"},
                {"138%张*", "138%张%"}, {"*user_01*", "%user!_01%"},
                {"*user!_01*", "%user!!!_01%"}, {"*用户+甲&乙*", "%用户+甲&乙%"},
                {"*C:\\logs\\_user*", "%C:\\logs\\!_user%"},
                {"*' OR 1=1 --*", "%' OR 1=1 --%"}, {"*", "%"}
        };
        for (String[] example : examples) {
            LoganTaskRequest request = new LoganTaskRequest(null, null, null, 0);
            request.setUnionId(example[0]);
            Map<String, Object> args = new HashMap<>();
            args.put("request", request); args.put("limit", 20); args.put("offset", 20L);
            for (String statement : new String[]{"countPage", "queryPage"}) {
                BoundSql bound = config.getMappedStatement("com.meituan.logan.web.mapper.LoganTaskMapper." + statement).getBoundSql(args);
                String sql = bound.getSql().replaceAll("\\s+", " ").trim();
                assertTrue(example[0] + ": " + sql, sql.contains("union_id LIKE ? ESCAPE '!'"));
                assertEquals("request.unionIdPattern", bound.getParameterMappings().get(0).getProperty());
                assertEquals(example[1], config.newMetaObject(args).getValue("request.unionIdPattern"));
                assertFalse(sql.contains("' OR 1=1 --"));
            }
            assertEquals(example[0], request.getUnionId());
        }
    }

    @Test
    public void unionIdsWithoutWildcardsRemainExact() throws Exception {
        Configuration config = new Configuration();
        try (InputStream input = getClass().getResourceAsStream("/sqlmap/LoganTaskMapper.xml")) {
            new XMLMapperBuilder(input, config, "sqlmap/LoganTaskMapper.xml", config.getSqlFragments()).parse();
        }
        for (String unionId : new String[]{"138_张三", "用户+甲&乙", "user!_01", "C:\\logs\\user"}) {
            LoganTaskRequest request = new LoganTaskRequest(null, null, null, 0);
            request.setUnionId(unionId);
            Map<String, Object> args = new HashMap<>();
            args.put("request", request); args.put("limit", 20); args.put("offset", 0L);
            for (String statement : new String[]{"countPage", "queryPage"}) {
                BoundSql bound = config.getMappedStatement("com.meituan.logan.web.mapper.LoganTaskMapper." + statement).getBoundSql(args);
                assertTrue(bound.getSql().contains("union_id = ?"));
                assertFalse(bound.getSql().contains("LIKE"));
                assertEquals("request.unionId", bound.getParameterMappings().get(0).getProperty());
                assertEquals(unionId, config.newMetaObject(args).getValue("request.unionId"));
            }
        }
    }
}
