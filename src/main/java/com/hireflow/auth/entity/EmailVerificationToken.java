package com.hireflow.auth.entity;

import com.hireflow.user.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Mirrors {@link PasswordResetToken}'s shape and security posture exactly (SHA-256 hash only,
 * single-use via {@code used}, TTL-bound) - the two are structurally identical because the
 * underlying problem is identical: prove control of an out-of-band channel via a single-use,
 * time-limited, unguessable token. Kept as a separate table/entity rather than reusing
 * {@code PasswordResetToken} for a different purpose, since conflating "prove you own this email"
 * with "prove you're allowed to change this password" would make a bug in one silently usable as
 * the other.
 */
@Entity
@Table(name = "email_verification_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailVerificationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private boolean used;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public boolean isExpired() {
        return expiresAt.isBefore(Instant.now());
    }
}
