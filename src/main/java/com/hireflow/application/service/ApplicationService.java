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
import com.hireflow.application.storage.ResumeStorageService;
import com.hireflow.application.transition.ApplicationStatus;
import com.hireflow.application.transition.TransitionResult;
import com.hireflow.application.transition.TransitionValidator;
import com.hireflow.common.dto.PageResponse;
import com.hireflow.common.exception.BadRequestException;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Service
public class ApplicationService {

    /** PDF magic bytes: "%PDF-". Checked in addition to the client-declared content type. */
    private static final byte[] PDF_MAGIC_BYTES = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    private final ApplicationRepository applicationRepository;
    private final ApplicationEventRepository applicationEventRepository;
    private final JobPostingRepository jobPostingRepository;
    private final UserRepository userRepository;
    private final TransitionValidator transitionValidator;
    private final ResumeStorageService resumeStorageService;
    private final long maxResumeSizeBytes;

    public ApplicationService(
            ApplicationRepository applicationRepository,
            ApplicationEventRepository applicationEventRepository,
            JobPostingRepository jobPostingRepository,
            UserRepository userRepository,
            TransitionValidator transitionValidator,
            ResumeStorageService resumeStorageService,
            @Value("${hireflow.resume.max-size-bytes}") long maxResumeSizeBytes) {
        this.applicationRepository = applicationRepository;
        this.applicationEventRepository = applicationEventRepository;
        this.jobPostingRepository = jobPostingRepository;
        this.userRepository = userRepository;
        this.transitionValidator = transitionValidator;
        this.resumeStorageService = resumeStorageService;
        this.maxResumeSizeBytes = maxResumeSizeBytes;
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
    public PageResponse<ApplicationResponse> list(Long jobPostingId, ApplicationStatus status, Pageable pageable) {
        Specification<Application> spec = Specification
                .where(ApplicationSpecifications.hasJobPostingId(jobPostingId))
                .and(ApplicationSpecifications.hasStatus(status));
        Page<Application> page = applicationRepository.findAll(spec, pageable);
        return PageResponse.from(page.map(ApplicationResponse::from));
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

    /**
     * Owner-only. Validates the upload (PDF content-type + magic bytes + size limit), stores it
     * on disk under a UUID-based name, deletes any previously-uploaded file for this application
     * (re-upload replaces, it does not accumulate), and records the metadata on the Application row.
     */
    @Transactional
    public ApplicationResponse uploadResume(Long applicationId, MultipartFile file, CustomUserDetails currentUser) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new NotFoundException("Application not found: " + applicationId));

        if (!application.getCandidate().getId().equals(currentUser.getId())) {
            throw new ForbiddenException("You may not upload a resume to another candidate's application");
        }

        validateResume(file);

        // Re-upload replaces: delete the old file from disk before storing the new one.
        if (application.getResumeStoredFilename() != null) {
            resumeStorageService.delete(application.getResumeStoredFilename());
        }

        String storedFilename = resumeStorageService.store(file);

        application.setResumeOriginalFilename(sanitizeForDisplay(file.getOriginalFilename()));
        application.setResumeStoredFilename(storedFilename);
        application.setResumeContentType(MediaType.APPLICATION_PDF_VALUE);
        application.setResumeSizeBytes(file.getSize());
        application.setResumeUploadedAt(Instant.now());
        application = applicationRepository.save(application);

        return ApplicationResponse.from(application);
    }

    /** Owning CANDIDATE, or RECRUITER/ADMIN - same visibility rule as {@link #events}. */
    @Transactional(readOnly = true)
    public ResponseEntity<Resource> downloadResume(Long applicationId, CustomUserDetails currentUser) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new NotFoundException("Application not found: " + applicationId));

        boolean isOwner = application.getCandidate().getId().equals(currentUser.getId());
        boolean isPrivileged = currentUser.getRoleEnum() == Role.RECRUITER || currentUser.getRoleEnum() == Role.ADMIN;
        if (!isOwner && !isPrivileged) {
            throw new ForbiddenException("You may not download another candidate's resume");
        }

        if (application.getResumeStoredFilename() == null) {
            throw new NotFoundException("No resume has been uploaded for this application");
        }

        Resource resource = resumeStorageService.load(application.getResumeStoredFilename());

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + application.getResumeOriginalFilename() + "\"")
                .body(resource);
    }

    private void validateResume(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("No file was uploaded");
        }
        if (file.getSize() > maxResumeSizeBytes) {
            throw new BadRequestException(
                    "Resume file exceeds the maximum allowed size of " + (maxResumeSizeBytes / (1024 * 1024)) + "MB");
        }
        // Check the client-declared content type first (cheap), then the file's actual magic
        // bytes (authoritative) - a client can lie about Content-Type, but not easily fake the
        // first 5 bytes of a real PDF without producing a file that then fails to open as one.
        if (!MediaType.APPLICATION_PDF_VALUE.equals(file.getContentType())) {
            throw new BadRequestException("Only PDF files are accepted (content type must be application/pdf)");
        }
        if (!startsWithPdfMagicBytes(file)) {
            throw new BadRequestException("File does not appear to be a valid PDF");
        }
    }

    private boolean startsWithPdfMagicBytes(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            byte[] header = in.readNBytes(PDF_MAGIC_BYTES.length);
            return Arrays.equals(header, PDF_MAGIC_BYTES);
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * The original filename is only ever used for display/Content-Disposition, never as a disk
     * path (see ResumeStorageService), but it's still sanitized here to a bare filename with no
     * path separators or control characters, so it can't be used to smuggle anything odd into an
     * HTTP response header on download.
     */
    private String sanitizeForDisplay(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "resume.pdf";
        }
        String baseName = Paths.get(originalFilename).getFileName().toString();
        String sanitized = baseName.replaceAll("[\\p{Cntrl}\"\\\\]", "_");
        return sanitized.isBlank() ? "resume.pdf" : sanitized;
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
