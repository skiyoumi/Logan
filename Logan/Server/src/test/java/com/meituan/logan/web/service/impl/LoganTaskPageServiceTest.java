package com.meituan.logan.web.service.impl;

import com.meituan.logan.web.dto.LoganTaskDTO;
import com.meituan.logan.web.mapper.LoganTaskMapper;
import com.meituan.logan.web.model.LoganTaskPageModel;
import com.meituan.logan.web.model.request.LoganTaskRequest;
import org.junit.Test;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Collections;
import static org.junit.Assert.*;

public class LoganTaskPageServiceTest {
    @Test
    public void fetchesOnlyTheRequestedPageAndSkipsOutOfRangeQueries() throws Exception {
        LoganTaskRequest request = new LoganTaskRequest("device", 1L, 2L, 3);
        int[] queries = {0};
        LoganTaskServiceImpl service = new LoganTaskServiceImpl();
        Field field = LoganTaskServiceImpl.class.getDeclaredField("taskMapper");
        field.setAccessible(true);
        field.set(service, Proxy.newProxyInstance(LoganTaskMapper.class.getClassLoader(),
                new Class<?>[]{LoganTaskMapper.class}, (proxy, method, args) -> {
                    assertSame(request, args[0]);
                    if (method.getName().equals("countPage")) return 45L;
                    assertEquals("queryPage", method.getName());
                    assertEquals(20L, args[1]);
                    assertEquals(20, args[2]);
                    queries[0]++;
                    LoganTaskDTO dto = new LoganTaskDTO(); dto.setId(25);
                    return Collections.singletonList(dto);
                }));
        LoganTaskPageModel page = service.queryPage(request, 2, 20);
        assertEquals(45, page.getTotal());
        assertEquals(25, page.getItems().get(0).getTaskId());
        assertTrue(service.queryPage(request, Integer.MAX_VALUE, 100).getItems().isEmpty());
        assertEquals(1, queries[0]);
    }
}
