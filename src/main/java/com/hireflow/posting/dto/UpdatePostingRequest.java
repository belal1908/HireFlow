package com.hireflow.posting.dto;

import com.hireflow.posting.entity.PostingStatus;
import jakarta.validation.constraints.Size;

/** All fields optional/nullable - PATCH semantics: only non-null fields are applied. */
public record UpdatePostingRequest(
        @Size(max = 200) String title,
        @Size(max = 10000) String description,
        PostingStatus status
) {
}
