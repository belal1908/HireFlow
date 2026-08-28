package com.hireflow.common.logging;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * No-Spring-context unit test for {@link RequestIdFilter}, matching {@code RateLimitingFilterTest}'s
 * style - the filter's whole job is a few lines of MDC/header bookkeeping around the chain.
 */
class RequestIdFilterTest {

    @Test
    void setsResponseHeaderAndMdc_duringTheChain_thenClearsMdcAfter() throws Exception {
        RequestIdFilter filter = new RequestIdFilter();
        FilterChain chain = mock(FilterChain.class);
        String[] mdcValueDuringChain = new String[1];
        doAnswer(invocation -> {
            mdcValueDuringChain[0] = MDC.get(RequestIdFilter.MDC_KEY);
            return null;
        }).when(chain).doFilter(any(), any());

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest(), response, chain);

        verify(chain).doFilter(any(), any());
        assertThat(mdcValueDuringChain[0]).isNotBlank();
        assertThat(response.getHeader(RequestIdFilter.RESPONSE_HEADER)).isEqualTo(mdcValueDuringChain[0]);
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void mdcIsClearedEvenWhenDownstreamThrows() throws Exception {
        RequestIdFilter filter = new RequestIdFilter();
        FilterChain chain = mock(FilterChain.class);
        doAnswer(invocation -> {
            throw new java.io.IOException("downstream failure");
        }).when(chain).doFilter(any(), any());

        org.junit.jupiter.api.Assertions.assertThrows(java.io.IOException.class, () ->
                filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain));

        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void generatesADifferentIdForEachRequest() throws Exception {
        RequestIdFilter filter = new RequestIdFilter();
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletResponse first = new MockHttpServletResponse();
        MockHttpServletResponse second = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest(), first, chain);
        filter.doFilter(new MockHttpServletRequest(), second, chain);

        assertThat(first.getHeader(RequestIdFilter.RESPONSE_HEADER))
                .isNotEqualTo(second.getHeader(RequestIdFilter.RESPONSE_HEADER));
    }
}
