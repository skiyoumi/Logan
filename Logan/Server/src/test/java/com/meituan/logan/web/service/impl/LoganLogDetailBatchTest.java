package com.meituan.logan.web.service.impl;

import com.meituan.logan.web.dto.LoganLogDetailDTO;
import com.meituan.logan.web.mapper.LoganLogDetailMapper;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class LoganLogDetailBatchTest {
    @Test
    public void splitsLargeUtf8LogsWithoutTruncatingOrReordering() throws Exception {
        String content = String.join("", Collections.nCopies(400000, "日"));
        List<LoganLogDetailDTO> input = Arrays.asList(detail(content), detail(content), detail(content));
        List<List<LoganLogDetailDTO>> batches = new ArrayList<>();
        service(batches).execute(input);
        assertEquals(3, batches.size());
        for (int i = 0; i < input.size(); i++) {
            assertEquals(1, batches.get(i).size());
            assertSame(input.get(i), batches.get(i).get(0));
            assertEquals(content, batches.get(i).get(0).getContent());
        }
    }

    @Test
    public void writesOneLargeEntryIntactAndKeepsSmallEntriesBatched() throws Exception {
        String large = String.join("", Collections.nCopies(3 * 1024 * 1024, "a"));
        LoganLogDetailDTO largeEntry = detail(large);
        List<List<LoganLogDetailDTO>> batches = new ArrayList<>();
        service(batches).execute(Arrays.asList(largeEntry, detail("small"), detail("tail")));
        assertEquals(2, batches.size());
        assertSame(largeEntry, batches.get(0).get(0));
        assertEquals(2, batches.get(1).size());
    }

    @Test
    public void doesNotInsertAnEmptyBatch() throws Exception {
        List<List<LoganLogDetailDTO>> batches = new ArrayList<>();
        service(batches).execute(Collections.emptyList());
        assertTrue(batches.isEmpty());
    }

    private static LoganLogDetailDTO detail(String content) {
        LoganLogDetailDTO dto = new LoganLogDetailDTO();
        dto.setContent(content);
        return dto;
    }

    @SuppressWarnings("unchecked")
    private static LoganLogDetailServiceImpl service(List<List<LoganLogDetailDTO>> batches) throws Exception {
        LoganLogDetailServiceImpl service = new LoganLogDetailServiceImpl();
        LoganLogDetailMapper mapper = (LoganLogDetailMapper) Proxy.newProxyInstance(
                LoganLogDetailMapper.class.getClassLoader(), new Class<?>[] {LoganLogDetailMapper.class},
                (proxy, method, args) -> {
                    assertEquals("batchInsert", method.getName());
                    batches.add(new ArrayList<>((List<LoganLogDetailDTO>) args[0]));
                    return null;
                });
        Field field = LoganLogDetailServiceImpl.class.getDeclaredField("detailMapper");
        field.setAccessible(true);
        field.set(service, mapper);
        return service;
    }
}
