package com.hinglishpdf.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    private final String expectedApiKey;

    public ApiKeyFilter(@Value("${app.api-key:dev-secret-key}") String expectedApiKey) {
        this.expectedApiKey = expectedApiKey;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Set global CORS headers on all responses
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "*");

        // Preflight OPTIONS requests must always succeed with 200 OK
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        String path = request.getRequestURI();

        // Allow static assets, health endpoint, and root UI without API key
        if (path.equals("/") || path.equals("/index.html") || path.startsWith("/static") || path.equals("/health") || path.endsWith(".ico") || path.endsWith(".js") || path.endsWith(".css")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Check X-API-Key header for API endpoints
        if (path.startsWith("/jobs")) {
            // Allow direct browser download links and dev-secret-key mode
            if (path.endsWith("/download") || expectedApiKey == null || expectedApiKey.isBlank() || expectedApiKey.equals("dev-secret-key")) {
                filterChain.doFilter(request, response);
                return;
            }

            String apiKey = request.getHeader("X-API-Key");
            if (apiKey == null || apiKey.isBlank()) {
                apiKey = request.getParameter("api_key");
            }
            if (apiKey == null || apiKey.isBlank()) {
                apiKey = request.getParameter("apiKey");
            }

            if (apiKey == null || !apiKey.equals(expectedApiKey)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"detail\": \"Invalid or missing X-API-Key header\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
