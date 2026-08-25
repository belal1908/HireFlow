package com.hireflow.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hireflow.posting.entity.JobPosting;
import com.hireflow.posting.entity.PostingStatus;
import com.hireflow.posting.repository.JobPostingRepository;
import com.hireflow.security.JwtService;
import com.hireflow.user.entity.Role;
import com.hireflow.user.entity.User;
import com.hireflow.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.UUID;

/**
 * Shared base for all "real Postgres via Testcontainers" integration tests. Every subclass
 * gets a MockMvc wired against the full Spring context (real Spring Security filter chain,
 * real @PreAuthorize checks, real JPA/Hibernate against a real Postgres instance - not H2).
 *
 * <p>This deliberately does NOT use {@code @Testcontainers}/{@code @Container}: that annotation
 * pair ties a container's lifecycle to a single test class's JUnit callbacks, and even a
 * {@code static} field gets torn down after the first subclass's tests finish - breaking every
 * subclass that runs after it. Instead this follows Testcontainers' documented "singleton
 * container" pattern: start the container once, manually, in a static initializer, and let it
 * live for the whole JVM (Ryuk - or the OS, in local dev - reaps it afterwards).
 *
 * <p>The container and Spring context are reused across all subclasses (identical
 * configuration -> Spring's test context cache keeps one context alive for the whole suite),
 * so tests must use randomized emails/titles rather than assuming a pristine, empty database.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("hireflow_test")
            .withUsername("hireflow_test")
            .withPassword("hireflow_test")
            .withReuse(false);

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("hireflow.jwt.secret",
                () -> "integration-test-signing-secret-at-least-32-bytes-long-0123456789");
        registry.add("hireflow.jwt.access-token-ttl-minutes", () -> "15");
        registry.add("hireflow.jwt.refresh-token-ttl-days", () -> "7");
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected JobPostingRepository jobPostingRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected JwtService jwtService;

    protected static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@hireflow.test";
    }

    /** Persists a user directly (bypassing the register endpoint, which always forces CANDIDATE). */
    protected User createUser(Role role) {
        User user = User.builder()
                .email(uniqueEmail(role.name().toLowerCase()))
                .passwordHash(passwordEncoder.encode("Password123!"))
                .role(role)
                .build();
        return userRepository.save(user);
    }

    protected User createUser(Role role, String rawPassword) {
        User user = User.builder()
                .email(uniqueEmail(role.name().toLowerCase()))
                .passwordHash(passwordEncoder.encode(rawPassword))
                .role(role)
                .build();
        return userRepository.save(user);
    }

    protected String bearerToken(User user) {
        return "Bearer " + jwtService.generateAccessToken(user);
    }

    protected JobPosting createPosting(User admin, PostingStatus status) {
        JobPosting posting = JobPosting.builder()
                .title("Posting " + UUID.randomUUID())
                .description("Test posting")
                .status(status)
                .createdBy(admin)
                .build();
        return jobPostingRepository.save(posting);
    }
}
