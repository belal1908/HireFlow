package com.hireflow.application.dto;

import com.hireflow.application.transition.ApplicationStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StatusUpdateRequest(
        @NotNull ApplicationStatus targetStatus,
        @Size(max = 2000) String note
) {
}
