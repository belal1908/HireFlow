package com.hireflow.common.exception;

/** Maps to HTTP 400. Generic "the request is invalid given current state" error. */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
