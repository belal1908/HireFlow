package com.hireflow.application.dto;

import jakarta.validation.constraints.NotNull;

public record ApplyRequest(@NotNull Long jobPostingId) {
}
