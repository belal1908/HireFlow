package com.hireflow.posting.repository;

import com.hireflow.posting.entity.JobPosting;
import com.hireflow.posting.entity.PostingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobPostingRepository extends JpaRepository<JobPosting, Long> {

    List<JobPosting> findByStatus(PostingStatus status);
}
