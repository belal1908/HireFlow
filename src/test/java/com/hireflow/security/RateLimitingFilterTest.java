package com.hireflow.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Servlet-filter-level tests for {@link RateLimitingFilter}, built directly on Spring's
 * Mock{@code HttpServletRequest}/{@code HttpServletResponse} (no Spring context, no MockMvc, no
 * Testcontainers) - fast, deterministic, and independent of the rest of the integration suite.
 * See the class comment on {@link RateLimitingFilter} for why the shared
 * {@code AbstractIntegrationTest} Spring context disables rate limiting entirely instead of
 * trying to exercise the real 429 path through it.
 */
class RateLimitingFilterTest {

    /** findAndRegisterModules() picks up jackson-datatype-jsr310 so ApiError's Instant field serializes, matching the app's auto-configured ObjectMapper bean. */
    private static ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    @Test
    void allowsRequestsUpToThreshold_thenReturns429() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter(objectMapper(), true, 3);
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 3; i++) {
            filter.doFilter(loginRequest(), new MockHttpServletResponse(), chain);
        }
        verify(chain, times(3)).doFilter(any(), any());

        MockHttpServletResponse fourthResponse = new MockHttpServletResponse();
        filter.doFilter(loginRequest(), fourthResponse, chain);

        assertThat(fourthResponse.getStatus()).isEqualTo(429);
        assertThat(fourthResponse.getContentAsString()).contains("Too many requests");
        assertThat(fourthResponse.getContentType()).isEqualTo("application/json");
        // The chain was never reached a 4th time - the filter short-circuited before the controller.
        verify(chain, times(3)).doFilter(any(), any());
    }

    @Test
    void differentRemoteAddresses_haveIndependentLimits() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter(objectMapper(), true, 1);
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletRequest fromA = loginRequest();
        fromA.setRemoteAddr("10.0.0.1");
        filter.doFilter(fromA, new MockHttpServletResponse(), chain);

        MockHttpServletRequest fromB = loginRequest();
        fromB.setRemoteAddr("10.0.0.2");
        MockHttpServletResponse responseB = new MockHttpServletResponse();
        filter.doFilter(fromB, responseB, chain);

        assertThat(responseB.getStatus()).isEqualTo(200);
        verify(chain, times(2)).doFilter(any(), any());
    }

    @Test
    void unrelatedEndpoints_areNeverThrottled() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter(objectMapper(), true, 1);
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 5; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(new MockHttpServletRequest("GET", "/api/postings"), response, chain);
            assertThat(response.getStatus()).isEqualTo(200);
        }
        verify(chain, times(5)).doFilter(any(), any());
    }

    @Test
    void whenDisabled_neverThrottlesEvenPastThreshold() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter(objectMapper(), false, 1);
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 5; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(loginRequest(), response, chain);
            assertThat(response.getStatus()).isEqualTo(200);
        }
        verify(chain, times(5)).doFilter(any(), any());
    }

    @Test
    void registerEndpoint_isAlsoLimited() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter(objectMapper(), true, 1);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(new MockHttpServletRequest("POST", "/api/auth/register"), new MockHttpServletResponse(), chain);

        MockHttpServletResponse second = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("POST", "/api/auth/register"), second, chain);

        assertThat(second.getStatus()).isEqualTo(429);
    }

    @Test
    void passwordResetRequestEndpoint_isAlsoLimited() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter(objectMapper(), true, 1);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(new MockHttpServletRequest("POST", "/api/auth/password-reset/request"),
                new MockHttpServletResponse(), chain);

        MockHttpServletResponse second = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("POST", "/api/auth/password-reset/request"), second, chain);

        assertThat(second.getStatus()).isEqualTo(429);
    }

    @Test
    void passwordResetConfirmEndpoint_isAlsoLimited() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter(objectMapper(), true, 1);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(new MockHttpServletRequest("POST", "/api/auth/password-reset/confirm"),
                new MockHttpServletResponse(), chain);

        MockHttpServletResponse second = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("POST", "/api/auth/password-reset/confirm"), second, chain);

        assertThat(second.getStatus()).isEqualTo(429);
    }

    /**
     * The bucket key is client IP alone (see RateLimitingFilter#doFilterInternal), not
     * IP+path - so hitting the threshold on one limited endpoint also blocks a different limited
     * endpoint from the same client until the bucket refills. Worth pinning down explicitly since
     * it's easy to assume each endpoint gets its own independent budget.
     */
    @Test
    void limitIsSharedAcrossDifferentLimitedEndpoints_fromTheSameClient() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter(objectMapper(), true, 1);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(loginRequest(), new MockHttpServletResponse(), chain);

        MockHttpServletResponse registerResponse = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("POST", "/api/auth/register"), registerResponse, chain);

        assertThat(registerResponse.getStatus()).isEqualTo(429);
    }

    private MockHttpServletRequest loginRequest() {
        return new MockHttpServletRequest("POST", "/api/auth/login");
    }
}
