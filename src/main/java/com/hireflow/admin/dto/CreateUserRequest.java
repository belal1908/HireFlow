package com.hireflow.admin.dto;

import com.hireflow.user.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code POST /api/admin/users}. Unlike {@code RegisterRequest}, this DTO does carry a
 * {@code role} - that is the entire point of this endpoint (creating the RECRUITER/ADMIN accounts
 * self-registration can never produce). {@code role == CANDIDATE} is still rejected, but at the
 * service layer (see {@code AdminUserService#createUser}) with a domain-specific message, since
 * that path already exists via {@code POST /api/auth/register}.
 */
public record CreateUserRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 100) String password,
        @NotNull Role role
) {
}
