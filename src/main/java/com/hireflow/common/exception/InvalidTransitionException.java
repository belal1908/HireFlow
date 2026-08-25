package com.hireflow.common.exception;

/**
 * Maps to HTTP 400. Thrown when {@code TransitionValidator} denies a requested Application
 * status change - whether because of the wrong actor role, wrong current state, a skipped
 * step, or a terminal state. The message is the validator's own denial reason.
 */
public class InvalidTransitionException extends RuntimeException {
    public InvalidTransitionException(String message) {
        super(message);
    }
}
