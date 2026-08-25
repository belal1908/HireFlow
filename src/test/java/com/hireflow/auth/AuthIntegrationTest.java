package com.hireflow.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.hireflow.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIntegrationTest extends AbstractIntegrationTest {

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
    void protectedEndpoint_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/postings")).andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_withGarbageToken_returns401() throws Exception {
        mockMvc.perform(get("/api/postings").header("Authorization", "Bearer garbage.token.value"))
                .andExpect(status().isUnauthorized());
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
