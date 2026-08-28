package com.hireflow.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Assigns a fresh request ID to every request and puts it in the SLF4J MDC (see the
 * {@code logging.pattern.console} entry in {@code application.yml}, which renders it into every
 * log line for the duration of the request) so log lines from different concurrent requests can
 * be told apart. Also echoed back as the {@code X-Request-Id} response header, so a client-side
 * error report can be correlated back to the exact server-side log lines.
 *
 * <p>Runs first in the filter chain (before {@link com.hireflow.security.RateLimitingFilter} and
 * {@link com.hireflow.security.JwtAuthenticationFilter} - see
 * {@link com.hireflow.security.SecurityConfig}), so even a 429 or 401 response still carries a
 * request ID and a correlated log line. Generated server-side rather than trusted from an inbound
 * header, since this API has no upstream proxy that would legitimately set one.
 */
@Component
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String RESPONSE_HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String requestId = UUID.randomUUID().toString();
        MDC.put(MDC_KEY, requestId);
        response.setHeader(RESPONSE_HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
