package com.hireflow.application;

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

/**
 * Covers the full application lifecycle endpoints: apply, mine, list, status transitions
 * (delegated to TransitionValidator), and the audit trail - including the explicit proof that
 * a CANDIDATE cannot read or act on another candidate's Application via a direct API call.
 */
class ApplicationIntegrationTest extends AbstractIntegrationTest {

    // ---- POST /api/applications ----------------------------------------------------------

    @Test
    void apply_asCandidate_createsApplicationInAppliedStatus() throws Exception {
        User admin = createUser(Role.ADMIN);
        User candidate = createUser(Role.CANDIDATE);
        JobPosting posting = createPosting(admin, PostingStatus.OPEN);

        mockMvc.perform(post("/api/applications")
                        .header("Authorization", bearerToken(candidate))
                        .contentType("application/json")
                        .content("{\"jobPostingId\": " + posting.getId() + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andExpect(jsonPath("$.candidateId").value(candidate.getId()))
                .andExpect(jsonPath("$.jobPostingId").value(posting.getId()));
    }

    @Test
    void apply_asRecruiter_returns403() throws Exception {
        User admin = createUser(Role.ADMIN);
        User recruiter = createUser(Role.RECRUITER);
        JobPosting posting = createPosting(admin, PostingStatus.OPEN);

        mockMvc.perform(post("/api/applications")
                        .header("Authorization", bearerToken(recruiter))
                        .contentType("application/json")
                        .content("{\"jobPostingId\": " + posting.getId() + "}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void apply_asAdmin_returns403() throws Exception {
        User admin = createUser(Role.ADMIN);
        JobPosting posting = createPosting(admin, PostingStatus.OPEN);

        mockMvc.perform(post("/api/applications")
                        .header("Authorization", bearerToken(admin))
                        .contentType("application/json")
                        .content("{\"jobPostingId\": " + posting.getId() + "}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void apply_twiceToSamePosting_returns409() throws Exception {
        User admin = createUser(Role.ADMIN);
        User candidate = createUser(Role.CANDIDATE);
        JobPosting posting = createPosting(admin, PostingStatus.OPEN);
        String body = "{\"jobPostingId\": " + posting.getId() + "}";

        mockMvc.perform(post("/api/applications")
                        .header("Authorization", bearerToken(candidate))
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/applications")
                        .header("Authorization", bearerToken(candidate))
                        .contentType("application/json").content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void apply_toClosedPosting_returns409() throws Exception {
        User admin = createUser(Role.ADMIN);
        User candidate = createUser(Role.CANDIDATE);
        JobPosting posting = createPosting(admin, PostingStatus.CLOSED);

        mockMvc.perform(post("/api/applications")
                        .header("Authorization", bearerToken(candidate))
                        .contentType("application/json")
                        .content("{\"jobPostingId\": " + posting.getId() + "}"))
                .andExpect(status().isConflict());
    }

    @Test
    void apply_toNonExistentPosting_returns404() throws Exception {
        User candidate = createUser(Role.CANDIDATE);

        mockMvc.perform(post("/api/applications")
                        .header("Authorization", bearerToken(candidate))
                        .contentType("application/json")
                        .content("{\"jobPostingId\": 999999999}"))
                .andExpect(status().isNotFound());
    }

    // ---- GET /api/applications/mine -------------------------------------------------------

    @Test
    void mine_returnsOnlyOwnApplications() throws Exception {
        User admin = createUser(Role.ADMIN);
        User candidateA = createUser(Role.CANDIDATE);
        User candidateB = createUser(Role.CANDIDATE);
        JobPosting postingA = createPosting(admin, PostingStatus.OPEN);
        JobPosting postingB = createPosting(admin, PostingStatus.OPEN);

        Long applicationA = apply(candidateA, postingA);
        apply(candidateB, postingB);

        mockMvc.perform(get("/api/applications/mine").header("Authorization", bearerToken(candidateA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(applicationA))
                .andExpect(jsonPath("$[0].candidateId").value(candidateA.getId()));
    }

    @Test
    void mine_asRecruiter_returns403() throws Exception {
        User recruiter = createUser(Role.RECRUITER);

        mockMvc.perform(get("/api/applications/mine").header("Authorization", bearerToken(recruiter)))
                .andExpect(status().isForbidden());
    }

    // ---- GET /api/applications (recruiter/admin, filterable) ------------------------------

    @Test
    void listAll_asRecruiter_returnsAcrossCandidatesAndFiltersByPosting() throws Exception {
        User admin = createUser(Role.ADMIN);
        User recruiter = createUser(Role.RECRUITER);
        User candidateA = createUser(Role.CANDIDATE);
        User candidateB = createUser(Role.CANDIDATE);
        JobPosting postingA = createPosting(admin, PostingStatus.OPEN);
        JobPosting postingB = createPosting(admin, PostingStatus.OPEN);

        Long applicationA = apply(candidateA, postingA);
        apply(candidateB, postingB);

        mockMvc.perform(get("/api/applications")
                        .header("Authorization", bearerToken(recruiter))
                        .param("postingId", String.valueOf(postingA.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + applicationA + ")]").exists())
                .andExpect(jsonPath("$[?(@.jobPostingId == " + postingB.getId() + ")]").doesNotExist());
    }

    @Test
    void listAll_asCandidate_returns403() throws Exception {
        User candidate = createUser(Role.CANDIDATE);

        mockMvc.perform(get("/api/applications").header("Authorization", bearerToken(candidate)))
                .andExpect(status().isForbidden());
    }

    @Test
    void listAll_withInvalidStatusQueryParam_returns400() throws Exception {
        User recruiter = createUser(Role.RECRUITER);

        mockMvc.perform(get("/api/applications")
                        .header("Authorization", bearerToken(recruiter))
                        .param("status", "NOT_A_REAL_STATUS"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listAll_asAdmin_succeeds() throws Exception {
        User admin = createUser(Role.ADMIN);

        mockMvc.perform(get("/api/applications").header("Authorization", bearerToken(admin)))
                .andExpect(status().isOk());
    }

    // ---- PATCH /api/applications/{id}/status -----------------------------------------------

    @Test
    void statusUpdate_recruiterAdvancesAppliedToScreening_succeedsAndWritesAuditEvent() throws Exception {
        User admin = createUser(Role.ADMIN);
        User recruiter = createUser(Role.RECRUITER);
        User candidate = createUser(Role.CANDIDATE);
        JobPosting posting = createPosting(admin, PostingStatus.OPEN);
        Long applicationId = apply(candidate, posting);

        mockMvc.perform(patch("/api/applications/" + applicationId + "/status")
                        .header("Authorization", bearerToken(recruiter))
                        .contentType("application/json")
                        .content("{\"targetStatus\": \"SCREENING\", \"note\": \"looks good\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCREENING"));

        mockMvc.perform(get("/api/applications/" + applicationId + "/events")
                        .header("Authorization", bearerToken(recruiter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].fromStatus").value("APPLIED"))
                .andExpect(jsonPath("$[1].toStatus").value("SCREENING"))
                .andExpect(jsonPath("$[1].note").value("looks good"))
                .andExpect(jsonPath("$[1].changedBy").value(recruiter.getId()));
    }

    @Test
    void statusUpdate_candidateAttemptsForwardProgression_returns400() throws Exception {
        User admin = createUser(Role.ADMIN);
        User candidate = createUser(Role.CANDIDATE);
        JobPosting posting = createPosting(admin, PostingStatus.OPEN);
        Long applicationId = apply(candidate, posting);

        mockMvc.perform(patch("/api/applications/" + applicationId + "/status")
                        .header("Authorization", bearerToken(candidate))
                        .contentType("application/json")
                        .content("{\"targetStatus\": \"SCREENING\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void statusUpdate_recruiterSkipsAStep_returns400() throws Exception {
        User admin = createUser(Role.ADMIN);
        User recruiter = createUser(Role.RECRUITER);
        User candidate = createUser(Role.CANDIDATE);
        JobPosting posting = createPosting(admin, PostingStatus.OPEN);
        Long applicationId = apply(candidate, posting);

        mockMvc.perform(patch("/api/applications/" + applicationId + "/status")
                        .header("Authorization", bearerToken(recruiter))
                        .contentType("application/json")
                        .content("{\"targetStatus\": \"INTERVIEW\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void statusUpdate_ownerAcceptsOffer_succeeds() throws Exception {
        User admin = createUser(Role.ADMIN);
        User recruiter = createUser(Role.RECRUITER);
        User candidate = createUser(Role.CANDIDATE);
        JobPosting posting = createPosting(admin, PostingStatus.OPEN);
        Long applicationId = apply(candidate, posting);
        advance(recruiter, applicationId, "SCREENING");
        advance(recruiter, applicationId, "INTERVIEW");
        advance(recruiter, applicationId, "OFFER");

        mockMvc.perform(patch("/api/applications/" + applicationId + "/status")
                        .header("Authorization", bearerToken(candidate))
                        .contentType("application/json")
                        .content("{\"targetStatus\": \"ACCEPTED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    void statusUpdate_recruiterRejectsFromApplied_succeeds() throws Exception {
        User admin = createUser(Role.ADMIN);
        User recruiter = createUser(Role.RECRUITER);
        User candidate = createUser(Role.CANDIDATE);
        JobPosting posting = createPosting(admin, PostingStatus.OPEN);
        Long applicationId = apply(candidate, posting);

        mockMvc.perform(patch("/api/applications/" + applicationId + "/status")
                        .header("Authorization", bearerToken(recruiter))
                        .contentType("application/json")
                        .content("{\"targetStatus\": \"REJECTED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void statusUpdate_ownerWithdraws_succeeds() throws Exception {
        User admin = createUser(Role.ADMIN);
        User candidate = createUser(Role.CANDIDATE);
        JobPosting posting = createPosting(admin, PostingStatus.OPEN);
        Long applicationId = apply(candidate, posting);

        mockMvc.perform(patch("/api/applications/" + applicationId + "/status")
                        .header("Authorization", bearerToken(candidate))
                        .contentType("application/json")
                        .content("{\"targetStatus\": \"WITHDRAWN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"));
    }

    @Test
    void statusUpdate_afterTerminalState_returns400_evenForAdmin() throws Exception {
        User admin = createUser(Role.ADMIN);
        User candidate = createUser(Role.CANDIDATE);
        JobPosting posting = createPosting(admin, PostingStatus.OPEN);
        Long applicationId = apply(candidate, posting);

        mockMvc.perform(patch("/api/applications/" + applicationId + "/status")
                        .header("Authorization", bearerToken(candidate))
                        .contentType("application/json")
                        .content("{\"targetStatus\": \"WITHDRAWN\"}"))
                .andExpect(status().isOk());

        // Not even ADMIN can move an application out of a terminal state.
        mockMvc.perform(patch("/api/applications/" + applicationId + "/status")
                        .header("Authorization", bearerToken(admin))
                        .contentType("application/json")
                        .content("{\"targetStatus\": \"SCREENING\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void statusUpdate_withoutAuthentication_returns401() throws Exception {
        User admin = createUser(Role.ADMIN);
        User candidate = createUser(Role.CANDIDATE);
        JobPosting posting = createPosting(admin, PostingStatus.OPEN);
        Long applicationId = apply(candidate, posting);

        mockMvc.perform(patch("/api/applications/" + applicationId + "/status")
                        .contentType("application/json")
                        .content("{\"targetStatus\": \"SCREENING\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void statusUpdate_nonExistentApplication_returns404() throws Exception {
        User recruiter = createUser(Role.RECRUITER);

        mockMvc.perform(patch("/api/applications/999999999/status")
                        .header("Authorization", bearerToken(recruiter))
                        .contentType("application/json")
                        .content("{\"targetStatus\": \"SCREENING\"}"))
                .andExpect(status().isNotFound());
    }

    // ---- Ownership proof: a CANDIDATE must never touch another candidate's Application -----

    @Test
    void candidateCannotTransitionAnotherCandidatesApplication_returns403() throws Exception {
        User admin = createUser(Role.ADMIN);
        User owner = createUser(Role.CANDIDATE);
        User intruder = createUser(Role.CANDIDATE);
        JobPosting posting = createPosting(admin, PostingStatus.OPEN);
        Long applicationId = apply(owner, posting);

        mockMvc.perform(patch("/api/applications/" + applicationId + "/status")
                        .header("Authorization", bearerToken(intruder))
                        .contentType("application/json")
                        .content("{\"targetStatus\": \"WITHDRAWN\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void candidateCannotViewAnotherCandidatesApplicationEvents_returns403() throws Exception {
        User admin = createUser(Role.ADMIN);
        User owner = createUser(Role.CANDIDATE);
        User intruder = createUser(Role.CANDIDATE);
        JobPosting posting = createPosting(admin, PostingStatus.OPEN);
        Long applicationId = apply(owner, posting);

        mockMvc.perform(get("/api/applications/" + applicationId + "/events")
                        .header("Authorization", bearerToken(intruder)))
                .andExpect(status().isForbidden());
    }

    // ---- GET /api/applications/{id}/events -------------------------------------------------

    @Test
    void events_owningCandidate_canViewOwnAuditTrail() throws Exception {
        User admin = createUser(Role.ADMIN);
        User candidate = createUser(Role.CANDIDATE);
        JobPosting posting = createPosting(admin, PostingStatus.OPEN);
        Long applicationId = apply(candidate, posting);

        mockMvc.perform(get("/api/applications/" + applicationId + "/events")
                        .header("Authorization", bearerToken(candidate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].toStatus").value("APPLIED"))
                .andExpect(jsonPath("$[0].fromStatus").doesNotExist());
    }

    @Test
    void events_recruiter_canViewAnyApplicationsAuditTrail() throws Exception {
        User admin = createUser(Role.ADMIN);
        User recruiter = createUser(Role.RECRUITER);
        User candidate = createUser(Role.CANDIDATE);
        JobPosting posting = createPosting(admin, PostingStatus.OPEN);
        Long applicationId = apply(candidate, posting);

        mockMvc.perform(get("/api/applications/" + applicationId + "/events")
                        .header("Authorization", bearerToken(recruiter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void events_admin_canViewAnyApplicationsAuditTrail() throws Exception {
        User admin = createUser(Role.ADMIN);
        User candidate = createUser(Role.CANDIDATE);
        JobPosting posting = createPosting(admin, PostingStatus.OPEN);
        Long applicationId = apply(candidate, posting);

        mockMvc.perform(get("/api/applications/" + applicationId + "/events")
                        .header("Authorization", bearerToken(admin)))
                .andExpect(status().isOk());
    }

    @Test
    void events_withoutAuthentication_returns401() throws Exception {
        User admin = createUser(Role.ADMIN);
        User candidate = createUser(Role.CANDIDATE);
        JobPosting posting = createPosting(admin, PostingStatus.OPEN);
        Long applicationId = apply(candidate, posting);

        mockMvc.perform(get("/api/applications/" + applicationId + "/events"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void events_nonExistentApplication_returns404() throws Exception {
        User recruiter = createUser(Role.RECRUITER);

        mockMvc.perform(get("/api/applications/999999999/events")
                        .header("Authorization", bearerToken(recruiter)))
                .andExpect(status().isNotFound());
    }

    // ---- helpers ----------------------------------------------------------------------------

    private Long apply(User candidate, JobPosting posting) throws Exception {
        String response = mockMvc.perform(post("/api/applications")
                        .header("Authorization", bearerToken(candidate))
                        .contentType("application/json")
                        .content("{\"jobPostingId\": " + posting.getId() + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private void advance(User recruiter, Long applicationId, String targetStatus) throws Exception {
        mockMvc.perform(patch("/api/applications/" + applicationId + "/status")
                        .header("Authorization", bearerToken(recruiter))
                        .contentType("application/json")
                        .content("{\"targetStatus\": \"" + targetStatus + "\"}"))
                .andExpect(status().isOk());
    }
}
