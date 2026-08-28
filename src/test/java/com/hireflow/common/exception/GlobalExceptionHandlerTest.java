package com.hireflow.common.exception;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.hireflow.common.dto.ApiError;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code handleUnexpected} is the one branch in {@link GlobalExceptionHandler} that logs (every
 * other branch maps an already-meaningful exception straight to its status - see the class
 * comment on that method). Before this, an unexpected exception vanished into a generic 500 with
 * zero trace of what actually happened; this test is the regression guard for that log line
 * existing at all, using a Logback {@link ListAppender} attached directly to the class's logger
 * (no Spring context needed - this is a plain unit test, matching {@code RateLimitingFilterTest}'s
 * style elsewhere in this package).
 */
class GlobalExceptionHandlerTest {

    private ListAppender<ILoggingEvent> appender;
    private Logger logbackLogger;

    @BeforeEach
    void attachAppender() {
        logbackLogger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        appender = new ListAppender<>();
        appender.start();
        logbackLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logbackLogger.detachAppender(appender);
    }

    @Test
    void unexpectedException_logsAtError_andReturnsGeneric500() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        RuntimeException thrown = new RuntimeException("boom - something nobody expected");

        ResponseEntity<ApiError> response = handler.handleUnexpected(thrown);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().message()).isEqualTo("An unexpected error occurred");

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getThrowableProxy().getMessage()).isEqualTo(thrown.getMessage());
    }
}
