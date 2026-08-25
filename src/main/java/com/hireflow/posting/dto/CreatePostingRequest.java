package com.hireflow.posting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePostingRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 10000) String description
) {
}
