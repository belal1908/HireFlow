package com.hireflow.application.repository;

import com.hireflow.application.entity.Application;
import com.hireflow.application.transition.ApplicationStatus;
import org.springframework.data.jpa.domain.Specification;

/** Small, composable filters for GET /api/applications?postingId=&status=. */
public final class ApplicationSpecifications {

    private ApplicationSpecifications() {
    }

    public static Specification<Application> hasJobPostingId(Long jobPostingId) {
        return (root, query, cb) -> jobPostingId == null
                ? null
                : cb.equal(root.get("jobPosting").get("id"), jobPostingId);
    }

    public static Specification<Application> hasStatus(ApplicationStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }
}
