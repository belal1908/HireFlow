package com.hireflow.posting.dto;

import com.hireflow.posting.entity.JobPosting;
import com.hireflow.posting.entity.PostingStatus;

import java.time.Instant;

public record PostingResponse(
        Long id,
        String title,
        String description,
        PostingStatus status,
        Long createdBy,
        Instant createdAt
) {
    public static PostingResponse from(JobPosting posting) {
        return new PostingResponse(
                posting.getId(),
                posting.getTitle(),
                posting.getDescription(),
                posting.getStatus(),
                posting.getCreatedBy().getId(),
                posting.getCreatedAt());
    }
}
