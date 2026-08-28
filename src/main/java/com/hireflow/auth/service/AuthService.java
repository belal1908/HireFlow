package com.hireflow.auth.service;

import com.hireflow.auth.dto.AuthResponse;
import com.hireflow.auth.dto.EmailVerificationConfirmRequest;
import com.hireflow.auth.dto.EmailVerificationRequest;
import com.hireflow.auth.dto.EmailVerificationResponse;
import com.hireflow.auth.dto.LoginRequest;
import com.hireflow.auth.dto.PasswordResetConfirmRequest;
import com.hireflow.auth.dto.PasswordResetRequest;
import com.hireflow.auth.dto.PasswordResetResponse;
import com.hireflow.auth.dto.RefreshRequest;
import com.hireflow.auth.dto.RegisterRequest;
import com.hireflow.auth.entity.EmailVerificationToken;
import com.hireflow.auth.entity.PasswordResetToken;
import com.hireflow.auth.entity.RefreshToken;
import com.hireflow.auth.repository.EmailVerificationTokenRepository;
import com.hireflow.auth.repository.PasswordResetTokenRepository;
import com.hireflow.auth.repository.RefreshTokenRepository;
import com.hireflow.common.exception.BadRequestException;
import com.hireflow.common.exception.ConflictException;
import com.hireflow.security.JwtService;
import com.hireflow.user.dto.UserResponse;
import com.hireflow.user.entity.Role;
import com.hireflow.user.entity.User;
import com.hireflow.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int REFRESH_TOKEN_BYTES = 64;
    private static final int PASSWORD_RESET_TOKEN_BYTES = 32;
    private static final int EMAIL_VERIFICATION_TOKEN_BYTES = 32;

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final HttpServletRequest httpRequest;
    private final Duration refreshTokenTtl;
    private final Duration passwordResetTokenTtl;
    private final Duration emailVerificationTokenTtl;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            EmailVerificationTokenRepository emailVerificationTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            HttpServletRequest httpRequest,
            @Value("${hireflow.jwt.refresh-token-ttl-days}") long refreshTokenTtlDays,
            @Value("${hireflow.password-reset.token-ttl-minutes}") long passwordResetTokenTtlMinutes,
            @Value("${hireflow.email-verification.token-ttl-hours}") long emailVerificationTokenTtlHours) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.httpRequest = httpRequest;
        this.refreshTokenTtl = Duration.ofDays(refreshTokenTtlDays);
        this.passwordResetTokenTtl = Duration.ofMinutes(passwordResetTokenTtlMinutes);
        this.emailVerificationTokenTtl = Duration.ofHours(emailVerificationTokenTtlHours);
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ConflictException("An account with this email already exists");
        }
        User user = User.builder()
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(Role.CANDIDATE) // always forced, regardless of anything the client sends
                .build();
        User saved = userRepository.save(user);
        log.info("New CANDIDATE account registered (userId={})", saved.getId());
        return UserResponse.from(saved);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> {
                    log.warn("Login failed - no account for email={}", normalizedEmail);
                    return new BadCredentialsException("Invalid email or password");
                });
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.warn("Login failed - wrong password (userId={})", user.getId());
            throw new BadCredentialsException("Invalid email or password");
        }
        log.info("Login succeeded (userId={}, role={})", user.getId(), user.getRole());
        return issueTokenPair(user);
    }

    /**
     * {@code noRollbackFor}: the User-Agent-mismatch branch below revokes the token and then
     * throws {@code BadCredentialsException} to return 401 - without this, Spring's default
     * rollback-on-RuntimeException behavior would undo that revocation the instant the exception
     * propagates, silently defeating the whole point of revoking it (the token would still work
     * from either device on the next attempt).
     */
    @Transactional(noRollbackFor = BadCredentialsException.class)
    public AuthResponse refresh(RefreshRequest refreshRequest) {
        String hash = sha256(refreshRequest.refreshToken());
        RefreshToken existing = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> {
                    log.warn("Refresh rejected - token not recognized");
                    return new BadCredentialsException("Invalid refresh token");
                });

        if (existing.isRevoked() || existing.isExpired()) {
            // A *revoked* (already-rotated) token being presented again is the signal worth
            // watching - it means either the same client retried after a race, or the token
            // leaked and someone else is replaying it. An *expired* one is just routine.
            if (existing.isRevoked()) {
                log.warn("Refresh rejected - already-rotated token reused (userId={})", existing.getUser().getId());
            }
            throw new BadCredentialsException("Refresh token has expired or already been used");
        }

        // Device binding: the User-Agent recorded at issuance must match the one presenting the
        // token now. This is a coarse, trivially-spoofable signal (a header, not a credential) -
        // it stops a leaked token being casually replayed from a different client, not a
        // determined attacker who copies the header too. Treated as seriously as token reuse:
        // revoke immediately rather than just deny, since a mismatch means this token is already
        // compromised from the legitimate holder's perspective too. IP is recorded for the same
        // audit trail but deliberately NOT enforced - unlike User-Agent, a legitimate client's IP
        // changes constantly (mobile networks, VPNs, ISP rotation), so hard-blocking on it would
        // lock out real users far more often than it would stop an attacker.
        // Compared from the guaranteed-non-null side (userAgent()/clientIp() always return a
        // value, "unknown" at worst) so a legacy pre-device-binding row - null userAgent/issuedIp,
        // since those columns were added later without a backfill - fails closed as a mismatch
        // rather than NPEing.
        String currentUserAgent = userAgent();
        if (!currentUserAgent.equals(existing.getUserAgent())) {
            existing.setRevoked(true);
            refreshTokenRepository.save(existing);
            log.warn("Refresh rejected - User-Agent mismatch, token revoked (userId={}, issuedUserAgent={}, presentedUserAgent={})",
                    existing.getUser().getId(), existing.getUserAgent(), currentUserAgent);
            throw new BadCredentialsException("Invalid refresh token");
        }
        if (!clientIp().equals(existing.getIssuedIp())) {
            log.warn("Refresh from a different IP than issuance - allowed, not enforced (userId={}, issuedIp={}, presentedIp={})",
                    existing.getUser().getId(), existing.getIssuedIp(), clientIp());
        }

        // Rotate: burn this token the instant it's used, so replay fails even if it leaks.
        existing.setRevoked(true);
        refreshTokenRepository.save(existing);

        return issueTokenPair(existing.getUser());
    }

    /**
     * Dev-style password reset: this returns the raw reset token directly in the response body
     * instead of emailing it, since the project has no SMTP infrastructure. <b>This is explicitly
     * not production-safe</b> - a real deployment must replace this with an actual email send and
     * drop the token from the response, otherwise anyone who can call this endpoint can reset any
     * account's password. See the README's "Password reset" section.
     *
     * <p>Always returns 200 (never 404) regardless of whether the email matches an account, to
     * avoid trivially confirming which emails are registered - though note the response body
     * itself already reveals that (null token vs. a real one), which a production version backed
     * by real email delivery would not do.
     */
    @Transactional
    public PasswordResetResponse requestPasswordReset(PasswordResetRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();
        Optional<User> user = userRepository.findByEmail(normalizedEmail);
        if (user.isEmpty()) {
            log.info("Password reset requested for unrecognized email={}", normalizedEmail);
            return new PasswordResetResponse(null, null);
        }

        String rawToken = generateRawToken(PASSWORD_RESET_TOKEN_BYTES);
        Instant expiresAt = Instant.now().plus(passwordResetTokenTtl);
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .user(user.get())
                .tokenHash(sha256(rawToken))
                .expiresAt(expiresAt)
                .used(false)
                .build();
        passwordResetTokenRepository.save(resetToken);
        log.info("Password reset token issued (userId={})", user.get().getId());
        return new PasswordResetResponse(rawToken, expiresAt);
    }

    @Transactional
    public void confirmPasswordReset(PasswordResetConfirmRequest request) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHash(sha256(request.token()))
                .orElseThrow(() -> {
                    log.warn("Password reset rejected - token not recognized");
                    return new BadRequestException("Invalid or expired reset token");
                });

        if (resetToken.isUsed() || resetToken.isExpired()) {
            log.warn("Password reset rejected - {} token (userId={})",
                    resetToken.isUsed() ? "already-used" : "expired", resetToken.getUser().getId());
            throw new BadRequestException("Invalid or expired reset token");
        }

        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);

        // A password reset is exactly the moment any existing session might be exactly what the
        // user is trying to lock out (leaked credentials) - so every refresh token they currently
        // hold gets revoked too, not just the password changed.
        List<RefreshToken> activeSessions = refreshTokenRepository.findAllByUserAndRevokedFalse(user);
        activeSessions.forEach(t -> t.setRevoked(true));
        refreshTokenRepository.saveAll(activeSessions);

        log.info("Password reset completed (userId={}), {} active session(s) revoked",
                user.getId(), activeSessions.size());
    }

    /**
     * Same dev-mode tradeoff as {@link #requestPasswordReset}, for the same reason (no SMTP
     * infrastructure in this project): the token is returned directly instead of being emailed.
     *
     * <p>Collapses two different "nothing to do" cases - no account for this email, and an
     * already-verified account - into the same null-token response, so this endpoint doesn't leak
     * either an account's existence or its verification status to an unauthenticated caller.
     */
    @Transactional
    public EmailVerificationResponse requestEmailVerification(EmailVerificationRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();
        Optional<User> user = userRepository.findByEmail(normalizedEmail);
        if (user.isEmpty() || user.get().isEmailVerified()) {
            log.info("Email verification requested for email={} - no eligible account", normalizedEmail);
            return new EmailVerificationResponse(null, null);
        }

        String rawToken = generateRawToken(EMAIL_VERIFICATION_TOKEN_BYTES);
        Instant expiresAt = Instant.now().plus(emailVerificationTokenTtl);
        EmailVerificationToken verificationToken = EmailVerificationToken.builder()
                .user(user.get())
                .tokenHash(sha256(rawToken))
                .expiresAt(expiresAt)
                .used(false)
                .build();
        emailVerificationTokenRepository.save(verificationToken);
        log.info("Email verification token issued (userId={})", user.get().getId());
        return new EmailVerificationResponse(rawToken, expiresAt);
    }

    @Transactional
    public void confirmEmailVerification(EmailVerificationConfirmRequest request) {
        EmailVerificationToken verificationToken = emailVerificationTokenRepository.findByTokenHash(sha256(request.token()))
                .orElseThrow(() -> {
                    log.warn("Email verification rejected - token not recognized");
                    return new BadRequestException("Invalid or expired verification token");
                });

        if (verificationToken.isUsed() || verificationToken.isExpired()) {
            log.warn("Email verification rejected - {} token (userId={})",
                    verificationToken.isUsed() ? "already-used" : "expired", verificationToken.getUser().getId());
            throw new BadRequestException("Invalid or expired verification token");
        }

        User user = verificationToken.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);

        verificationToken.setUsed(true);
        emailVerificationTokenRepository.save(verificationToken);

        log.info("Email verified (userId={})", user.getId());
    }

    private AuthResponse issueTokenPair(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String rawRefreshToken = generateRawToken(REFRESH_TOKEN_BYTES);

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(sha256(rawRefreshToken))
                .expiresAt(Instant.now().plus(refreshTokenTtl))
                .revoked(false)
                .userAgent(userAgent())
                .issuedIp(clientIp())
                .build();
        refreshTokenRepository.save(refreshToken);

        return new AuthResponse(accessToken, rawRefreshToken);
    }

    /** "unknown" rather than null when absent, so refresh's equality check never NPEs on a client that omits the header. */
    private String userAgent() {
        String header = httpRequest.getHeader(HttpHeaders.USER_AGENT);
        return header == null || header.isBlank() ? "unknown" : header;
    }

    private String clientIp() {
        return httpRequest.getRemoteAddr();
    }

    private String generateRawToken(int byteLength) {
        byte[] bytes = new byte[byteLength];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
