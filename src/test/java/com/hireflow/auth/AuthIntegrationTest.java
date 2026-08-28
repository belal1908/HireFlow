package com.hireflow.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.hireflow.auth.entity.PasswordResetToken;
import com.hireflow.auth.repository.PasswordResetTokenRepository;
import com.hireflow.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Test
    void register_createsCandidateAccount() throws Exception {
        String email = uniqueEmail("register");
        String body = """
                {"email": "%s", "password": "Password123!"}
                """.formatted(email);

        mockMvc.perform(post("/api/auth/register").contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("CANDIDATE"))
                .andExpect(jsonPath("$.id").exists());
    }

    @Test
    void register_ignoresClientSuppliedRole_forcesCandidate() throws Exception {
        // Even if a malicious/naive client tries to self-register as ADMIN, the server must
        // force CANDIDATE. The DTO has no role field at all, so this also exercises that extra
        // JSON properties are tolerated (fail-on-unknown-properties disabled), not rejected.
        String email = uniqueEmail("privesc");
        String body = """
                {"email": "%s", "password": "Password123!", "role": "ADMIN"}
                """.formatted(email);

        mockMvc.perform(post("/api/auth/register").contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("CANDIDATE"));
    }

    @Test
    void register_duplicateEmail_returns409() throws Exception {
        String email = uniqueEmail("dupe");
        String body = """
                {"email": "%s", "password": "Password123!"}
                """.formatted(email);

        mockMvc.perform(post("/api/auth/register").contentType("application/json").content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register").contentType("application/json").content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void register_invalidEmail_returns400() throws Exception {
        String body = """
                {"email": "not-an-email", "password": "Password123!"}
                """;

        mockMvc.perform(post("/api/auth/register").contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_shortPassword_returns400() throws Exception {
        String body = """
                {"email": "%s", "password": "short"}
                """.formatted(uniqueEmail("shortpw"));

        mockMvc.perform(post("/api/auth/register").contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_withCorrectCredentials_returnsTokenPair() throws Exception {
        String email = uniqueEmail("login");
        registerCandidate(email, "Password123!");

        String loginBody = """
                {"email": "%s", "password": "Password123!"}
                """.formatted(email);

        mockMvc.perform(post("/api/auth/login").contentType("application/json").content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists());
    }

    @Test
    void login_withWrongPassword_returns401() throws Exception {
        String email = uniqueEmail("wrongpw");
        registerCandidate(email, "Password123!");

        String loginBody = """
                {"email": "%s", "password": "TotallyWrong1!"}
                """.formatted(email);

        mockMvc.perform(post("/api/auth/login").contentType("application/json").content(loginBody))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_withUnknownEmail_returns401() throws Exception {
        String loginBody = """
                {"email": "%s", "password": "Password123!"}
                """.formatted(uniqueEmail("ghost"));

        mockMvc.perform(post("/api/auth/login").contentType("application/json").content(loginBody))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_withValidToken_rotatesAndReturnsNewPair() throws Exception {
        String email = uniqueEmail("refresh");
        registerCandidate(email, "Password123!");
        String refreshToken = login(email, "Password123!").get("refreshToken").asText();

        String refreshBody = """
                {"refreshToken": "%s"}
                """.formatted(refreshToken);

        mockMvc.perform(post("/api/auth/refresh").contentType("application/json").content(refreshBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists());
    }

    @Test
    void refresh_reusingAnAlreadyRotatedToken_returns401() throws Exception {
        String email = uniqueEmail("replay");
        registerCandidate(email, "Password123!");
        String originalRefreshToken = login(email, "Password123!").get("refreshToken").asText();

        String refreshBody = """
                {"refreshToken": "%s"}
                """.formatted(originalRefreshToken);

        // First use: succeeds and rotates.
        mockMvc.perform(post("/api/auth/refresh").contentType("application/json").content(refreshBody))
                .andExpect(status().isOk());

        // Replaying the SAME (now-revoked) token must fail.
        mockMvc.perform(post("/api/auth/refresh").contentType("application/json").content(refreshBody))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_withGarbageToken_returns401() throws Exception {
        String refreshBody = """
                {"refreshToken": "not-a-real-token"}
                """;

        mockMvc.perform(post("/api/auth/refresh").contentType("application/json").content(refreshBody))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_withDifferentUserAgent_returns401_andRevokesTheToken() throws Exception {
        String email = uniqueEmail("ua-mismatch");
        registerCandidate(email, "Password123!");
        String refreshToken = loginWithUserAgent(email, "Password123!", "DeviceA/1.0").get("refreshToken").asText();

        String refreshBody = """
                {"refreshToken": "%s"}
                """.formatted(refreshToken);

        // Same token, different device: rejected.
        mockMvc.perform(post("/api/auth/refresh")
                        .header("User-Agent", "DeviceB/1.0")
                        .contentType("application/json")
                        .content(refreshBody))
                .andExpect(status().isUnauthorized());

        // The mismatch burns the token, so even the ORIGINAL device can't use it anymore either -
        // a mismatch is treated as "this token is compromised", not just "wrong caller, try again".
        mockMvc.perform(post("/api/auth/refresh")
                        .header("User-Agent", "DeviceA/1.0")
                        .contentType("application/json")
                        .content(refreshBody))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_withSameUserAgent_succeeds() throws Exception {
        String email = uniqueEmail("ua-match");
        registerCandidate(email, "Password123!");
        String refreshToken = loginWithUserAgent(email, "Password123!", "DeviceA/1.0").get("refreshToken").asText();

        String refreshBody = """
                {"refreshToken": "%s"}
                """.formatted(refreshToken);

        mockMvc.perform(post("/api/auth/refresh")
                        .header("User-Agent", "DeviceA/1.0")
                        .contentType("application/json")
                        .content(refreshBody))
                .andExpect(status().isOk());
    }

    /**
     * IP is recorded (see RefreshToken#issuedIp) but deliberately NOT enforced - see the class
     * comment on AuthService#refresh for why a legitimate client's IP is expected to move around
     * far more often than its User-Agent does.
     */
    @Test
    void refresh_fromADifferentIp_isAllowed_notEnforced() throws Exception {
        String email = uniqueEmail("ip-roam");
        registerCandidate(email, "Password123!");
        String loginResponse = mockMvc.perform(post("/api/auth/login")
                        .with(request -> {
                            request.setRemoteAddr("10.0.0.1");
                            return request;
                        })
                        .contentType("application/json")
                        .content("""
                                {"email": "%s", "password": "Password123!"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String refreshToken = objectMapper.readTree(loginResponse).get("refreshToken").asText();

        mockMvc.perform(post("/api/auth/refresh")
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.7");
                            return request;
                        })
                        .contentType("application/json")
                        .content("""
                                {"refreshToken": "%s"}
                                """.formatted(refreshToken)))
                .andExpect(status().isOk());
    }

    private JsonNode loginWithUserAgent(String email, String password, String userAgent) throws Exception {
        String body = """
                {"email": "%s", "password": "%s"}
                """.formatted(email, password);
        String response = mockMvc.perform(post("/api/auth/login")
                        .header("User-Agent", userAgent)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    @Test
    void protectedEndpoint_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/postings")).andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_withGarbageToken_returns401() throws Exception {
        mockMvc.perform(get("/api/postings").header("Authorization", "Bearer garbage.token.value"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void passwordReset_forExistingAccount_returnsAToken() throws Exception {
        String email = uniqueEmail("reset-exists");
        registerCandidate(email, "Password123!");

        String body = """
                {"email": "%s"}
                """.formatted(email);

        mockMvc.perform(post("/api/auth/password-reset/request").contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resetToken").exists())
                .andExpect(jsonPath("$.expiresAt").exists());
    }

    @Test
    void passwordReset_forUnknownEmail_returns200WithNullToken() throws Exception {
        String body = """
                {"email": "%s"}
                """.formatted(uniqueEmail("reset-ghost"));

        mockMvc.perform(post("/api/auth/password-reset/request").contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resetToken").doesNotExist());
    }

    @Test
    void passwordReset_requestWithInvalidEmail_returns400() throws Exception {
        String body = """
                {"email": "not-an-email"}
                """;

        mockMvc.perform(post("/api/auth/password-reset/request").contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void passwordReset_confirmWithValidToken_changesPassword_oldPasswordNoLongerWorks() throws Exception {
        String email = uniqueEmail("reset-confirm");
        registerCandidate(email, "OldPassword123!");
        String token = requestPasswordReset(email);

        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType("application/json")
                        .content("""
                                {"token": "%s", "newPassword": "NewPassword456!"}
                                """.formatted(token)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/login").contentType("application/json").content("""
                        {"email": "%s", "password": "NewPassword456!"}
                        """.formatted(email)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login").contentType("application/json").content("""
                        {"email": "%s", "password": "OldPassword123!"}
                        """.formatted(email)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void passwordReset_confirmRevokesExistingRefreshTokens() throws Exception {
        String email = uniqueEmail("reset-revoke");
        registerCandidate(email, "OldPassword123!");
        String staleRefreshToken = login(email, "OldPassword123!").get("refreshToken").asText();
        String token = requestPasswordReset(email);

        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType("application/json")
                        .content("""
                                {"token": "%s", "newPassword": "NewPassword456!"}
                                """.formatted(token)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/refresh").contentType("application/json").content("""
                        {"refreshToken": "%s"}
                        """.formatted(staleRefreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void passwordReset_confirmWithGarbageToken_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType("application/json")
                        .content("""
                                {"token": "not-a-real-token", "newPassword": "NewPassword456!"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void passwordReset_confirmReusingAnAlreadyUsedToken_returns400() throws Exception {
        String email = uniqueEmail("reset-reuse");
        registerCandidate(email, "OldPassword123!");
        String token = requestPasswordReset(email);
        String confirmBody = """
                {"token": "%s", "newPassword": "NewPassword456!"}
                """.formatted(token);

        mockMvc.perform(post("/api/auth/password-reset/confirm").contentType("application/json").content(confirmBody))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/password-reset/confirm").contentType("application/json").content(confirmBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void passwordReset_confirmWithExpiredToken_returns400() throws Exception {
        String email = uniqueEmail("reset-expired");
        registerCandidate(email, "OldPassword123!");
        String token = requestPasswordReset(email);

        Long userId = userRepository.findByEmail(email).orElseThrow().getId();
        PasswordResetToken stored = passwordResetTokenRepository.findAll().stream()
                .filter(t -> t.getUser().getId().equals(userId))
                .findFirst().orElseThrow();
        stored.setExpiresAt(Instant.now().minusSeconds(60));
        passwordResetTokenRepository.save(stored);

        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType("application/json")
                        .content("""
                                {"token": "%s", "newPassword": "NewPassword456!"}
                                """.formatted(token)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void passwordReset_confirmWithShortNewPassword_returns400() throws Exception {
        String email = uniqueEmail("reset-shortpw");
        registerCandidate(email, "OldPassword123!");
        String token = requestPasswordReset(email);

        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType("application/json")
                        .content("""
                                {"token": "%s", "newPassword": "short"}
                                """.formatted(token)))
                .andExpect(status().isBadRequest());

        Long userId = userRepository.findByEmail(email).orElseThrow().getId();
        assertThat(passwordResetTokenRepository.findAll().stream()
                        .anyMatch(t -> t.getUser().getId().equals(userId) && t.isUsed()))
                .as("a token rejected by validation before the service ever ran must not be marked used")
                .isFalse();
    }

    private String requestPasswordReset(String email) throws Exception {
        String body = """
                {"email": "%s"}
                """.formatted(email);
        String response = mockMvc.perform(post("/api/auth/password-reset/request")
                        .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("resetToken").asText();
    }

    private void registerCandidate(String email, String password) throws Exception {
        String body = """
                {"email": "%s", "password": "%s"}
                """.formatted(email, password);
        mockMvc.perform(post("/api/auth/register").contentType("application/json").content(body))
                .andExpect(status().isCreated());
    }

    private JsonNode login(String email, String password) throws Exception {
        String body = """
                {"email": "%s", "password": "%s"}
                """.formatted(email, password);
        String response = mockMvc.perform(post("/api/auth/login").contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }
}
