package com.meituan.logan.web.filter;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class CORSFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        // Lets make sure that we are working with HTTP (that is, against HttpServletRequest and HttpServletResponse objects)
        if (servletRequest instanceof HttpServletRequest && servletResponse instanceof HttpServletResponse) {
            HttpServletRequest request = (HttpServletRequest) servletRequest;
            HttpServletResponse response = (HttpServletResponse) servletResponse;

            // Access-Control-Allow-Origin
            // Wildcard origins require requests without cross-origin credentials.
            response.setHeader("Access-Control-Allow-Origin", "*");

            // Access-Control-Max-Age
            response.setHeader("Access-Control-Max-Age", "3600");

            // Access-Control-Allow-Methods
            response.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS, DELETE");

            // Access-Control-Allow-Headers
            response.setHeader("Access-Control-Allow-Headers",
                    "Origin, X-Requested-With, Content-Type, Accept, " + "X-CSRF-TOKEN");
            if ("OPTIONS".equalsIgnoreCase(request.getMethod())
                    && request.getHeader("Origin") != null
                    && request.getHeader("Access-Control-Request-Method") != null) {
                response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                return;
            }
        }

        filterChain.doFilter(servletRequest, servletResponse);
    }

    @Override
    public void destroy() {

    }
}
