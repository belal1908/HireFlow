package com.hireflow.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.hireflow.support.AbstractIntegrationTest;
import com.hireflow.user.entity.Role;
import com.hireflow.user.entity.User;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminUserIntegrationTest extends AbstractIntegrationTest {

    @Test
    void create_asAdmin_createsRecruiterAccount_appearsInList_andCanLoginAndUseRecruiterEndpoints() throws Exception {
        User admin = createUser(Role.ADMIN);
        String email = uniqueEmail("new-recruiter");
        String body = """
                {"email": "%s", "password": "Password123!", "role": "RECRUITER"}
                """.formatted(email);

        // 1. Admin creates the account.
        mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", bearerToken(admin))
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("RECRUITER"))
                .andExpect(jsonPath("$.id").exists());

        // 2. It shows up in the admin's user list.
        mockMvc.perform(get("/api/admin/users").header("Authorization", bearerToken(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email == '" + email + "')]").exists());

        // 3. And filtering by role finds it too.
        mockMvc.perform(get("/api/admin/users?role=RECRUITER").header("Authorization", bearerToken(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email == '" + email + "')]").exists());

        // 4. The created account can actually log in...
        String loginBody = """
                {"email": "%s", "password": "Password123!"}
                """.formatted(email);
        String loginResponse = mockMvc.perform(post("/api/auth/login").contentType("application/json").content(loginBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode tokens = objectMapper.readTree(loginResponse);
        String accessToken = tokens.get("accessToken").asText();

        // 5. ...and use a RECRUITER-gated endpoint end-to-end (proves the role in the JWT is real).
        mockMvc.perform(get("/api/applications").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    @Test
    void create_asAdmin_createsAdminAccount_succeeds() throws Exception {
        User admin = createUser(Role.ADMIN);
        String email = uniqueEmail("new-admin");
        String body = """
                {"email": "%s", "password": "Password123!", "role": "ADMIN"}
                """.formatted(email);

        mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", bearerToken(admin))
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void create_asCandidate_returns403() throws Exception {
        User candidate = createUser(Role.CANDIDATE);
        String body = """
                {"email": "%s", "password": "Password123!", "role": "RECRUITER"}
                """.formatted(uniqueEmail("blocked"));

        mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", bearerToken(candidate))
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_asRecruiter_returns403() throws Exception {
        User recruiter = createUser(Role.RECRUITER);
        String body = """
                {"email": "%s", "password": "Password123!", "role": "RECRUITER"}
                """.formatted(uniqueEmail("blocked"));

        mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", bearerToken(recruiter))
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_withoutAuthentication_returns401() throws Exception {
        String body = """
                {"email": "%s", "password": "Password123!", "role": "RECRUITER"}
                """.formatted(uniqueEmail("blocked"));

        mockMvc.perform(post("/api/admin/users").contentType("application/json").content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_withRoleCandidate_returns400() throws Exception {
        User admin = createUser(Role.ADMIN);
        String body = """
                {"email": "%s", "password": "Password123!", "role": "CANDIDATE"}
                """.formatted(uniqueEmail("shouldnt-exist"));

        mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", bearerToken(admin))
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_withMissingRole_returns400() throws Exception {
        User admin = createUser(Role.ADMIN);
        String body = """
                {"email": "%s", "password": "Password123!"}
                """.formatted(uniqueEmail("no-role"));

        mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", bearerToken(admin))
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_withInvalidRole_returns400() throws Exception {
        User admin = createUser(Role.ADMIN);
        String body = """
                {"email": "%s", "password": "Password123!", "role": "SUPERUSER"}
                """.formatted(uniqueEmail("bad-role"));

        mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", bearerToken(admin))
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_duplicateEmail_returns409() throws Exception {
        User admin = createUser(Role.ADMIN);
        String email = uniqueEmail("dupe-admin-created");
        String body = """
                {"email": "%s", "password": "Password123!", "role": "RECRUITER"}
                """.formatted(email);

        mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", bearerToken(admin))
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", bearerToken(admin))
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void create_emailAlreadyUsedByCandidate_returns409() throws Exception {
        User admin = createUser(Role.ADMIN);
        String email = uniqueEmail("existing-candidate");
        String registerBody = """
                {"email": "%s", "password": "Password123!"}
                """.formatted(email);
        mockMvc.perform(post("/api/auth/register").contentType("application/json").content(registerBody))
                .andExpect(status().isCreated());

        String adminBody = """
                {"email": "%s", "password": "Password123!", "role": "RECRUITER"}
                """.formatted(email);
        mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", bearerToken(admin))
                        .contentType("application/json")
                        .content(adminBody))
                .andExpect(status().isConflict());
    }

    @Test
    void create_shortPassword_returns400() throws Exception {
        User admin = createUser(Role.ADMIN);
        String body = """
                {"email": "%s", "password": "short", "role": "RECRUITER"}
                """.formatted(uniqueEmail("shortpw"));

        mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", bearerToken(admin))
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_invalidEmail_returns400() throws Exception {
        User admin = createUser(Role.ADMIN);
        String body = """
                {"email": "not-an-email", "password": "Password123!", "role": "RECRUITER"}
                """;

        mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", bearerToken(admin))
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void list_asCandidate_returns403() throws Exception {
        User candidate = createUser(Role.CANDIDATE);

        mockMvc.perform(get("/api/admin/users").header("Authorization", bearerToken(candidate)))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_asRecruiter_returns403() throws Exception {
        User recruiter = createUser(Role.RECRUITER);

        mockMvc.perform(get("/api/admin/users").header("Authorization", bearerToken(recruiter)))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
    }
}
