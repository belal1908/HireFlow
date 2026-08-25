package com.hireflow.application.dto;

import com.hireflow.application.entity.Application;
import com.hireflow.application.transition.ApplicationStatus;

import java.time.Instant;

public record ApplicationResponse(
        Long id,
        Long candidateId,
        Long jobPostingId,
        ApplicationStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public static ApplicationResponse from(Application application) {
        return new ApplicationResponse(
                application.getId(),
                application.getCandidate().getId(),
                application.getJobPosting().getId(),
                application.getStatus(),
                application.getCreatedAt(),
                application.getUpdatedAt());
    }
}
