package com.hireflow.user.entity;

/**
 * Plain, framework-free enum shared across the user, security, and application.transition
 * packages. Deliberately has no Spring/JPA annotations so it can be used unmodified by the
 * framework-free {@code TransitionValidator}.
 */
public enum Role {
    CANDIDATE,
    RECRUITER,
    ADMIN
}
