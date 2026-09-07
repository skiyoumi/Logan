package com.meituan.logan.web.filter;

import org.junit.Test;

import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class CORSFilterTest {

    @Test
    public void allowsAllOriginsWithoutCredentials() throws Exception {
        String origin = "http://127.0.0.1:3000";
        Map<String, String> responseHeaders = new HashMap<>();
        HttpServletRequest request = (HttpServletRequest) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class[]{HttpServletRequest.class},
                (proxy, method, args) -> "getHeader".equals(method.getName()) ? origin : null);
        HttpServletResponse response = (HttpServletResponse) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class[]{HttpServletResponse.class},
                (proxy, method, args) -> {
                    if ("setHeader".equals(method.getName())) {
                        responseHeaders.put((String) args[0], (String) args[1]);
                    }
                    return null;
                });
        FilterChain filterChain = (servletRequest, servletResponse) -> { };

        new CORSFilter().doFilter(request, response, filterChain);

        assertEquals("*", responseHeaders.get("Access-Control-Allow-Origin"));
        assertEquals("false", responseHeaders.get("Access-Control-Allow-Credentials"));
    }
}
