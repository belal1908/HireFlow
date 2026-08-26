package com.hireflow.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hireflow.common.dto.ApiError;
import com.hireflow.security.ratelimit.TokenBucketRateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;

/**
 * A lightweight, in-memory, per-client-IP rate limiter scoped to just the two auth endpoints
 * that are worth protecting from brute-force/credential-stuffing and registration abuse:
 * {@code POST /api/auth/login} and {@code POST /api/auth/register}. Every other endpoint is
 * untouched (the path check below returns immediately for anything else, so the overhead on the
 * rest of the API is a single Set.contains check).
 *
 * <p>Wired into the security filter chain the same way as {@link JwtAuthenticationFilter} (a
 * {@code @Component} {@code OncePerRequestFilter} added via {@code addFilterBefore} in
 * {@link SecurityConfig}) - it deliberately runs <em>before</em> the JWT filter, since these two
 * endpoints are unauthenticated ({@code permitAll()}) and a 429 here should short-circuit the
 * request before any JWT parsing or controller logic runs at all.
 *
 * <p><b>Known limitation (also documented in the README)</b>: state is an in-memory
 * {@code ConcurrentHashMap} inside {@link TokenBucketRateLimiter} - this only rate-limits within
 * a single backend instance/process. It is not shared across horizontally-scaled replicas (each
 * instance would enforce its own independent limit); a real multi-instance deployment would need
 * a shared store (e.g. Redis) instead. Acceptable for this project's single-instance scope.
 *
 * <p><b>Test-profile behavior</b>: disabled entirely when {@code hireflow.rate-limit.enabled} is
 * false (set in {@code application-test.yml}, active for every {@code AbstractIntegrationTest}
 * subclass). The integration suite issues far more than the production threshold's worth of
 * login/register calls across its ~300 tests sharing one MockMvc "client" (Testcontainers/
 * MockMvc requests all report the same remote address), so enforcing the real limit there would
 * make unrelated tests fail with 429 depending on run order/volume rather than on their own
 * behavior. The limiter's actual token-bucket logic is unit-tested directly and in isolation in
 * {@code TokenBucketRateLimiterTest} and {@code RateLimitingFilterTest} (no Spring context, so
 * they run independently of this profile flag), and the real threshold is exercised manually
 * against the locally running dev server (see README "Rate limiting" section).
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Set<String> LIMITED_PATHS = Set.of("/api/auth/login", "/api/auth/register");

    private final TokenBucketRateLimiter rateLimiter;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public RateLimitingFilter(
            ObjectMapper objectMapper,
            @Value("${hireflow.rate-limit.enabled}") boolean enabled,
            @Value("${hireflow.rate-limit.requests-per-minute}") long requestsPerMinute) {
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.rateLimiter = new TokenBucketRateLimiter(requestsPerMinute, Duration.ofMinutes(1));
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        if (!enabled || !"POST".equalsIgnoreCase(request.getMethod()) || !LIMITED_PATHS.contains(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = request.getRemoteAddr();
        if (!rateLimiter.tryConsume(key)) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(objectMapper.writeValueAsString(
                    ApiError.of(HttpStatus.TOO_MANY_REQUESTS, "Too many requests - please try again later")));
            return;
        }

        filterChain.doFilter(request, response);
    }
}
