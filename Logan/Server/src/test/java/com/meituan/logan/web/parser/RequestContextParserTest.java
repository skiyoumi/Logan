package com.meituan.logan.web.parser;

import com.meituan.logan.web.model.LoganTaskModel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class RequestContextParserTest {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> cases() {
        return Arrays.asList(new Object[][] {
                {"missing", null, null},
                {"empty", "", ""},
                {"ascii", "user_123", "user_123"},
                {"unicode", "用户_张三", "用户_张三"},
                {"raw UTF-8 header", rawHeader("yzkdcg_c®公司名"), "yzkdcg_c®公司名"},
                {"raw emoji header", rawHeader("用户😀"), "用户😀"},
                {"URL encoded", "%E7%94%A8%E6%88%B7_%E5%BC%A0%E4%B8%89", "用户_张三"},
                {"lowercase escapes", "%e7%94%a8%e6%88%b7", "用户"},
                {"encoded emoji", "%F0%9F%98%80", "😀"},
                {"plus and space", "user+name%20%2B%25", "user+name +%"},
                {"decode once", "%2520", "%20"},
                {"mixed encoding", rawHeader("用户_%E5%BC%A0%E4%B8%89"), "用户_张三"},
                {"literal percent", "user100%", "user100%"},
                {"invalid escape", "%GG", "%GG"},
                {"incomplete escape", "%E4%B8%", "%E4%B8%"},
                {"invalid UTF-8", "%E4%B8", "%E4%B8"},
                {"Latin-1", "André", "André"}
        });
    }

    private static String rawHeader(String value) {
        return new String(value.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);
    }

    private final String input;
    private final String expected;

    public RequestContextParserTest(String name, String input, String expected) {
        this.input = input;
        this.expected = expected;
    }

    @Test
    public void normalizesUnionIdForEveryNativePlatform() {
        for (int platform : new int[] {1, 2, 3}) {
            Map<String, String> headers = new HashMap<>();
            headers.put("platform", Integer.toString(platform));
            headers.put("unionId", input);
            headers.put("appId", "app%20id");
            headers.put("deviceId", "device+id");
            headers.put("fileDate", "2026-09-08");
            HttpServletRequest request = (HttpServletRequest) Proxy.newProxyInstance(
                    HttpServletRequest.class.getClassLoader(), new Class<?>[] {HttpServletRequest.class},
                    (proxy, method, args) -> {
                        if ("getHeader".equals(method.getName())) {
                            return headers.get(args[0]);
                        }
                        if ("getContentLength".equals(method.getName())) {
                            return 0;
                        }
                        throw new UnsupportedOperationException(method.getName());
                    });
            LoganTaskModel model = RequestContextParser.parse(request);
            assertEquals(expected, model.getUnionId());
            assertEquals(expected, model.transformToDto().transformToModel().getUnionId());
            assertEquals(platform, model.getPlatform());
            assertEquals("app%20id", model.getAppId());
            assertEquals("device+id", model.getDeviceId());
        }
    }
}
