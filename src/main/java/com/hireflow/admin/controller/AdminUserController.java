package com.hireflow.admin.controller;

import com.hireflow.admin.dto.CreateUserRequest;
import com.hireflow.admin.service.AdminUserService;
import com.hireflow.common.dto.PageResponse;
import com.hireflow.user.dto.UserResponse;
import com.hireflow.user.entity.Role;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * ADMIN-only account management for RECRUITER/ADMIN users - the endpoints that close the
 * README's "No admin-user-creation endpoint" gap. Deliberately scoped to create + list only:
 * no role-change, deactivation, or delete endpoints (out of scope per the PRD stretch goal).
 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        return adminUserService.createUser(request);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<UserResponse> list(
            @RequestParam(required = false) Role role,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return adminUserService.list(role, pageable);
    }
}
