package com.meituan.logan.web.controller;

import com.meituan.logan.web.model.LoganTaskPageModel;
import com.meituan.logan.web.model.request.LoganTaskRequest;
import com.meituan.logan.web.service.LoganTaskService;
import com.meituan.logan.web.util.DateTimeUtil;
import org.junit.Test;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Collections;
import static org.junit.Assert.*;

public class LoganTaskPageTest {
    @Test
    public void validatesPageLimitsBeforeCallingService() {
        LoganController controller = new LoganController();
        assertEquals(400, controller.page(0, 20, null, null, null, 0).getCode());
        assertEquals(400, controller.page(1, 101, null, null, null, 0).getCode());
        assertEquals(400, controller.page(1, 0, null, null, null, 0).getCode());
        assertEquals(400, controller.page(1, 20, null, null, null, 4).getCode());
        assertEquals(400, controller.page(1, 20, null, 2L, 1L, 0).getCode());
        assertEquals(400, controller.page(1, 20, null, null, Long.MAX_VALUE, 0).getCode());
    }

    @Test
    public void acceptsAnUnfilteredPageAndPreservesExplicitFilters() throws Exception {
        LoganController controller = new LoganController();
        Field field = LoganController.class.getDeclaredField("taskService");
        field.setAccessible(true);
        field.set(controller, Proxy.newProxyInstance(LoganTaskService.class.getClassLoader(),
                new Class<?>[]{LoganTaskService.class}, (proxy, method, args) -> {
                    assertEquals("queryPage", method.getName());
                    LoganTaskRequest request = (LoganTaskRequest) args[0];
                    if (request.getPlatform() == 0) {
                        assertNull(request.getDeviceId());
                        assertNull(request.getBeginTime());
                        assertNull(request.getEndTime());
                    } else {
                        assertEquals("device", request.getDeviceId());
                        assertEquals(Long.valueOf(1), request.getBeginTime());
                        assertEquals(Long.valueOf(2 + DateTimeUtil.ONE_DAY), request.getEndTime());
                    }
                    return new LoganTaskPageModel(Collections.emptyList(), 45, (int) args[1], (int) args[2]);
                }));
        assertEquals(45, controller.page(2, 20, null, null, null, 0).getData().getTotal());
        assertEquals(3, controller.page(3, 10, " device ", 1L, 2L, 3).getData().getPage());
    }
}
