package com.hireflow.admin;

import com.hireflow.user.entity.Role;
import com.hireflow.user.entity.User;
import com.hireflow.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Direct unit tests for {@link AdminBootstrapRunner} - no Spring context, matching
 * {@code RateLimitingFilterTest}'s style, since the runner's behavior is a handful of plain
 * conditionals over a mocked repository.
 */
class AdminBootstrapRunnerTest {

    private static final PasswordEncoder ENCODER = new BCryptPasswordEncoder();

    @Test
    void bothPropertiesUnset_doesNothing() {
        UserRepository userRepository = mock(UserRepository.class);
        AdminBootstrapRunner runner = new AdminBootstrapRunner(userRepository, ENCODER, "", "");

        runner.run();

        verify(userRepository, never()).existsByRole(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void onlyEmailSet_doesNothing() {
        UserRepository userRepository = mock(UserRepository.class);
        AdminBootstrapRunner runner = new AdminBootstrapRunner(userRepository, ENCODER, "admin@example.com", "");

        runner.run();

        verify(userRepository, never()).save(any());
    }

    @Test
    void onlyPasswordSet_doesNothing() {
        UserRepository userRepository = mock(UserRepository.class);
        AdminBootstrapRunner runner = new AdminBootstrapRunner(userRepository, ENCODER, "", "Password123!");

        runner.run();

        verify(userRepository, never()).save(any());
    }

    @Test
    void adminAlreadyExists_doesNotCreateAnotherOne() {
        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(true);
        AdminBootstrapRunner runner =
                new AdminBootstrapRunner(userRepository, ENCODER, "admin@example.com", "Password123!");

        runner.run();

        verify(userRepository, never()).save(any());
    }

    @Test
    void emailAlreadyTakenByNonAdmin_skipsWithoutCreating() {
        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);
        when(userRepository.existsByEmail("admin@example.com")).thenReturn(true);
        AdminBootstrapRunner runner =
                new AdminBootstrapRunner(userRepository, ENCODER, "admin@example.com", "Password123!");

        runner.run();

        verify(userRepository, never()).save(any());
    }

    @Test
    void noAdminExists_bothPropertiesSet_createsAdminWithNormalizedEmailAndUsablePasswordHash() {
        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);
        when(userRepository.existsByEmail("admin@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AdminBootstrapRunner runner =
                new AdminBootstrapRunner(userRepository, ENCODER, "  Admin@Example.com  ", "Password123!");

        runner.run();

        var captor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User created = captor.getValue();
        assertThat(created.getEmail()).isEqualTo("admin@example.com");
        assertThat(created.getRole()).isEqualTo(Role.ADMIN);
        assertThat(ENCODER.matches("Password123!", created.getPasswordHash())).isTrue();
    }
}
