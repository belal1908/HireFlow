package com.hireflow.common.exception;

/** Maps to HTTP 409. Thrown for state conflicts such as a duplicate application. */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
