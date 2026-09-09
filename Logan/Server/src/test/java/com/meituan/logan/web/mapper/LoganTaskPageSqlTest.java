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
}
