package com.hireflow.posting.repository;

import com.hireflow.posting.entity.JobPosting;
import com.hireflow.posting.entity.PostingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobPostingRepository extends JpaRepository<JobPosting, Long> {

    Page<JobPosting> findByStatus(PostingStatus status, Pageable pageable);
}
