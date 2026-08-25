package com.hireflow.common.exception;

/**
 * Maps to HTTP 403. Thrown for ownership violations - e.g. a CANDIDATE trying to read or act
 * on another candidate's Application - as distinct from role-based access denials that
 * {@code @PreAuthorize} already rejects before a controller method is even entered.
 */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
