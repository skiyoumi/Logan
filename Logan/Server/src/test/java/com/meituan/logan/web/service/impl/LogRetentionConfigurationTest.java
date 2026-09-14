package com.meituan.logan.web.service.impl;

import com.meituan.logan.web.mapper.LogRetentionMapper;
import org.junit.Test;
import org.springframework.context.annotation.AnnotatedBeanDefinitionReader;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class LogRetentionConfigurationTest {
    @Test
    public void resolvesDefaultEnvironmentAndJvmStyleOverrides() {
        assertRetention(new HashMap<>(), 3);
        Map<String, Object> properties = new HashMap<>();
        properties.put("LOGAN_RETENTION_DAYS", "14");
        assertRetention(properties, 14);
        properties.put("logan.retention-days", "7");
        assertRetention(properties, 7);
    }

    @Test
    public void invalidConfigurationFailsBeforeCleanupCanStart() {
        for (String days : new String[]{"0", "-1", "abc", "1.5", "2147483648", ""}) {
            Map<String, Object> properties = new HashMap<>();
            properties.put("LOGAN_RETENTION_DAYS", days);
            try {
                assertRetention(properties, 3);
                fail("Should reject " + days);
            } catch (org.springframework.beans.BeansException expected) {
                // Invalid configuration prevents the bean (and its scheduled cleanup) from starting.
            }
        }
    }

    private void assertRetention(Map<String, Object> properties, int expectedDays) {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.getEnvironment().getPropertySources().remove("systemProperties");
            context.getEnvironment().getPropertySources().remove("systemEnvironment");
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", properties));
            PropertySourcesPlaceholderConfigurer configurer = new PropertySourcesPlaceholderConfigurer();
            configurer.setEnvironment(context.getEnvironment());
            configurer.setLocation(new ClassPathResource("retention.properties"));
            context.addBeanFactoryPostProcessor(configurer);
            context.registerBean(LogRetentionMapper.class, () -> (LogRetentionMapper) Proxy.newProxyInstance(
                    LogRetentionMapper.class.getClassLoader(), new Class<?>[]{LogRetentionMapper.class},
                    (proxy, method, args) -> { throw new AssertionError("Cleanup must not run during configuration"); }));
            new AnnotatedBeanDefinitionReader(context).register(LogRetentionService.class);
            context.refresh();
            assertEquals(TimeUnit.DAYS.toMillis(expectedDays),
                    ReflectionTestUtils.getField(context.getBean(LogRetentionService.class), "retentionMillis"));
        }
    }
}
