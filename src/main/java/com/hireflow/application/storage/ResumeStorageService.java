package com.hireflow.application.storage;

import com.hireflow.common.exception.NotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Local-disk storage for résumé files, kept entirely separate from the database (only file
 * *metadata* lives on {@code Application} - see its class comment). Files live under a
 * configured directory outside {@code src/} (default {@code ./data/resumes}, gitignored) rather
 * than as a BLOB column, so the database stays small and files can be served/streamed without
 * loading them into memory as JDBC bytes.
 *
 * <p>Every file is written under a freshly generated {@link UUID} name - the client's original
 * filename is preserved only as display metadata on {@code Application} (see
 * {@code ApplicationResponse}/{@code Content-Disposition} on download) and is never used to
 * build a filesystem path. This is a deliberate defense against path traversal: a filename like
 * {@code ../../etc/passwd} or one containing null bytes can never influence where the file
 * actually lands on disk.
 *
 * <p>Known limitation (documented in the README): this is local-disk storage on a single
 * instance. It does not survive the backend container being recreated under the Docker Compose
 * "full" profile unless a volume is mounted for the storage directory (one is - see
 * {@code docker-compose.yml}) - and would not work at all across multiple horizontally-scaled
 * backend replicas without a shared filesystem or object storage.
 */
@Service
public class ResumeStorageService {

    private final Path storageDir;

    public ResumeStorageService(@Value("${hireflow.resume.storage-dir}") String storageDir) {
        this.storageDir = Paths.get(storageDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.storageDir);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create resume storage directory: " + this.storageDir, e);
        }
    }

    /** Stores the uploaded file under a new UUID-based name and returns that stored filename. */
    public String store(MultipartFile file) {
        String storedFilename = UUID.randomUUID() + ".pdf";
        Path target = resolveSafely(storedFilename);

        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store resume file", e);
        }
        return storedFilename;
    }

    /** Loads a previously stored file for streaming back to the client. */
    public Resource load(String storedFilename) {
        Path file = resolveSafely(storedFilename);
        if (!Files.exists(file)) {
            throw new NotFoundException("Resume file not found on disk");
        }
        return new FileSystemResource(file);
    }

    /** Best-effort delete, e.g. when a resume is replaced by a re-upload. Never throws. */
    public void delete(String storedFilename) {
        if (storedFilename == null) {
            return;
        }
        try {
            Files.deleteIfExists(resolveSafely(storedFilename));
        } catch (IOException e) {
            // A stray file left on disk isn't worth failing the request over; the DB row (the
            // source of truth for "does this application have a resume") is already updated.
        }
    }

    /**
     * Resolves a stored filename against the storage directory and defensively verifies the
     * result is still directly inside it - stored filenames are always our own UUIDs (see
     * {@link #store}), so this should never actually fire, but it's cheap insurance against a
     * path-traversal bug being reintroduced later.
     */
    private Path resolveSafely(String storedFilename) {
        Path resolved = storageDir.resolve(storedFilename).normalize();
        if (!resolved.getParent().equals(storageDir)) {
            throw new IllegalArgumentException("Invalid stored filename: " + storedFilename);
        }
        return resolved;
    }
}
