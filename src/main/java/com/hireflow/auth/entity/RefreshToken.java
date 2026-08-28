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
 * A refresh token is never stored raw - only a SHA-256 hash of it - so a leaked database
 * cannot be used to mint sessions. Rotation-on-use: {@code revoked} is flipped to true the
 * moment a token is exchanged for a new pair, so a stolen-and-replayed refresh token fails.
 *
 * <p>{@code userAgent}/{@code issuedIp} capture the client at issuance (see
 * {@code AuthService#issueTokenPair}) so a later {@code /api/auth/refresh} can be checked against
 * them - see {@code AuthService#refresh} for which one is actually enforced and why. Both are
 * nullable at the DB level (not {@code nullable = false}) purely so that adding these columns to
 * an already-populated table via {@code ddl-auto: update} doesn't fail on existing rows with no
 * default to backfill; the application code always sets both on every row it creates, and treats
 * a null (a token issued before this feature existed) as an automatic mismatch on refresh.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

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
    private boolean revoked;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "issued_ip", length = 64)
    private String issuedIp;

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
