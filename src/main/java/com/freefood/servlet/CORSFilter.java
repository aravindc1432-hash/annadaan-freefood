package com.freefood.servlet;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.*;
import java.io.IOException;

@WebFilter("/api/*")
public class CORSFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletResponse r = (HttpServletResponse) response;
        r.setHeader("Access-Control-Allow-Origin", "*");
        r.setHeader("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
        r.setHeader("Access-Control-Allow-Headers", "Content-Type,Accept");
        if ("OPTIONS".equalsIgnoreCase(((HttpServletRequest)request).getMethod())) {
            r.setStatus(200); return;
        }
        chain.doFilter(request, response);
    }
}
