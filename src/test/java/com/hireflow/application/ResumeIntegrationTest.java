package com.hireflow.application;

import com.hireflow.posting.entity.JobPosting;
import com.hireflow.posting.entity.PostingStatus;
import com.hireflow.support.AbstractIntegrationTest;
import com.hireflow.user.entity.Role;
import com.hireflow.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers POST/GET /api/applications/{id}/resume: owner-only upload (with re-upload replacing
 * the previous file), PDF validation (declared content-type AND magic bytes), the 5MB size
 * limit, and the same owner-or-privileged visibility rule as GET .../events for downloads.
 */
class ResumeIntegrationTest extends AbstractIntegrationTest {

    private static final byte[] VALID_PDF_BYTES =
            "%PDF-1.4\n%mock pdf content for HireFlow integration tests\n%%EOF".getBytes(StandardCharsets.US_ASCII);

    // ---- POST /api/applications/{id}/resume ------------------------------------------------

    @Test
    void upload_asOwningCandidate_succeedsAndUpdatesApplicationMetadata() throws Exception {
        User admin = createUser(Role.ADMIN);
        User candidate = createUser(Role.CANDIDATE);
        JobPosting posting = createPosting(admin, PostingStatus.OPEN);
        Long applicationId = apply(candidate, posting);

        MockMultipartFile file = pdfFile("my-resume.pdf", VALID_PDF_BYTES);

        mockMvc.perform(multipart("/api/applications/" + applicationId + "/resume")
                        .file(file)
                        .header("Authorization", bearerToken(candidate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumeFilename").value("my-resume.pdf"))
                .andExpect(jsonPath("$.resumeContentType").value("application/pdf"))
                .andExpect(jsonPath("$.resumeSizeBytes").value(VALID_PDF_BYTES.length))
                .andExpect(jsonPath("$.resumeUploadedAt").exists());
    }

    @Test
    void upload_asAnotherCandidate_returns403() throws Exception {
        User admin = createUser(Role.ADMIN);
        User owner = createUser(Role.CANDIDATE);
        User intruder = createUser(Role.CANDIDATE);
        JobPosting posting = createPosting(admin, PostingStatus.OPEN);
        Long applicationId = apply(owner, posting);

        mockMvc.perform(multipart("/api/applications/" + applicationId + "/resume")
                        .file(pdfFile("resume.pdf", VALID_PDF_BYTES))
                        .header("Authorization", bearerToken(intruder)))
                .andExpect(status().isForbidden());
    }

    @Test
    void upload_asRecruiter_returns403() throws Exception {
        User admin = createUser(Role.ADMIN);
        User candidate = createUser(Role.CANDIDATE);
        User recruiter = createUser(Role.RECRUITER);
        JobPosting posting = createPosting(admin, PostingStatus.OPEN);
        Long applicationId = apply(candidate, posting);

        mockMvc.perform(multipart("/api/applications/" + applicationId + "/resume")
                        .file(pdfFile("resume.pdf", VALID_PDF_BYTES))
                        .header("Authorization", bearerToken(recruiter)))
                .andExpect(status().isForbidden());
    }

    @Test
    void upload_withoutAuthentication_returns401() throws Exception {
        User admin = createUser(Role.ADMIN);
        User candidate = createUser(Role.CANDIDATE);
        JobPosting posting = createPosting(admin, PostingStatus.OPEN);
        Long applicationId = apply(candidate, posting);

        mockMvc.perform(multipart("/api/applications/" + applicationId + "/resume")
                        .file(pdfFile("resume.pdf", VALID_PDF_BYTES)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void upload_nonExistentApplication_returns404() throws Exception {
        User candidate = createUser(Role.CANDIDATE);

        mockMvc.perform(multipart("/api/applications/999999999/resume")
                        .file(pdfFile("resume.pdf", VALID_PDF_BYTES))
                        .header("Authorization", bearerToken(candidate)))
                .andExpect(status().isNotFound());
    }

    @Test
    void upload_withNonPdfContentType_returns400() throws Exception {
        User admin = createUser(Role.ADMIN);
        User candidate = createUser(Role.CANDIDATE);
        JobPosting posting = createPosting(admin, PostingStatus.OPEN);
        Long applicationId = apply(candidate, posting);

        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.txt", "text/plain", "just some text, not a pdf".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/applications/" + applicationId + "/resume")
                        .file(file)
                        .header("Authorization", bearerToken(candidate)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void upload_withPdfContentTypeButWrongMagicBytes_returns400() throws Exception {
        // Declares application/pdf but the actual bytes don't start with "%PDF-" - the magic
        // bytes check must catch what the (client-controlled) content-type header alone cannot.
        User admin = createUser(Role.ADMIN);
        User candidate = createUser(Role.CANDIDATE);
        JobPosting posting = createPosting(admin, PostingStatus.OPEN);
        Long applicationId = apply(candidate, posting);

        MockMultipartFile file = pdfFile("fake.pdf", "not actually a pdf file at all".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/applications/" + applicationId + "/resume")
                        .file(file)
                        .header("Authorization", bearerToken(candidate)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void upload_exceedingSizeLimit_returns400() throws Exception {
        User admin = createUser(Role.ADMIN);
        User candidate = createUser(Role.CANDIDATE);
        JobPosting posting = createPosting(admin, PostingStatus.OPEN);
        Long applicationId = apply(candidate, posting);

        byte[] oversized = new byte[5 * 1024 * 1024 + 1024]; // just over 5MB
        byte[] magic = "%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(magic, 0, oversized, 0, magic.length);

        mockMvc.perform(multipart("/api/applications/" + applicationId + "/resume")
                        .file(pdfFile("huge.pdf", oversized))
                        .header("Authorization", bearerToken(candidate)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reupload_replacesThePreviousFile() throws Exception {
        User admin = createUser(Role.ADMIN);
        User candidate = createUser(Role.CANDIDATE);
        JobPosting posting = createPosting(admin, PostingStatus.OPEN);
        Long applicationId = apply(candidate, posting);

        mockMvc.perform(multipart("/api/applications/" + applicationId + "/resume")
                        .file(pdfFile("first.pdf", VALID_PDF_BYTES))
                        .header("Authorization", bearerToken(candidate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumeFilename").value("first.pdf"));

        byte[] secondContent = "%PDF-1.4\nsecond version\n%%EOF".getBytes(StandardCharsets.US_ASCII);
        mockMvc.perform(multipart("/api/applications/" + applicationId + "/resume")
                        .file(pdfFile("second.pdf", secondContent))
                        .header("Authorization", bearerToken(candidate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumeFilename").value("second.pdf"))
                .andExpect(jsonPath("$.resumeSizeBytes").value(secondContent.length));

        mockMvc.perform(get("/api/applications/" + applicationId + "/resume")
                        .header("Authorization", bearerToken(candidate)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"second.pdf\""))
                .andExpect(content().bytes(secondContent));
    }

    // ---- GET /api/applications/{id}/resume --------------------------------------------------

    @Test
    void download_asOwningCandidate_returnsTheFileWithCorrectHeaders() throws Exception {
        User admin = createUser(Role.ADMIN);
        User candidate = createUser(Role.CANDIDATE);
        JobPosting posting = createPosting(admin, PostingStatus.OPEN);
        Long applicationId = apply(candidate, posting);
        upload(candidate, applicationId, "my-resume.pdf", VALID_PDF_BYTES);

        mockMvc.perform(get("/api/applications/" + applicationId + "/resume")
                        .header("Authorization", bearerToken(candidate)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"my-resume.pdf\""))
                .andExpect(content().bytes(VALID_PDF_BYTES));
    }

    @Test
    void download_asRecruiter_succeeds() throws Exception {
        User admin = createUser(Role.ADMIN);
        User candidate = createUser(Role.CANDIDATE);
        User recruiter = createUser(Role.RECRUITER);
        JobPosting posting = createPosting(admin, PostingStatus.OPEN);
        Long applicationId = apply(candidate, posting);
        upload(candidate, applicationId, "resume.pdf", VALID_PDF_BYTES);

        mockMvc.perform(get("/api/applications/" + applicationId + "/resume")
                        .header("Authorization", bearerToken(recruiter)))
                .andExpect(status().isOk())
                .andExpect(content().bytes(VALID_PDF_BYTES));
    }

    @Test
    void download_asAdmin_succeeds() throws Exception {
        User admin = createUser(Role.ADMIN);
        User candidate = createUser(Role.CANDIDATE);
        JobPosting posting = createPosting(admin, PostingStatus.OPEN);
        Long applicationId = apply(candidate, posting);
        upload(candidate, applicationId, "resume.pdf", VALID_PDF_BYTES);

        mockMvc.perform(get("/api/applications/" + applicationId + "/resume")
                        .header("Authorization", bearerToken(admin)))
                .andExpect(status().isOk());
    }

    @Test
    void download_asAnotherCandidate_returns403() throws Exception {
        User admin = createUser(Role.ADMIN);
        User owner = createUser(Role.CANDIDATE);
        User intruder = createUser(Role.CANDIDATE);
        JobPosting posting = createPosting(admin, PostingStatus.OPEN);
        Long applicationId = apply(owner, posting);
        upload(owner, applicationId, "resume.pdf", VALID_PDF_BYTES);

        mockMvc.perform(get("/api/applications/" + applicationId + "/resume")
                        .header("Authorization", bearerToken(intruder)))
                .andExpect(status().isForbidden());
    }

    @Test
    void download_withoutAuthentication_returns401() throws Exception {
        User admin = createUser(Role.ADMIN);
        User candidate = createUser(Role.CANDIDATE);
        JobPosting posting = createPosting(admin, PostingStatus.OPEN);
        Long applicationId = apply(candidate, posting);
        upload(candidate, applicationId, "resume.pdf", VALID_PDF_BYTES);

        mockMvc.perform(get("/api/applications/" + applicationId + "/resume"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void download_whenNoResumeUploaded_returns404() throws Exception {
        User admin = createUser(Role.ADMIN);
        User candidate = createUser(Role.CANDIDATE);
        JobPosting posting = createPosting(admin, PostingStatus.OPEN);
        Long applicationId = apply(candidate, posting);

        mockMvc.perform(get("/api/applications/" + applicationId + "/resume")
                        .header("Authorization", bearerToken(candidate)))
                .andExpect(status().isNotFound());
    }

    @Test
    void download_nonExistentApplication_returns404() throws Exception {
        User recruiter = createUser(Role.RECRUITER);

        mockMvc.perform(get("/api/applications/999999999/resume")
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

    private void upload(User candidate, Long applicationId, String filename, byte[] content) throws Exception {
        mockMvc.perform(multipart("/api/applications/" + applicationId + "/resume")
                        .file(pdfFile(filename, content))
                        .header("Authorization", bearerToken(candidate)))
                .andExpect(status().isOk());
    }

    private MockMultipartFile pdfFile(String filename, byte[] content) {
        return new MockMultipartFile("file", filename, "application/pdf", Arrays.copyOf(content, content.length));
    }
}
