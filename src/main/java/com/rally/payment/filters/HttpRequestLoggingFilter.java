package com.rally.payment.filters;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class HttpRequestLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        if (isNoisyPath(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
        long start = System.nanoTime();
        try {
            filterChain.doFilter(request, responseWrapper);
        } finally {
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            String query = request.getQueryString();
            String uri = query == null ? request.getRequestURI() : request.getRequestURI() + "?" + query;
            log.info("HTTP {} {} completed with status {} in {}ms",
                    request.getMethod(), uri, responseWrapper.getStatus(), durationMs);
            responseWrapper.copyBodyToResponse();
        }
    }

    private boolean isNoisyPath(String uri) {
        return uri != null
                && (uri.startsWith("/actuator")
                        || uri.startsWith("/api-docs")
                        || uri.startsWith("/swagger-ui")
                        || uri.startsWith("/v3/api-docs"));
    }
}