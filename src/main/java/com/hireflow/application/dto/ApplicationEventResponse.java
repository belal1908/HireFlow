package com.hireflow.application.dto;

import com.hireflow.application.entity.ApplicationEvent;
import com.hireflow.application.transition.ApplicationStatus;

import java.time.Instant;

public record ApplicationEventResponse(
        Long id,
        Long applicationId,
        ApplicationStatus fromStatus,
        ApplicationStatus toStatus,
        Long changedBy,
        String note,
        Instant timestamp
) {
    public static ApplicationEventResponse from(ApplicationEvent event) {
        return new ApplicationEventResponse(
                event.getId(),
                event.getApplication().getId(),
                event.getFromStatus(),
                event.getToStatus(),
                event.getChangedBy().getId(),
                event.getNote(),
                event.getTimestamp());
    }
}
