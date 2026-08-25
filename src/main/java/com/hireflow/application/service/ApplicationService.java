package com.hireflow.application.service;

import com.hireflow.application.dto.ApplicationEventResponse;
import com.hireflow.application.dto.ApplicationResponse;
import com.hireflow.application.dto.ApplyRequest;
import com.hireflow.application.dto.StatusUpdateRequest;
import com.hireflow.application.entity.Application;
import com.hireflow.application.entity.ApplicationEvent;
import com.hireflow.application.repository.ApplicationEventRepository;
import com.hireflow.application.repository.ApplicationRepository;
import com.hireflow.application.repository.ApplicationSpecifications;
import com.hireflow.application.transition.ApplicationStatus;
import com.hireflow.application.transition.TransitionResult;
import com.hireflow.application.transition.TransitionValidator;
import com.hireflow.common.exception.ConflictException;
import com.hireflow.common.exception.ForbiddenException;
import com.hireflow.common.exception.InvalidTransitionException;
import com.hireflow.common.exception.NotFoundException;
import com.hireflow.posting.entity.JobPosting;
import com.hireflow.posting.entity.PostingStatus;
import com.hireflow.posting.repository.JobPostingRepository;
import com.hireflow.security.CustomUserDetails;
import com.hireflow.user.entity.Role;
import com.hireflow.user.entity.User;
import com.hireflow.user.repository.UserRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final ApplicationEventRepository applicationEventRepository;
    private final JobPostingRepository jobPostingRepository;
    private final UserRepository userRepository;
    private final TransitionValidator transitionValidator;

    public ApplicationService(
            ApplicationRepository applicationRepository,
            ApplicationEventRepository applicationEventRepository,
            JobPostingRepository jobPostingRepository,
            UserRepository userRepository,
            TransitionValidator transitionValidator) {
        this.applicationRepository = applicationRepository;
        this.applicationEventRepository = applicationEventRepository;
        this.jobPostingRepository = jobPostingRepository;
        this.userRepository = userRepository;
        this.transitionValidator = transitionValidator;
    }

    @Transactional
    public ApplicationResponse apply(ApplyRequest request, CustomUserDetails currentUser) {
        User candidate = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new NotFoundException("Authenticated user not found"));

        JobPosting posting = jobPostingRepository.findById(request.jobPostingId())
                .orElseThrow(() -> new NotFoundException("Job posting not found: " + request.jobPostingId()));

        if (posting.getStatus() != PostingStatus.OPEN) {
            throw new ConflictException("Cannot apply to a job posting that is not OPEN");
        }

        if (applicationRepository.existsByCandidateIdAndJobPostingId(candidate.getId(), posting.getId())) {
            throw new ConflictException("You have already applied to this job posting");
        }

        Application application = Application.builder()
                .candidate(candidate)
                .jobPosting(posting)
                .status(ApplicationStatus.APPLIED)
                .build();
        application = applicationRepository.save(application);

        writeAuditEvent(application, null, ApplicationStatus.APPLIED, candidate, null);

        return ApplicationResponse.from(application);
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> mine(CustomUserDetails currentUser) {
        return applicationRepository.findByCandidateId(currentUser.getId()).stream()
                .map(ApplicationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> list(Long jobPostingId, ApplicationStatus status) {
        Specification<Application> spec = Specification
                .where(ApplicationSpecifications.hasJobPostingId(jobPostingId))
                .and(ApplicationSpecifications.hasStatus(status));
        return applicationRepository.findAll(spec).stream()
                .map(ApplicationResponse::from)
                .toList();
    }

    @Transactional
    public ApplicationResponse updateStatus(Long applicationId, StatusUpdateRequest request, CustomUserDetails currentUser) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new NotFoundException("Application not found: " + applicationId));

        // Ownership: a CANDIDATE may only ever act on their own application. RECRUITER/ADMIN
        // are not tied to individual applications in this model, so no ownership check for them
        // - their allowed actions are governed entirely by TransitionValidator below.
        if (currentUser.getRoleEnum() == Role.CANDIDATE
                && !application.getCandidate().getId().equals(currentUser.getId())) {
            throw new ForbiddenException("You may not act on another candidate's application");
        }

        ApplicationStatus previousStatus = application.getStatus();
        TransitionResult result = transitionValidator.validate(previousStatus, request.targetStatus(), currentUser.getRoleEnum());
        if (!result.allowed()) {
            throw new InvalidTransitionException(result.reason());
        }

        application.setStatus(request.targetStatus());
        application = applicationRepository.save(application);

        User actor = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new NotFoundException("Authenticated user not found"));
        writeAuditEvent(application, previousStatus, request.targetStatus(), actor, request.note());

        return ApplicationResponse.from(application);
    }

    @Transactional(readOnly = true)
    public List<ApplicationEventResponse> events(Long applicationId, CustomUserDetails currentUser) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new NotFoundException("Application not found: " + applicationId));

        boolean isOwner = application.getCandidate().getId().equals(currentUser.getId());
        boolean isPrivileged = currentUser.getRoleEnum() == Role.RECRUITER || currentUser.getRoleEnum() == Role.ADMIN;
        if (!isOwner && !isPrivileged) {
            throw new ForbiddenException("You may not view another candidate's application events");
        }

        return applicationEventRepository.findByApplicationIdOrderByTimestampAsc(applicationId).stream()
                .map(ApplicationEventResponse::from)
                .toList();
    }

    private void writeAuditEvent(
            Application application, ApplicationStatus fromStatus, ApplicationStatus toStatus, User changedBy, String note) {
        ApplicationEvent event = ApplicationEvent.builder()
                .application(application)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .changedBy(changedBy)
                .note(note)
                .build();
        applicationEventRepository.save(event);
    }
}
