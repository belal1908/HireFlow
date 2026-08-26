package com.hireflow.application.controller;

import com.hireflow.application.dto.ApplicationEventResponse;
import com.hireflow.application.dto.ApplicationResponse;
import com.hireflow.application.dto.ApplyRequest;
import com.hireflow.application.dto.StatusUpdateRequest;
import com.hireflow.application.service.ApplicationService;
import com.hireflow.application.transition.ApplicationStatus;
import com.hireflow.common.dto.PageResponse;
import com.hireflow.security.CustomUserDetails;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

    private final ApplicationService applicationService;

    public ApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping
    @PreAuthorize("hasRole('CANDIDATE')")
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationResponse apply(
            @Valid @RequestBody ApplyRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return applicationService.apply(request, currentUser);
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('CANDIDATE')")
    public List<ApplicationResponse> mine(@AuthenticationPrincipal CustomUserDetails currentUser) {
        return applicationService.mine(currentUser);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('RECRUITER', 'ADMIN')")
    public PageResponse<ApplicationResponse> list(
            @RequestParam(required = false) Long postingId,
            @RequestParam(required = false) ApplicationStatus status,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return applicationService.list(postingId, status, pageable);
    }

    /**
     * Role-gating for this endpoint is delegated entirely to {@code TransitionValidator}
     * (see PRD): any authenticated user may call it, but the validator denies the request
     * with 400 unless the caller's role is legal for that specific target status transition.
     * Ownership (a CANDIDATE acting on someone else's application) is a separate 403 check
     * in the service, since it is about resource access, not the state machine itself.
     */
    @PatchMapping("/{id}/status")
    @PreAuthorize("isAuthenticated()")
    public ApplicationResponse updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody StatusUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return applicationService.updateStatus(id, request, currentUser);
    }

    @GetMapping("/{id}/events")
    @PreAuthorize("isAuthenticated()")
    public List<ApplicationEventResponse> events(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return applicationService.events(id, currentUser);
    }

    /** Owner-only (enforced in the service, same pattern as updateStatus). Replaces any existing resume. */
    @PostMapping(value = "/{id}/resume", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('CANDIDATE')")
    public ApplicationResponse uploadResume(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return applicationService.uploadResume(id, file, currentUser);
    }

    /** Owning CANDIDATE, or RECRUITER/ADMIN - same visibility rule as {@link #events}. */
    @GetMapping("/{id}/resume")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> downloadResume(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return applicationService.downloadResume(id, currentUser);
    }
}
