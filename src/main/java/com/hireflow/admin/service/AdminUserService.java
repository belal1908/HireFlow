package com.hireflow.admin.service;

import com.hireflow.admin.dto.CreateUserRequest;
import com.hireflow.common.dto.PageResponse;
import com.hireflow.common.exception.BadRequestException;
import com.hireflow.common.exception.ConflictException;
import com.hireflow.user.dto.UserResponse;
import com.hireflow.security.SecurityUtils;
import com.hireflow.user.entity.Role;
import com.hireflow.user.entity.User;
import com.hireflow.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Backs the ADMIN-only user-management endpoints ({@code /api/admin/users}) that close the
 * README's documented gap: self-registration always forces {@code CANDIDATE}, so the first
 * RECRUITER/ADMIN accounts previously had to be created by hand with a raw SQL {@code UPDATE}.
 *
 * <p>Deliberately mirrors {@code AuthService#register}'s validation (normalize email, reject
 * duplicates with 409, BCrypt-hash the password) rather than calling into it directly - that
 * method's whole contract is "always forces CANDIDATE", which is exactly the behavior this
 * endpoint must NOT have.
 */
@Service
public class AdminUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminUserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (request.role() == Role.CANDIDATE) {
            throw new BadRequestException(
                    "CANDIDATE accounts are created via self-registration (POST /api/auth/register), not this endpoint");
        }

        String normalizedEmail = request.email().trim().toLowerCase();
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ConflictException("An account with this email already exists");
        }

        User user = User.builder()
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(request.role())
                .build();

        User saved = userRepository.save(user);
        log.info("Elevated account created: userId={} role={} createdByUserId={}",
                saved.getId(), saved.getRole(), SecurityUtils.currentUser().getId());
        return UserResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(Role roleFilter, Pageable pageable) {
        Page<User> users = roleFilter == null
                ? userRepository.findAll(pageable)
                : userRepository.findByRole(roleFilter, pageable);
        return PageResponse.from(users.map(UserResponse::from));
    }
}
