package com.meituan.logan.web.controller;

import com.meituan.logan.web.enums.ResultEnum;
import com.meituan.logan.web.model.LoganTaskModel;
import com.meituan.logan.web.model.response.LoganResponse;
import com.meituan.logan.web.service.LoganLogFileService;
import com.meituan.logan.web.service.LoganTaskService;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class LoganUploadControllerTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void accepts256CharacterAppIdAndDeviceIdWithoutLongFilenames() throws Exception {
        String appId = String.join("", Collections.nCopies(256, "a"));
        LoganUploadController controller = new LoganUploadController();
        List<LoganTaskModel> saved = new ArrayList<>();
        List<String> files = new ArrayList<>();
        set(controller, "fileService", (LoganLogFileService) (stream, name) -> {
            assertTrue(name.matches("[0-9a-f-]{36}\\.log"));
            try {
                Files.createFile(folder.getRoot().toPath().resolve(name));
            } catch (Exception e) {
                throw new AssertionError(e);
            }
            files.add(name);
            return ResultEnum.SUCCESS;
        });
        set(controller, "taskService", Proxy.newProxyInstance(LoganTaskService.class.getClassLoader(),
                new Class<?>[] {LoganTaskService.class}, (proxy, method, args) -> {
                    assertEquals("insertTask", method.getName());
                    saved.add((LoganTaskModel) args[0]);
                    return 1L;
                }));
        assertEquals(200, controller.upload(request(appId, true)).getCode());
        assertEquals(200, controller.upload(request(appId, true)).getCode());
        assertEquals(appId, saved.get(0).getAppId());
        assertEquals(256, saved.get(0).getDeviceId().length());
        assertNotEquals(files.get(0), files.get(1));
    }

    @Test
    public void rejects257CharactersBeforeReadingTheBodyOrCallingServices() throws Exception {
        LoganUploadController controller = new LoganUploadController();
        String appId = String.join("", Collections.nCopies(257, "a"));
        LoganResponse<String> result = controller.upload(request(appId, false));
        assertEquals(400, result.getCode());
        assertEquals("appId must not exceed 256 characters", result.getMsg());
    }

    private static HttpServletRequest request(String appId, boolean allowBody) {
        return (HttpServletRequest) Proxy.newProxyInstance(HttpServletRequest.class.getClassLoader(),
                new Class<?>[] {HttpServletRequest.class}, (proxy, method, args) -> {
                    if ("getHeader".equals(method.getName())) {
                        switch ((String) args[0]) {
                            case "appId": return appId;
                            case "platform": return "3";
                            case "deviceId": return String.join("", Collections.nCopies(256, "d"));
                            case "fileDate": return "2026-09-08";
                            default: return null;
                        }
                    }
                    if ("getContentLength".equals(method.getName())) return 0;
                    if ("getInputStream".equals(method.getName()) && allowBody) return null;
                    throw new AssertionError("Unexpected request access: " + method.getName());
                });
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
