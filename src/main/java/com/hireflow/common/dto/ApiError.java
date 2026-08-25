package com.hireflow.common.dto;

import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;

public record ApiError(int status, String error, String message, Instant timestamp, List<String> details) {

    public static ApiError of(HttpStatus status, String message) {
        return new ApiError(status.value(), status.getReasonPhrase(), message, Instant.now(), null);
    }

    public static ApiError of(HttpStatus status, String message, List<String> details) {
        return new ApiError(status.value(), status.getReasonPhrase(), message, Instant.now(), details);
    }
}
