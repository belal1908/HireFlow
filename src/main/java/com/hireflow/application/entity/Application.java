package com.hireflow.application.entity;

import com.hireflow.application.transition.ApplicationStatus;
import com.hireflow.posting.entity.JobPosting;
import com.hireflow.user.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(
        name = "applications",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_candidate_job_posting",
                columnNames = {"candidate_id", "job_posting_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_id", nullable = false)
    private User candidate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_posting_id", nullable = false)
    private JobPosting jobPosting;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApplicationStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // --- Resume upload (nullable: most applications never get one) --------------------------
    // Modeled as plain nullable columns on Application rather than a separate table: unlike
    // ApplicationEvent (a genuine one-to-many audit log, append-only), a resume is a strict
    // one-to-one/optional attribute of a single Application with no history to preserve - a
    // re-upload simply replaces it (old file deleted from disk, columns overwritten). A join
    // table would add a join for zero benefit here. The file itself is never stored in the DB
    // (see ResumeStorageService) - only this metadata is.

    /** Client-supplied filename, kept only for display/download purposes - never used as a disk path. */
    @Column(name = "resume_original_filename")
    private String resumeOriginalFilename;

    /** UUID-based filename actually used on disk under the configured resume storage directory. */
    @Column(name = "resume_stored_filename")
    private String resumeStoredFilename;

    @Column(name = "resume_content_type")
    private String resumeContentType;

    @Column(name = "resume_size_bytes")
    private Long resumeSizeBytes;

    @Column(name = "resume_uploaded_at")
    private Instant resumeUploadedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (status == null) {
            status = ApplicationStatus.APPLIED;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
