package com.hireflow.user.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * {@code @ColumnDefault("false")} (a real Postgres {@code DEFAULT}, not just app-level) so
     * {@code ddl-auto: update} can add this column to an already-populated table - a plain
     * {@code NOT NULL} column with no default fails to backfill existing rows. Self-registered
     * accounts start unverified ({@code AuthService#register}); admin-created and bootstrap
     * accounts start verified (vouched for by an existing admin, not self-service - see
     * {@code AdminUserService#createUser} / {@code AdminBootstrapRunner}).
     */
    @Column(name = "email_verified", nullable = false)
    @ColumnDefault("false")
    private boolean emailVerified;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
