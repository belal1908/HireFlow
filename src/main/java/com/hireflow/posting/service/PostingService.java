package com.hireflow.posting.service;

import com.hireflow.common.dto.PageResponse;
import com.hireflow.common.exception.NotFoundException;
import com.hireflow.posting.dto.CreatePostingRequest;
import com.hireflow.posting.dto.PostingResponse;
import com.hireflow.posting.dto.UpdatePostingRequest;
import com.hireflow.posting.entity.JobPosting;
import com.hireflow.posting.entity.PostingStatus;
import com.hireflow.posting.repository.JobPostingRepository;
import com.hireflow.security.CustomUserDetails;
import com.hireflow.user.entity.Role;
import com.hireflow.user.entity.User;
import com.hireflow.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PostingService {

    private final JobPostingRepository jobPostingRepository;
    private final UserRepository userRepository;

    public PostingService(JobPostingRepository jobPostingRepository, UserRepository userRepository) {
        this.jobPostingRepository = jobPostingRepository;
        this.userRepository = userRepository;
    }

    /** Any authenticated user sees OPEN postings only; ADMIN sees everything (including CLOSED). */
    @Transactional(readOnly = true)
    public PageResponse<PostingResponse> list(CustomUserDetails currentUser, Pageable pageable) {
        Page<JobPosting> postings = currentUser.getRoleEnum() == Role.ADMIN
                ? jobPostingRepository.findAll(pageable)
                : jobPostingRepository.findByStatus(PostingStatus.OPEN, pageable);
        return PageResponse.from(postings.map(PostingResponse::from));
    }

    @Transactional
    public PostingResponse create(CreatePostingRequest request, CustomUserDetails currentUser) {
        User admin = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new NotFoundException("Authenticated user not found"));

        JobPosting posting = JobPosting.builder()
                .title(request.title())
                .description(request.description())
                .status(PostingStatus.OPEN)
                .createdBy(admin)
                .build();

        return PostingResponse.from(jobPostingRepository.save(posting));
    }

    @Transactional
    public PostingResponse update(Long id, UpdatePostingRequest request) {
        JobPosting posting = jobPostingRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Job posting not found: " + id));

        if (request.title() != null) {
            posting.setTitle(request.title());
        }
        if (request.description() != null) {
            posting.setDescription(request.description());
        }
        if (request.status() != null) {
            posting.setStatus(request.status());
        }

        return PostingResponse.from(jobPostingRepository.save(posting));
    }
}
