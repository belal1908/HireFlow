package com.hireflow.posting;

import com.hireflow.posting.entity.JobPosting;
import com.hireflow.posting.entity.PostingStatus;
import com.hireflow.support.AbstractIntegrationTest;
import com.hireflow.user.entity.Role;
import com.hireflow.user.entity.User;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PostingIntegrationTest extends AbstractIntegrationTest {

    @Test
    void list_asCandidate_returnsOnlyOpenPostings() throws Exception {
        User admin = createUser(Role.ADMIN);
        JobPosting open = createPosting(admin, PostingStatus.OPEN);
        JobPosting closed = createPosting(admin, PostingStatus.CLOSED);
        User candidate = createUser(Role.CANDIDATE);

        mockMvc.perform(get("/api/postings").header("Authorization", bearerToken(candidate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + open.getId() + ")]").exists())
                .andExpect(jsonPath("$[?(@.id == " + closed.getId() + ")]").doesNotExist());
    }

    @Test
    void list_asAdmin_returnsOpenAndClosedPostings() throws Exception {
        User admin = createUser(Role.ADMIN);
        JobPosting open = createPosting(admin, PostingStatus.OPEN);
        JobPosting closed = createPosting(admin, PostingStatus.CLOSED);

        mockMvc.perform(get("/api/postings").header("Authorization", bearerToken(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + open.getId() + ")]").exists())
                .andExpect(jsonPath("$[?(@.id == " + closed.getId() + ")]").exists());
    }

    @Test
    void list_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(get("/api/postings")).andExpect(status().isUnauthorized());
    }

    @Test
    void create_asAdmin_succeeds() throws Exception {
        User admin = createUser(Role.ADMIN);
        String body = """
                {"title": "Senior Backend Engineer", "description": "Java, Spring Boot"}
                """;

        mockMvc.perform(post("/api/postings")
                        .header("Authorization", bearerToken(admin))
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Senior Backend Engineer"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.createdBy").value(admin.getId()));
    }

    @Test
    void create_asRecruiter_returns403() throws Exception {
        User recruiter = createUser(Role.RECRUITER);
        String body = """
                {"title": "Should Not Be Created"}
                """;

        mockMvc.perform(post("/api/postings")
                        .header("Authorization", bearerToken(recruiter))
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_asCandidate_returns403() throws Exception {
        User candidate = createUser(Role.CANDIDATE);
        String body = """
                {"title": "Should Not Be Created"}
                """;

        mockMvc.perform(post("/api/postings")
                        .header("Authorization", bearerToken(candidate))
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_withBlankTitle_returns400() throws Exception {
        User admin = createUser(Role.ADMIN);
        String body = """
                {"title": ""}
                """;

        mockMvc.perform(post("/api/postings")
                        .header("Authorization", bearerToken(admin))
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_asAdmin_appliesPatchAndSucceeds() throws Exception {
        User admin = createUser(Role.ADMIN);
        JobPosting posting = createPosting(admin, PostingStatus.OPEN);
        String body = """
                {"status": "CLOSED"}
                """;

        mockMvc.perform(patch("/api/postings/" + posting.getId())
                        .header("Authorization", bearerToken(admin))
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.title").value(posting.getTitle()));
    }

    @Test
    void update_asRecruiter_returns403() throws Exception {
        User admin = createUser(Role.ADMIN);
        User recruiter = createUser(Role.RECRUITER);
        JobPosting posting = createPosting(admin, PostingStatus.OPEN);
        String body = """
                {"status": "CLOSED"}
                """;

        mockMvc.perform(patch("/api/postings/" + posting.getId())
                        .header("Authorization", bearerToken(recruiter))
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void update_nonExistentPosting_returns404() throws Exception {
        User admin = createUser(Role.ADMIN);
        String body = """
                {"status": "CLOSED"}
                """;

        mockMvc.perform(patch("/api/postings/999999999")
                        .header("Authorization", bearerToken(admin))
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isNotFound());
    }
}
