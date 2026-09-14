package com.meituan.logan.web.service.impl;

import org.junit.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.FileSystemResourceLoader;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletConfig;
import org.springframework.mock.web.MockServletContext;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.XmlWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.util.Collections;

import static org.junit.Assert.*;

public class LogRetentionWebContextTest {
    @Test
    public void deployedContextsRegisterOneCleanupAndStillServeMvcRoutes() throws Exception {
        Document descriptor = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new File("src/main/webapp/WEB-INF/web.xml"));
        assertEquals("1", XPathFactory.newInstance().newXPath().evaluate(
                "count(/web-app/servlet[servlet-name='mvc-dispatcher']/init-param[param-name='contextConfigLocation'])",
                descriptor));
        String childLocation = XPathFactory.newInstance().newXPath().evaluate(
                "/web-app/servlet[servlet-name='mvc-dispatcher']/init-param[param-name='contextConfigLocation']/param-value",
                descriptor);
        MockServletContext servletContext = new MockServletContext("src/main/webapp", new FileSystemResourceLoader());
        try (XmlWebApplicationContext root = new XmlWebApplicationContext()) {
            root.setServletContext(servletContext);
            root.setConfigLocation("/WEB-INF/mvc-dispatcher-servlet.xml");
            root.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",
                    Collections.singletonMap("logan.retention.initial-delay-ms", "3600000")));
            root.refresh();
            servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, root);
            DispatcherServlet dispatcher = new DispatcherServlet();
            MockServletConfig servletConfig = new MockServletConfig(servletContext, "mvc-dispatcher");
            servletConfig.addInitParameter("contextConfigLocation", childLocation);
            try {
                dispatcher.init(servletConfig);
                WebApplicationContext child = dispatcher.getWebApplicationContext();
                assertEquals(1, root.getBeansOfType(LogRetentionService.class).size());
                assertTrue(child.getBeansOfType(LogRetentionService.class).isEmpty());
                assertSame(root.getBean(LogRetentionService.class), child.getBean(LogRetentionService.class));
                assertEquals(1, root.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks().size());
                MockHttpServletRequest request = new MockHttpServletRequest(servletContext, "GET", "/logan/meta/logtypes.json");
                MockHttpServletResponse response = new MockHttpServletResponse();
                dispatcher.service(request, response);
                assertEquals(200, response.getStatus());
                assertTrue(response.getContentAsString(), response.getContentAsString().contains("\"data\""));
            } finally {
                dispatcher.destroy();
            }
        }
    }
}
