package com.hireflow.admin;

import com.hireflow.user.entity.Role;
import com.hireflow.user.entity.User;
import com.hireflow.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Closes the bootstrap gap the README used to leave to a hand-written SQL {@code UPDATE}:
 * self-registration ({@code POST /api/auth/register}) always forces {@code CANDIDATE}, and
 * {@code POST /api/admin/users} itself requires an existing ADMIN caller, so without this there
 * is no path at all to the *first* admin account.
 *
 * <p>Opt-in and idempotent: a no-op unless both {@code ADMIN_BOOTSTRAP_EMAIL} and
 * {@code ADMIN_BOOTSTRAP_PASSWORD} are set, and even then only creates an account if no ADMIN
 * exists yet - it never resets an existing admin's password on a later restart.
 */
@Component
@Slf4j
public class AdminBootstrapRunner implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String bootstrapEmail;
    private final String bootstrapPassword;

    public AdminBootstrapRunner(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${hireflow.admin-bootstrap.email:}") String bootstrapEmail,
            @Value("${hireflow.admin-bootstrap.password:}") String bootstrapPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.bootstrapEmail = bootstrapEmail;
        this.bootstrapPassword = bootstrapPassword;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (bootstrapEmail.isBlank() || bootstrapPassword.isBlank()) {
            return;
        }
        if (userRepository.existsByRole(Role.ADMIN)) {
            return;
        }

        String normalizedEmail = bootstrapEmail.trim().toLowerCase();
        if (userRepository.existsByEmail(normalizedEmail)) {
            log.warn("ADMIN_BOOTSTRAP_EMAIL ({}) already belongs to an existing non-admin account; "
                    + "skipping bootstrap. Promote it via POST /api/admin/users or a different address.",
                    normalizedEmail);
            return;
        }

        User admin = User.builder()
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(bootstrapPassword))
                .role(Role.ADMIN)
                .emailVerified(true)
                .build();
        userRepository.save(admin);
        log.info("Bootstrapped initial ADMIN account: {}", normalizedEmail);
    }
}
