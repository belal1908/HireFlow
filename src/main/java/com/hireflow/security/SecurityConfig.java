package com.hireflow.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hireflow.common.dto.ApiError;
import com.hireflow.common.logging.RequestIdFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Stateless JWT-based API security: no sessions, no CSRF (no cookies involved), the JWT filter
 * populates the SecurityContext per-request, and every non-public endpoint is gated with
 * {@code @PreAuthorize} at the controller/service layer (method security enabled below).
 *
 * <p>Authentication/authorization failures raised by Spring Security itself (missing/invalid
 * token, or an {@code @PreAuthorize} check failing) are intercepted by
 * {@code ExceptionTranslationFilter} before they would ever reach a {@code @RestControllerAdvice},
 * so we render the same {@link ApiError} JSON shape directly here to keep every error response
 * consistent.
 */
@Configuration
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitingFilter rateLimitingFilter;
    private final RequestIdFilter requestIdFilter;
    private final ObjectMapper objectMapper;
    private final List<String> allowedOrigins;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            RateLimitingFilter rateLimitingFilter,
            RequestIdFilter requestIdFilter,
            ObjectMapper objectMapper,
            @Value("${hireflow.cors.allowed-origins}") List<String> allowedOrigins) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.rateLimitingFilter = rateLimitingFilter;
        this.requestIdFilter = requestIdFilter;
        this.objectMapper = objectMapper;
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * CORS for the Angular frontend (Week 2 frontend addition). The API is called cross-origin
     * from the frontend's own origin - {@code http://localhost:4200} for the local dev server by
     * default, overridable via {@code CORS_ALLOWED_ORIGINS} (comma-separated) for a real
     * deployment where frontend and backend are on different hosts - which the browser blocks by
     * default since this app has no CORS policy otherwise. Access tokens are sent via an
     * {@code Authorization} header (not a cookie), so credentials/cookies are not needed here -
     * {@code allowCredentials} is deliberately left at its default (false).
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint((request, response, authException) ->
                                writeError(response, HttpStatus.UNAUTHORIZED, "Authentication required"))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                writeError(response, HttpStatus.FORBIDDEN, "You do not have permission to perform this action")))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(rateLimitingFilter, JwtAuthenticationFilter.class)
                .addFilterBefore(requestIdFilter, RateLimitingFilter.class);

        return http.build();
    }

    private void writeError(HttpServletResponse response, HttpStatus status, String message) throws java.io.IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(ApiError.of(status, message)));
    }
}
