package com.hireflow.posting.controller;

import com.hireflow.posting.dto.CreatePostingRequest;
import com.hireflow.posting.dto.PostingResponse;
import com.hireflow.posting.dto.UpdatePostingRequest;
import com.hireflow.posting.service.PostingService;
import com.hireflow.security.CustomUserDetails;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/postings")
public class PostingController {

    private final PostingService postingService;

    public PostingController(PostingService postingService) {
        this.postingService = postingService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<PostingResponse> list(@AuthenticationPrincipal CustomUserDetails currentUser) {
        return postingService.list(currentUser);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public PostingResponse create(
            @Valid @RequestBody CreatePostingRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return postingService.create(request, currentUser);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public PostingResponse update(@PathVariable Long id, @Valid @RequestBody UpdatePostingRequest request) {
        return postingService.update(id, request);
    }
}
